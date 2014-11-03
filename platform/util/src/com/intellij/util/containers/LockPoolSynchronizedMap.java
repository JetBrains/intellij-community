/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.util.containers;

import com.intellij.openapi.util.Comparing;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Deprecated // use com.intellij.util.containers.ContainerUtil.newConcurrentMap() instead
public class LockPoolSynchronizedMap<K, V> extends THashMap<K, V> implements ConcurrentMap<K, V> {
  private final Lock r;
  private final Lock w;

  private static final StripedLockHolder<ReentrantReadWriteLock> LOCKS = new StripedLockHolder<ReentrantReadWriteLock>(ReentrantReadWriteLock.class) {
    @NotNull
    @Override
    protected ReentrantReadWriteLock create() {
      return new ReentrantReadWriteLock();
    }
  };

  {
    final ReentrantReadWriteLock mutex = LOCKS.allocateLock();
    r = mutex.readLock();
    w = mutex.writeLock();
  }
  public LockPoolSynchronizedMap() {
  }

  public LockPoolSynchronizedMap(final int initialCapacity) {
    super(initialCapacity);
  }

  public LockPoolSynchronizedMap(final int initialCapacity, final float loadFactor) {
    super(initialCapacity, loadFactor);
  }

  @Override
  public int size() {
    r.lock();
    try {
      return super.size();
    }
    finally {
      r.unlock();
    }
  }

  @Override
  public boolean isEmpty() {
    r.lock();
    try {
      return super.isEmpty();
    }
    finally {
      r.unlock();
    }
  }

  @Override
  public boolean containsKey(Object key) {
    r.lock();
    try {
      return super.containsKey(key);
    }
    finally {
      r.unlock();
    }
  }

  @Override
  public boolean containsValue(Object value) {
    r.lock();
    try {
      return super.containsValue(value);
    }
    finally {
      r.unlock();
    }
  }

  @Override
  public V get(Object key) {
    r.lock();
    try {
      return super.get(key);
    }
    finally {
      r.unlock();
    }
  }

  @Override
  public V put(K key, V value) {
    w.lock();
    try {
      return super.put(key, value);
    }
    finally {
      w.unlock();
    }
  }

  @Override
  public V remove(Object key) {
    w.lock();
    try {
      return super.remove(key);
    }
    finally {
      w.unlock();
    }
  }

  @Override
  public void putAll(@NotNull Map<? extends K, ? extends V> map) {
    w.lock();
    try {
      super.putAll(map);
    }
    finally {
      w.unlock();
    }
  }

  @Override
  public void clear() {
    w.lock();
    try {
      super.clear();
    }
    finally {
      w.unlock();
    }
  }

  @Override
  public LockPoolSynchronizedMap<K, V> clone() {
    r.lock();
    try {
      return (LockPoolSynchronizedMap<K,V>)super.clone();
    }
    finally {
      r.unlock();
    }
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    r.lock();
    try {
      return super.keySet();
    }
    finally {
      r.unlock();
    }
  }

  @NotNull
  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    r.lock();
    try {
      return super.entrySet();
    }
    finally {
      r.unlock();
    }
  }

  @NotNull
  @Override
  public Collection<V> values() {
    r.lock();
    try {
      return super.values();
    }
    finally {
      r.unlock();
    }
  }

  @Override
  public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
    w.lock();
    try {
      V prev = get(key);
      if (!Comparing.equal(oldValue, prev)) {
        return false;
      }

      put(key, newValue);
      return true;
    }
    finally {
      w.unlock();
    }
  }

  @Override
  public V replace(@NotNull K key, @NotNull V newValue) {
    w.lock();
    try {
      V prev = get(key);

      put(key, newValue);
      return prev;
    }
    finally {
      w.unlock();
    }
  }

  @Override
  public V putIfAbsent(@NotNull K key, V value) {
    w.lock();
    try {
      V prev = get(key);
      if (prev == null) {
        put(key, value);
        return value;
      }
      else {
        return prev;
      }
    }
    finally {
      w.unlock();
    }
  }

  @Override
  public boolean remove(@NotNull Object key, Object oldValue) {
    w.lock();
    try {
      V currentValue = get(key);
      return Comparing.equal(oldValue, currentValue) && super.remove(key) != null;
    }
    finally {
      w.unlock();
    }
  }
}
