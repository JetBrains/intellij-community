/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * a ConcurrentMap which computes the value associated with the key (via {@link #create(Object)} method) on first {@link #get(Object)} access.
 * THREAD SAFE.
 * It's guaranteed that two {@link #get(Object)} method calls with the same key will return the same value (i.e. the created value stored atomically).
 * For not thread-safe (but possible faster and more memory-efficient) alternative please use {@link FactoryMap}
 */
public abstract class ConcurrentFactoryMap<K,V> implements ConcurrentMap<K,V> {
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("factoryMap");

  private final ConcurrentMap<K, V> myMap = createMap();

  /**
   * Use {@link #createMap(Function)} instead
   * TODO to remove in IDEA 2018
   */
  @Deprecated
  public ConcurrentFactoryMap() {
  }

  @Nullable
  protected abstract V create(K key);

  @Override
  public V get(Object key) {
    ConcurrentMap<K, V> map = myMap;
    K k = notNull(key);
    V value = map.get(k);
    if (value == null) {
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      value = create((K)key);
      if (stamp.mayCacheNow()) {
        V v = notNull(value);
        value = ConcurrencyUtil.cacheOrGet(map, k, v);
      }
    }
    return nullize(value);
  }

  @Nullable
  private static <T> T nullize(T value) {
    return value == FAKE_NULL() ? null : value;
  }

  private static <T> T FAKE_NULL() {
    //noinspection unchecked
    return (T)ObjectUtils.NULL;
  }

  private static <T> T notNull(final Object key) {
    //noinspection unchecked
    return key == null ? ConcurrentFactoryMap.<T>FAKE_NULL() : (T)key;
  }

  @Override
  public final boolean containsKey(Object key) {
    return myMap.containsKey(notNull(key));
  }

  @Override
  public V put(K key, V value) {
    K k = notNull(key);
    V v = notNull(value);
    v = myMap.put(k, v);
    return nullize(v);
  }

  @Override
  public V remove(Object key) {
    V v = myMap.remove(notNull(key));
    return nullize(v);
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    return new CollectionWrapper.Set<K>(myMap.keySet());
  }

  public boolean removeValue(Object value) {
    Object t = notNull(value);
    //noinspection SuspiciousMethodCalls
    return myMap.values().remove(t);
  }

  @Override
  public void clear() {
    myMap.clear();
  }

  @Override
  public int size() {
    return myMap.size();
  }

  @Override
  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  @Override
  public boolean containsValue(final Object value) {
    return myMap.containsValue(notNull(value));
  }

  @Override
  public void putAll(@NotNull final Map<? extends K, ? extends V> m) {
    for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  @NotNull
  @Override
  public Collection<V> values() {
    return new CollectionWrapper<V>(myMap.values());
  }

  @NotNull
  @Override
  public Set<Entry<K, V>> entrySet() {
    return new CollectionWrapper.Set<Entry<K, V>>(myMap.entrySet()) {
      @Override
      public Object wrap(Object val) {
        return val instanceof EntryWrapper ? ((EntryWrapper)val).myEntry : val;
      }

      @Override
      public Entry<K, V> unwrap(Entry<K, V> val) {
        return val.getKey() == FAKE_NULL() || val.getValue() == FAKE_NULL() ? new EntryWrapper<K, V>(val) : val;
      }
    };
  }

  @NotNull
  protected ConcurrentMap<K, V> createMap() {
    return ContainerUtil.newConcurrentMap();
  }

  @Override
  public V putIfAbsent(@NotNull K key, V value) {
    return nullize(myMap.putIfAbsent(ConcurrentFactoryMap.<K>notNull(key), ConcurrentFactoryMap.<V>notNull(value)));
  }

  @Override
  public boolean remove(@NotNull Object key, Object value) {
    return myMap.remove(ConcurrentFactoryMap.<K>notNull(key), ConcurrentFactoryMap.<V>notNull(value));
  }

  @Override
  public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
    return myMap.replace(ConcurrentFactoryMap.<K>notNull(key), ConcurrentFactoryMap.<V>notNull(oldValue), ConcurrentFactoryMap.<V>notNull(newValue));
  }

  @Override
  public V replace(@NotNull K key, @NotNull V value) {
    return nullize(myMap.replace(ConcurrentFactoryMap.<K>notNull(key), ConcurrentFactoryMap.<V>notNull(value)));
  }

  @NotNull
  public static <T, V> ConcurrentMap<T, V> createMap(@NotNull final Function<T, V> computeValue) {
    //noinspection deprecation
    return new ConcurrentFactoryMap<T, V>() {
      @Nullable
      @Override
      protected V create(T key) {
        return computeValue.fun(key);
      }
    };
  }
  @NotNull
  public static <K, V> ConcurrentMap<K, V> createMap(@NotNull final Function<K, V> computeValue, @NotNull final Producer<ConcurrentMap<K,V>> mapCreator) {
    //noinspection deprecation
    return new ConcurrentFactoryMap<K, V>() {
      @Nullable
      @Override
      protected V create(K key) {
        return computeValue.fun(key);
      }

      @NotNull
      @Override
      protected ConcurrentMap<K, V> createMap() {
        return mapCreator.produce();
      }
    };
  }

  /**
   * @return Concurrent factory map with weak keys, strong values
   */
  @NotNull
  public static <T, V> ConcurrentMap<T, V> createWeakMap(@NotNull Function<T, V> compute) {
    return createMap(compute, new Producer<ConcurrentMap<T, V>>() {
      @Override
      public ConcurrentMap<T, V> produce() {return ContainerUtil.createConcurrentWeakMap();}
    });
  }

  /**
   * needed for compatibility in case of moronic subclassing
   * TODO to remove in IDEA 2018
   */
  @Deprecated 
  public V getOrDefault(Object key, V defaultValue) {
      V v;
      return (v = get(key)) != null ? v : defaultValue;
  }

  private static class CollectionWrapper<K> extends AbstractCollection<K> {
    private final Collection<K> myDelegate;

    CollectionWrapper(Collection<K> delegate) {
      myDelegate = delegate;
    }

    @NotNull
    @Override
    public Iterator<K> iterator() {
      return new Iterator<K>() {
        Iterator<K> it = myDelegate.iterator();
        @Override
        public boolean hasNext() {
          return it.hasNext();
        }
        @Override
        public K next() {
          return unwrap(it.next());
        }
        @Override
        public void remove() {
          it.remove();
        }
      };
    }

    @Override
    public int size() {
      return myDelegate.size();
    }

    @Override
    public boolean contains(Object o) {
      return myDelegate.contains(wrap(o));
    }

    @Override
    public boolean remove(Object o) {
      return myDelegate.remove(wrap(o));
    }

    protected Object wrap(Object val) {
      return notNull(val);
    }
    protected K unwrap(K val) {
      return nullize(val);
    }

    private static class Set<K> extends CollectionWrapper<K> implements java.util.Set<K> {
      public Set(Collection<K> delegate) {
        super(delegate);
      }
    }

    protected static class EntryWrapper<K, V> implements Entry<K, V> {
      final Entry<K, V> myEntry;
      private EntryWrapper(Entry<K, V> entry) {
        myEntry = entry;
      }

      @Override
      public K getKey() {
        return nullize(myEntry.getKey());
      }

      @Override
      public V getValue() {
        return nullize(myEntry.getValue());
      }

      @Override
      public V setValue(V value) {
        return myEntry.setValue(ConcurrentFactoryMap.<V>notNull(value));
      }

      @Override
      public int hashCode() {
        return myEntry.hashCode();
      }

      @Override
      public boolean equals(Object obj) {
        return myEntry.equals(obj instanceof EntryWrapper ? ((EntryWrapper)obj).myEntry : obj);
      }
    }
  }
}
