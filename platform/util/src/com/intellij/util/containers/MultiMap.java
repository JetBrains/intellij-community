// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.containers;

import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.*;
import java.util.HashMap;

/**
 * Consider to use factory methods {@link #createLinked()}, {@link #createSet()}, {@link #createSmart()}, {@link #create(TObjectHashingStrategy)} instead of override.
 * @see BidirectionalMultiMap
 * @see ConcurrentMultiMap
 * @author Dmitry Avdeev
 */
public class MultiMap<K, V> implements Serializable {
  public static final MultiMap EMPTY = new EmptyMap();
  private static final long serialVersionUID = -2632269270151455493L;

  protected final Map<K, Collection<V>> myMap;
  private Collection<V> values;

  public MultiMap() {
    myMap = createMap();
  }

  public MultiMap(@NotNull MultiMap<? extends K, ? extends V> toCopy) {
    this();
    putAllValues(toCopy);
  }

  @NotNull
  public MultiMap<K, V> copy() {
    return new MultiMap<K, V>(this);
  }
  
  public MultiMap(int initialCapacity, float loadFactor) {
    myMap = createMap(initialCapacity, loadFactor);
  }

  @NotNull
  protected Map<K, Collection<V>> createMap() {
    return new java.util.HashMap<K, Collection<V>>();
  }

  @NotNull
  protected Map<K, Collection<V>> createMap(int initialCapacity, float loadFactor) {
    return new HashMap<K, Collection<V>>(initialCapacity, loadFactor);
  }

  @NotNull
  protected Collection<V> createCollection() {
    return new SmartList<V>();
  }

  @NotNull
  protected Collection<V> createEmptyCollection() {
    return Collections.emptyList();
  }

  public void putAllValues(@NotNull MultiMap<? extends K, ? extends V> from) {
    for (Map.Entry<? extends K, ? extends Collection<? extends V>> entry : from.entrySet()) {
      putValues(entry.getKey(), entry.getValue());
    }
  }

  public void putAllValues(@NotNull Map<? extends K, ? extends V> from) {
    for (Map.Entry<? extends K, ? extends V> entry : from.entrySet()) {
      putValue(entry.getKey(), entry.getValue());
    }
  }

  public void putValues(K key, @NotNull Collection<? extends V> values) {
    Collection<V> list = myMap.get(key);
    if (list == null) {
      list = createCollection();
      myMap.put(key, list);
    }
    list.addAll(values);
  }

  public void putValue(@Nullable K key, V value) {
    Collection<V> list = myMap.get(key);
    if (list == null) {
      list = createCollection();
      myMap.put(key, list);
    }
    list.add(value);
  }

  @NotNull
  public Set<Map.Entry<K, Collection<V>>> entrySet() {
    return myMap.entrySet();
  }

