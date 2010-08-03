/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class MultiMap<K, V> {

  public static final MultiMap EMPTY = new MultiMap() {
    @Override
    protected Map createMap() {
      return Collections.emptyMap();
    }
  };

  private final Map<K, Collection<V>> myMap;

  private Collection<V> values;

  public MultiMap() {
    myMap = createMap();
  }

  protected Map<K, Collection<V>> createMap() {
    return new HashMap<K, Collection<V>>();
  }

  protected Collection<V> createCollection() {
    return new ArrayList<V>();
  }

  protected Collection<V> createEmptyCollection() {
    return Collections.emptyList();
  }

  public void putAllValues(MultiMap<? extends K, ? extends V> from) {
    for (K k : from.keySet()) {
      //noinspection unchecked
      putValues(k, ((MultiMap)from).get(k));
    }
  }

  public void putValues(K key, Collection<? extends V> values) {
    Collection<V> list = myMap.get(key);
    if (list == null) {
      list = createCollection();
      myMap.put(key, list);
    }
    list.addAll(values);
  }

  public void putValue(K key, V value) {
    Collection<V> list = myMap.get(key);
    if (list == null) {
      list = createCollection();
      myMap.put(key, list);
    }
    list.add(value);
  }

  public boolean isEmpty() {
    for(Collection<V> valueList: myMap.values()) {
      if (!valueList.isEmpty()) {
        return false;
      }
    }
    return true;    
  }

  public boolean containsKey(K key) {
    return myMap.containsKey(key);
  }
  
  public boolean containsScalarValue(V value) {
    for(Collection<V> valueList: myMap.values()) {
      if (valueList.contains(value)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public Collection<V> get(final K key) {
    final Collection<V> collection = myMap.get(key);
    return collection == null ? createEmptyCollection() : collection;
  }

  @NotNull
  public Collection<V> getModifiable(final K key) {
    Collection<V> collection = myMap.get(key);
    if (collection == null) {
      myMap.put(key, collection = createCollection());
    }
    return collection;
  }

  public Set<K> keySet() {
    return myMap.keySet();
  }

  public int size() {
    return myMap.size();
  }

  public void put(final K key, final Collection<V> values) {
    myMap.put(key, values);
  }

  public void removeValue(final K key, final V value) {
    final Collection<V> values = myMap.get(key);
    values.remove(value);
    if (values.isEmpty()) {
      myMap.remove(key);
    }
  }

  public Collection<? extends V> values() {
    if (values == null) {
      values = new AbstractCollection<V>() {
        @Override
        public Iterator<V> iterator() {
          return new Iterator<V>() {

            private Iterator<Collection<V>> mapIterator = myMap.values().iterator();

            private Iterator<V> itr = EmptyIterator.getInstance();

            @Override
            public boolean hasNext() {
              do {
                if (itr.hasNext()) return true;
                if (!mapIterator.hasNext()) return false;
                itr = mapIterator.next().iterator();
              } while (true);
            }

            @Override
            public V next() {
              do {
                if (itr.hasNext()) return itr.next();
                if (!mapIterator.hasNext()) throw new NoSuchElementException();
                itr = mapIterator.next().iterator();
              } while (true);
            }

            @Override
            public void remove() {
              throw new UnsupportedOperationException();
            }
          };
        }

        @Override
        public int size() {
          int res = 0;
          for (Collection<V> vs : myMap.values()) {
            res += vs.size();
          }

          return res;
        }

        // Don't remove this method!!!
        @Override
        public boolean contains(Object o) {
          for (Collection<V> vs : myMap.values()) {
            if (vs.contains(o)) return true;
          }

          return false;
        }
      };
    }

    return values;
  }

  public void clear() {
    myMap.clear();
  }

  public Collection<V> remove(K key) {
    return myMap.remove(key);
  }

  public static <K, V> MultiMap<K, V> emptyInstance() {
    return EMPTY;
  }
}
