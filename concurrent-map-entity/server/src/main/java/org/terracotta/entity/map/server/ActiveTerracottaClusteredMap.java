/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Terracotta Platform.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.entity.map.server;

import org.terracotta.entity.ActiveServerEntity;
import org.terracotta.entity.ClientDescriptor;
import org.terracotta.entity.ConcurrencyStrategy;
import org.terracotta.entity.PassiveSynchronizationChannel;
import org.terracotta.entity.map.common.BooleanResponse;
import org.terracotta.entity.map.common.ConditionalRemoveOperation;
import org.terracotta.entity.map.common.ConditionalReplaceOperation;
import org.terracotta.entity.map.common.ContainsKeyOperation;
import org.terracotta.entity.map.common.ContainsValueOperation;
import org.terracotta.entity.map.common.EntrySetResponse;
import org.terracotta.entity.map.common.GetOperation;
import org.terracotta.entity.map.common.KeySetResponse;
import org.terracotta.entity.map.common.MapOperation;
import org.terracotta.entity.map.common.MapResponse;
import org.terracotta.entity.map.common.MapValueResponse;
import org.terracotta.entity.map.common.NullResponse;
import org.terracotta.entity.map.common.PutAllOperation;
import org.terracotta.entity.map.common.PutIfAbsentOperation;
import org.terracotta.entity.map.common.PutIfPresentOperation;
import org.terracotta.entity.map.common.PutOperation;
import org.terracotta.entity.map.common.RemoveOperation;
import org.terracotta.entity.map.common.SizeResponse;
import org.terracotta.entity.map.common.ValueCollectionResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


class ActiveTerracottaClusteredMap implements ActiveServerEntity<MapOperation, MapResponse>  {

  private static final int CONCURRENCY_KEY = 42;

  // TODO Given the way a passive becomes active - this does not work ...
  private final ConcurrentMap<Object, Object> map = new ConcurrentHashMap<Object, Object>();
  
  @Override
  public void connected(ClientDescriptor clientDescriptor) {
  }

  @Override
  public void handleReconnect(ClientDescriptor clientDescriptor, byte[] extendedReconnectData) {
    // Do nothing.
  }

  @Override
  public void disconnected(ClientDescriptor clientDescriptor) {
  }

  // Note that we suppress deprecation since we have the size method.
  @SuppressWarnings("deprecation")
  @Override
  public MapResponse invoke(ClientDescriptor clientDescriptor, MapOperation input) {
    MapResponse response = null;
    
    switch (input.operationType()) {
      case PUT: {
        PutOperation putOperation = (PutOperation) input;
        Object key = putOperation.getKey();
        Object old = map.get(key);
        map.put(key, putOperation.getValue());
        response = new MapValueResponse(old);
        break;
      }
      case GET: {
        Object key = ((GetOperation) input).getKey();
        response = new MapValueResponse(map.get(key));
        break;
      }
      case REMOVE: {
        Object key = ((RemoveOperation) input).getKey();
        response = new MapValueResponse(map.remove(key));
        break;
      }
      case CONTAINS_KEY: {
        Object key = ((ContainsKeyOperation) input).getKey();
        response = new BooleanResponse(map.containsKey(key));
        break;
      }
      case CONTAINS_VALUE: {
        Object value = ((ContainsValueOperation) input).getValue();
        response = new BooleanResponse(map.containsValue(value));
        break;
      }
      case CLEAR: {
        map.clear();
        // There is no response from the clear.
        response = new NullResponse();
        break;
      }
      case PUT_ALL: {
        @SuppressWarnings("unchecked")
        Map<Object, Object> newValues = (Map<Object, Object>) ((PutAllOperation)input).getMap();
        map.putAll(newValues);
        // There is no response from a put all.
        response = new NullResponse();
        break;
      }
      case KEY_SET: {
        Set<Object> keySet = new HashSet<Object>();
        keySet.addAll(map.keySet());
        response = new KeySetResponse(keySet);
        break;
      }
      case VALUES: {
        Collection<Object> values = new ArrayList<Object>();
        values.addAll(map.values());
        response = new ValueCollectionResponse(values);
        break;
      }
      case ENTRY_SET: {
        Set<Map.Entry<Object, Object>> entrySet = new HashSet<Map.Entry<Object, Object>>();
        entrySet.addAll(map.entrySet());
        response = new EntrySetResponse(entrySet);
        break;
      }
      case SIZE: {
        response = new SizeResponse(map.size());
        break;
      }
      case PUT_IF_ABSENT: {
        PutIfAbsentOperation operation = (PutIfAbsentOperation) input;
        response = new MapValueResponse(map.putIfAbsent(operation.getKey(), operation.getValue()));
        break;
      }
      case PUT_IF_PRESENT: {
        PutIfPresentOperation operation = (PutIfPresentOperation) input;
        response = new MapValueResponse(map.replace(operation.getKey(), operation.getValue()));
        break;
      }
      case CONDITIONAL_REMOVE: {
        ConditionalRemoveOperation operation = (ConditionalRemoveOperation) input;
        response = new BooleanResponse(map.remove(operation.getKey(), operation.getValue()));
        break;
      }
      case CONDITIONAL_REPLACE: {
        ConditionalReplaceOperation operation = (ConditionalReplaceOperation) input;
        response = new BooleanResponse(map.replace(operation.getKey(), operation.getOldValue(), operation.getNewValue()));
        break;
      }
      default:
        // Unknown message type.
        throw new AssertionError("Unsupported message type: " + input.operationType());
    }
    return response;
  }

  @Override
  public void createNew() {
  }

  @Override
  public void loadExisting() {
  }

  @Override
  public void destroy() {
    map.clear();
  }

  public static class MapConcurrencyStrategy implements ConcurrencyStrategy<MapOperation> {

    @Override
    public int concurrencyKey(MapOperation operation) {
      return CONCURRENCY_KEY;
    }

    @Override
    public Set<Integer> getKeysForSynchronization() {
      return Collections.singleton(CONCURRENCY_KEY);
    }
  }

  @Override
  public void synchronizeKeyToPassive(PassiveSynchronizationChannel<MapOperation> syncChannel, int concurrencyKey) {
    if (concurrencyKey != CONCURRENCY_KEY) {
      throw new IllegalArgumentException("concurrencyKey should only be " + CONCURRENCY_KEY);
    }

    syncChannel.synchronizeToPassive(new SyncOperation(map));
  }
}