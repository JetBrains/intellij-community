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

/*
 * @author max
 */
package com.intellij.util.containers;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.concurrency.JBLock;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import gnu.trove.THashMap;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public class LockPoolSynchronizedMap<K, V> extends THashMap<K, V> implements ConcurrentMap<K, V> {
  private static final int NUM_LOCKS = 256;
  private static final JBReentrantReadWriteLock[] ourLocks = new JBReentrantReadWriteLock[NUM_LOCKS];
  private static int ourLockAllocationCounter = 0;

  private final JBLock r;
  private final JBLock w;

  static {
    for (int i = 0; i < ourLocks.length; i++) {
      ourLocks[i] = LockFactory.createReadWriteLock();
    }
  }

  {
    final JBReentrantReadWriteLock mutex = allocateLock();
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

  private static JBReentrantReadWriteLock allocateLock() {
    ourLockAllocationCounter = (ourLockAllocationCounter + 1) % NUM_LOCKS;
    return ourLocks[ourLockAllocationCounter];
  }

  public int size() {
    r.lock();
    try {
      return super.size();
    }
    finally {
      r.unlock();
    }
  }

  public boolean isEmpty() {
    r.lock();
    try {
      return super.isEmpty();
    }
    finally {
      r.unlock();
    }
  }

  public boolean containsKey(Object key) {
    r.lock();
    try {
      return super.containsKey(key);
    }
    finally {
      r.unlock();
    }
  }

  public boolean containsValue(Object value) {
    r.lock();
    try {
      return super.containsValue(value);
    }
    finally {
      r.unlock();
    }
  }

  public V get(Object key) {
    r.lock();
    try {
      return super.get(key);
    }
    finally {
      r.unlock();
    }
  }

  public V put(K key, V value) {
    w.lock();
    try {
      return super.put(key, value);
    }
    finally {
      w.unlock();
    }
  }

  public V remove(Object key) {
    w.lock();
    try {
      return super.remove(key);
    }
    finally {
      w.unlock();
    }
  }

  public void putAll(Map<? extends K, ? extends V> map) {
    w.lock();
    try {
      super.putAll(map);
    }
    finally {
      w.unlock();
    }
  }

  public void clear() {
    w.lock();
    try {
      super.clear();
    }
    finally {
      w.unlock();
    }
  }

  public LockPoolSynchronizedMap<K, V> clone() {
    r.lock();
    try {
      return (LockPoolSynchronizedMap<K,V>)super.clone();
    }
    finally {
      r.unlock();
    }
  }

  public Set<K> keySet() {
    r.lock();
    try {
      return super.keySet();
    }
    finally {
      r.unlock();
    }
  }

  public Set<Map.Entry<K, V>> entrySet() {
    r.lock();
    try {
      return super.entrySet();
    }
    finally {
      r.unlock();
    }
  }

  public Collection<V> values() {
    r.lock();
    try {
      return super.values();
    }
    finally {
      r.unlock();
    }
  }

  public boolean replace(K key, V oldValue, V newValue) {
    w.lock();
    try {
      V prev = get(key);
      if (!Comparing.equal(oldValue, prev)) {
        return false;
      }

      if (newValue == null) {
        remove(key);
      }
      else {
        put(key, newValue);
      }
      return true;
    }
    finally {
      w.unlock();
    }
  }

  public V replace(K key, V newValue) {
    w.lock();
    try {
      V prev = get(key);

      if (newValue == null) {
        remove(key);
      }
      else {
        put(key, newValue);
      }
      return prev;
    }
    finally {
      w.unlock();
    }
  }

  public V putIfAbsent(K key, V value) {
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

  public boolean remove(Object key, Object oldValue) {
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