  public boolean isEmpty() {
    if (myMap.isEmpty()) return true;

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

  @NotNull
  public Set<K> keySet() {
    return myMap.keySet();
  }

  public int size() {
    return myMap.size();
  }

  public void put(final K key, Collection<V> values) {
    myMap.put(key, values);
  }

  /**
   * @deprecated use {@link #remove(Object, Object)} instead
   */
  @Deprecated
  public void removeValue(K key, V value) {
    remove(key, value);
  }

  public boolean remove(final K key, final V value) {
    final Collection<V> values = myMap.get(key);
    if (values != null) {
      boolean removed = values.remove(value);
      if (values.isEmpty()) {
        myMap.remove(key);
      }
      return removed;
    }
    return false;
  }

  @NotNull
  public Collection<? extends V> values() {
    if (values == null) {
      values = new AbstractCollection<V>() {
        @NotNull
        @Override
        public Iterator<V> iterator() {
          return new Iterator<V>() {

            private final Iterator<Collection<V>> mapIterator = myMap.values().iterator();

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
              itr.remove();
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

  @Nullable
  public Collection<V> remove(K key) {
    return myMap.remove(key);
  }

  @NotNull
  public static <K, V> MultiMap<K, V> emptyInstance() {
    @SuppressWarnings("unchecked") final MultiMap<K, V> empty = EMPTY;
    return empty;
  }

  /**
   * Null keys supported.
   */
  @NotNull
  public static <K, V> MultiMap<K, V> create() {
    return new MultiMap<K, V>();
  }

  @NotNull
  public static <K, V> MultiMap<K, V> create(@NotNull final TObjectHashingStrategy<K> strategy) {
    return new MultiMap<K, V>() {
      @NotNull
      @Override
      protected Map<K, Collection<V>> createMap() {
        return new THashMap<K, Collection<V>>(strategy);
      }
    };
  }

  @NotNull
  public static <K, V> MultiMap<K, V> createLinked() {
    return new LinkedMultiMap<K, V>();
  }

  @NotNull
  public static <K, V> MultiMap<K, V> createLinkedSet() {
    return new LinkedMultiMap<K, V>() {
      @NotNull
      @Override
      protected Collection<V> createCollection() {
        return ContainerUtil.newLinkedHashSet();
      }

      @NotNull
      @Override
      protected Collection<V> createEmptyCollection() {
        return Collections.emptySet();
      }
    };
  }

  @NotNull
  public static <K, V> MultiMap<K, V> createOrderedSet() {
    return new LinkedMultiMap<K, V>() {
      @NotNull
      @Override
      protected Collection<V> createCollection() {
        return new OrderedSet<V>();
      }

      @NotNull
      @Override
      protected Collection<V> createEmptyCollection() {
        return Collections.emptySet();
      }
    };
  }

  @NotNull
  public static <K, V> MultiMap<K, V> createSmart() {
    return new MultiMap<K, V>() {
      @NotNull
      @Override
      protected Map<K, Collection<V>> createMap() {
        return new THashMap<K, Collection<V>>();
      }
    };
  }

  @NotNull
  public static <K, V> MultiMap<K, V> createConcurrentSet() {
    return new ConcurrentMultiMap<K, V>() {
      @NotNull
      @Override
      protected Collection<V> createCollection() {
        return ContainerUtil.newConcurrentSet();
      }

      @NotNull
      @Override
      protected Collection<V> createEmptyCollection() {
        return Collections.emptySet();
      }
    };
  }

  @NotNull
  public static <K, V> MultiMap<K, V> createSet() {
    return createSet(ContainerUtil.<K>canonicalStrategy());
  }

  @NotNull
  public static <K, V> MultiMap<K, V> createSet(@NotNull final TObjectHashingStrategy<K> strategy) {
    return new MultiMap<K, V>() {
      @NotNull
      @Override
      protected Collection<V> createCollection() {
        return new SmartHashSet<V>();
      }

      @NotNull
      @Override
      protected Collection<V> createEmptyCollection() {
        return Collections.emptySet();
      }

      @NotNull
      @Override
      protected Map<K, Collection<V>> createMap() {
        return new THashMap<K, Collection<V>>(strategy);
      }
    };
  }

  @NotNull
  public static <K, V> MultiMap<K, V> createWeakKey() {
    return new MultiMap<K, V>() {
      @NotNull
      @Override
      protected Map<K, Collection<V>> createMap() {
        return ContainerUtil.createWeakMap();
      }
    };
  }

  public static <K, V> MultiMap<K, V> create(int initialCapacity, float loadFactor) {
    return new MultiMap<K, V>(initialCapacity, loadFactor);
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof MultiMap && myMap.equals(((MultiMap)o).myMap);
  }

  @Override
  public int hashCode() {
    return myMap.hashCode();
  }

  @Override
  public String toString() {
    return new java.util.HashMap<K, Collection<V>>(myMap).toString();
  }

  /**
   * @return immutable empty multi-map
   */
  public static <K, V> MultiMap<K, V> empty() {
    //noinspection unchecked
    return EMPTY;
  }

  private static class EmptyMap extends MultiMap {
    @NotNull
    @Override
    protected Map createMap() {
      return Collections.emptyMap();
    }

    @Override
    public void putValues(Object key, @NotNull Collection values) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void putValue(@Nullable Object key, Object value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void put(Object key, Collection values) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object key, Object value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public Collection remove(Object key) {
      throw new UnsupportedOperationException();
    }
  }
}