// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.WeakHashMap;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A map where each key can be associated with multiple values.
 * <p>
 * Uses {@link java.util.HashMap} by defaults. A different map implementation can be passed using the {@link #MultiMap(Map)} constructor.
 * @see BidirectionalMultiMap
 */
@Debug.Renderer(text = "\"size = \" + size()", hasChildren = "!isEmpty()", childrenArray = "entrySet().toArray()")
@ApiStatus.NonExtendable
public class MultiMap<K, V> implements Serializable {
  private static final MultiMap<?,?> EMPTY = new EmptyMap();

  private static final long serialVersionUID = -2632269270151455493L;

  protected final Map<K, Collection<V>> myMap;
  private Collection<V> values;

  public MultiMap() {
    myMap = new HashMap<>();
  }

  public MultiMap(@NotNull Map<K, Collection<V>> map) {
    myMap = map;
  }

  public MultiMap(int expectedSize) {
    myMap = new HashMap<>(expectedSize);
  }

  @SuppressWarnings("CopyConstructorMissesField")
  public MultiMap(@NotNull MultiMap<? extends K, ? extends V> toCopy) {
    this();
    putAllValues(toCopy);
  }

  public @NotNull MultiMap<@NotNull K, V> copy() {
    return new MultiMap<>(this);
  }

  public MultiMap(int initialCapacity, float loadFactor) {
    myMap = new HashMap<>(initialCapacity, loadFactor);
  }

  protected @NotNull Collection<V> createCollection() {
    return new SmartList<>();
  }

  protected @NotNull Collection<V> createEmptyCollection() {
    return Collections.emptyList();
  }

  public final void putAllValues(@NotNull MultiMap<? extends @NotNull K, ? extends V> from) {
    for (Map.Entry<? extends K, ? extends Collection<? extends V>> entry : from.entrySet()) {
      putValues(entry.getKey(), entry.getValue());
    }
  }

  public final @NotNull Map<K, Collection<V>> toHashMap() {
    if (myMap instanceof HashMap) {
      //noinspection unchecked
      return (Map<K, Collection<V>>)((HashMap<K, Collection<V>>)myMap).clone();
    }
    else {
      return new HashMap<>(myMap);
    }
  }

  public final void putAllValues(@NotNull Map<? extends K, ? extends V> from) {
    for (Map.Entry<? extends K, ? extends V> entry : from.entrySet()) {
      putValue(entry.getKey(), entry.getValue());
    }
  }

  public final void putValues(K key, @NotNull Collection<? extends V> values) {
    getModifiable(key).addAll(values);
  }

  public final void putValue(@Nullable K key, V value) {
    getModifiable(key).add(value);
  }

  public final @NotNull Set<Map.Entry<K, Collection<V>>> entrySet() {
    return myMap.entrySet();
  }

  /**
   * Prohibits modification of collections for existing keys and returns view of this as a map.
   */
  public final @NotNull Map<K, Collection<V>> freezeValues() {
    if (isEmpty()) {
      return Collections.emptyMap();
    }

    myMap.replaceAll((k, v) -> Collections.unmodifiableCollection(v));
    return Collections.unmodifiableMap(myMap);
  }

  public final boolean isEmpty() {
    if (myMap.isEmpty()) {
      return true;
    }

    for (Collection<V> valueList : myMap.values()) {
      if (!valueList.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  public final boolean containsKey(K key) {
    return myMap.containsKey(key);
  }

  public final boolean containsScalarValue(V value) {
    for (Collection<V> valueList : myMap.values()) {
      if (valueList.contains(value)) {
        return true;
      }
    }
    return false;
  }

  public final @NotNull Collection<V> get(K key) {
    Collection<V> collection = myMap.get(key);
    return collection == null ? createEmptyCollection() : collection;
  }

  public final @NotNull Collection<V> getOrPut(K key, Supplier<? extends V> defaultValue) {
    Collection<V> collection = getModifiable(key);
    if (collection.isEmpty()) {
      collection.add(defaultValue.get());
    }
    return collection;
  }

  public final @NotNull Collection<V> getModifiable(K key) {
    return myMap.computeIfAbsent(key, __ -> createCollection());
  }

  public final @NotNull Set<K> keySet() {
    return myMap.keySet();
  }

  public final int size() {
    return myMap.size();
  }

  public final void put(K key, Collection<V> values) {
    myMap.put(key, values);
  }

  /**
   * @deprecated use {@link #remove(Object, Object)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public final void removeValue(K key, V value) {
    remove(key, value);
  }

  public boolean remove(K key, V value) {
    Collection<V> values = myMap.get(key);
    if (values == null) {
      return false;
    }

    boolean removed = values.remove(value);
    if (values.isEmpty()) {
      myMap.remove(key);
    }
    return removed;
  }

  public final @NotNull Collection<V> values() {
    if (values != null) {
      return values;
    }

    values = new AbstractCollection<V>() {
      @Override
      public @NotNull Iterator<V> iterator() {
        return new Iterator<V>() {
          private final Iterator<Collection<V>> mapIterator = myMap.values().iterator();

          private Iterator<V> itr = Collections.emptyIterator();

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
    return values;
  }

  public final void clear() {
    myMap.clear();
  }

  public final @Nullable Collection<V> remove(K key) {
    return myMap.remove(key);
  }

  public static @NotNull <K, V> MultiMap<K, V> create() {
    return new MultiMap<>();
  }

  public static @NotNull <K, V> MultiMap<K, V> createIdentity() {
    return new MultiMap<>(new IdentityHashMap<>());
  }

  public static @NotNull <K, V> MultiMap<K, V> createLinked() {
    return new MultiMap<>(new LinkedHashMap<>());
  }

  public static @NotNull <K, V> MultiMap<K, V> createLinkedSet() {
    return new MultiMap<K, V>(new LinkedHashMap<>()) {
      @Override
      protected @NotNull Collection<V> createCollection() {
        return new LinkedHashSet<>();
      }

      @Override
      protected @NotNull Collection<V> createEmptyCollection() {
        return Collections.emptySet();
      }
    };
  }

  public static @NotNull <K, V> MultiMap<K, V> createOrderedSet() {
    return new MultiMap<K, V>(new LinkedHashMap<>()) {
      @Override
      protected @NotNull Collection<V> createCollection() {
        return new OrderedSet<>();
      }

      @Override
      protected @NotNull Collection<V> createEmptyCollection() {
        return Collections.emptySet();
      }
    };
  }

  public static @NotNull <K, V> MultiMap<K, V> createConcurrent() {
    return new MultiMap<K, V>(new ConcurrentHashMap<>()) {
      @Override
      protected @NotNull Collection<V> createCollection() {
        return ContainerUtil.createLockFreeCopyOnWriteList();
      }
    };
  }

  public static @NotNull <K, V> MultiMap<K, V> createConcurrentSet() {
    return new MultiMap<K, V>(new ConcurrentHashMap<>()) {
      @Override
      protected @NotNull Collection<V> createCollection() {
        return ContainerUtil.newConcurrentSet();
      }

      @Override
      protected @NotNull Collection<V> createEmptyCollection() {
        return Collections.emptySet();
      }
    };
  }

  public static @NotNull <K, V> MultiMap<K, V> createSet() {
    return new MultiMap<K, V>() {
      @Override
      protected @NotNull Collection<V> createCollection() {
        return new SmartHashSet<>();
      }

      @Override
      protected @NotNull Collection<V> createEmptyCollection() {
        return Collections.emptySet();
      }
    };
  }

  public static @NotNull <K, V> MultiMap<K, V> createSet(@NotNull Map<K, Collection<V>> map) {
    return new MultiMap<K, V>(map) {
      @Override
      protected @NotNull Collection<V> createCollection() {
        return new SmartHashSet<>();
      }

      @Override
      protected @NotNull Collection<V> createEmptyCollection() {
        return Collections.emptySet();
      }
    };
  }

  public static @NotNull <K, V> MultiMap<@NotNull K, V> createWeakKey() {
    return new MultiMap<>(new WeakHashMap<>());
  }

  public static <K, V> MultiMap<K, V> create(int initialCapacity, float loadFactor) {
    return new MultiMap<>(initialCapacity, loadFactor);
  }

  @Override
  public final boolean equals(Object o) {
    return this == o || o instanceof MultiMap && myMap.equals(((MultiMap<?,?>)o).myMap);
  }

  @Override
  public final int hashCode() {
    return myMap.hashCode();
  }

  @Override
  public final String toString() {
    return myMap.toString();
  }

  public static @NotNull <K, V> MultiMap<K, V> empty() {
    //noinspection unchecked
    return (MultiMap<K, V>)EMPTY;
  }

  private static final class EmptyMap extends MultiMap<Object, Object> {
    private EmptyMap() {
      super(Collections.emptyMap());
    }
  }
}
