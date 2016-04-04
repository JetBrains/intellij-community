/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.containers.hash;


import org.jetbrains.annotations.NotNull;

import java.util.*;


public class HashMap<K, V> extends AbstractMap<K, V> implements Map<K, V> {


  private Entry<K, V>[] table;

  private int capacity;

  private int size;

  private final float loadFactor;


  public HashMap() {

    this(0);
  }


  public HashMap(int capacity) {

    this(capacity, HashUtil.DEFAULT_LOAD_FACTOR);
  }


  public HashMap(int capacity, float loadFactor) {

    this.loadFactor = loadFactor;

    clear(capacity);
  }


  @Override
  public int size() {

    return size;
  }


  @Override
  public boolean isEmpty() {

    return size() == 0;
  }


  @Override
  public void clear() {

    if (isEmpty())

      return;

    clear(0);
  }


  @Override
  public V get(final Object key) {

    final Entry<K, V>[] table = this.table;

    final int hash = HashUtil.hash(key);

    final int index = hash % table.length;


    for (Entry<K, V> e = table[index]; e != null; e = e.hashNext) {

      final K entryKey;

      if (e.keyHash == hash && ((entryKey = e.key) == key || entryKey.equals(key))) {

        return e.value;
      }
    }


    return null;
  }


  @Override
  public V put(final K key, @NotNull final V value) {

    final Entry<K, V>[] table = this.table;

    final int hash = HashUtil.hash(key);

    final int index = hash % table.length;


    for (Entry<K, V> e = table[index]; e != null; e = e.hashNext) {

      final K entryKey;

      if (e.keyHash == hash && ((entryKey = e.key) == key || entryKey.equals(key))) {

        return e.setValue(value);
      }
    }


    final Entry<K, V> e = new Entry<K, V>(key, value);

    e.hashNext = table[index];

    table[index] = e;

    size += 1;


    if (size > capacity) {

      rehash((int)(capacity * HashUtil.CAPACITY_MULTIPLE));
    }

    return null;
  }


  @Override
  public boolean containsKey(final Object key) {

    return get(key) != null;
  }


  @Override
  public V remove(final Object key) {

    final Entry<K, V>[] table = this.table;

    final int hash = HashUtil.hash(key);

    final int index = hash % table.length;

    Entry<K, V> e = table[index];


    if (e == null) return null;


    K entryKey;

    if (e.keyHash == hash && ((entryKey = e.key) == key || entryKey.equals(key))) {

      table[index] = e.hashNext;
    }
    else {

      while (true) {

        final Entry<K, V> last = e;

        e = e.hashNext;

        if (e == null) return null;

        if (e.keyHash == hash && ((entryKey = e.key) == key || entryKey.equals(key))) {

          last.hashNext = e.hashNext;

          break;
        }
      }
    }

    size -= 1;

    return e.value;
  }


  @NotNull
  @Override
  public Set<K> keySet() {

    return new KeySet();
  }


  @NotNull
  @Override
  public Collection<V> values() {

    return new Values();
  }


  @NotNull
  @Override
  public Set<Map.Entry<K, V>> entrySet() {

    return new EntrySet();
  }


  private void init(int capacity) {

    table = new Entry[HashUtil.adjustTableSize((int)(capacity / loadFactor))];

    this.capacity = capacity;
  }


  private void clear(int capacity) {

    if (capacity < HashUtil.MIN_CAPACITY) {

      capacity = HashUtil.MIN_CAPACITY;
    }

    init(capacity);

    size = 0;
  }


  private void rehash(int capacity) {

    final Iterator<Map.Entry<K, V>> entries = entrySet().iterator();

    init(capacity);

    final Entry<K, V>[] table = this.table;

    final int tableLen = table.length;

    while (entries.hasNext()) {

      final Entry<K, V> e = (Entry<K, V>)entries.next();

      final int hash = e.keyHash % tableLen;

      e.hashNext = table[hash];

      table[hash] = e;
    }
  }


  private static class Entry<K, V> implements Map.Entry<K, V> {


    private final K key;

    private final int keyHash;

    private V value;

    private Entry<K, V> hashNext;


    public Entry(final K key, final V value) {

      this.key = key;

      keyHash = HashUtil.hash(key);

      this.value = value;
    }


    @Override
    public K getKey() {

      return key;
    }


    @Override
    public V getValue() {

      return value;
    }


    @Override
    public V setValue(final V value) {

      final V result = this.value;

      this.value = value;

      return result;
    }
  }


  private abstract class HashIterator<T> implements Iterator<T> {


    private final HashMap.Entry<K, V>[] table = HashMap.this.table;

    private int index = 0;

    private HashMap.Entry<K, V> e = null;

    private HashMap.Entry<K, V> last;


    HashIterator() {

      initNextEntry();
    }


    @Override
    public boolean hasNext() {

      return e != null;
    }


    @Override
    public void remove() {

      if (last == null) {

        throw new IllegalStateException();
      }

      HashMap.this.remove(last.key);

      last = null;
    }


    protected HashMap.Entry<K, V> nextEntry() {

      final HashMap.Entry<K, V> result = last = e;

      initNextEntry();

      return result;
    }


    private void initNextEntry() {

      HashMap.Entry<K, V> result = e;

      if (result != null) {

        result = result.hashNext;
      }

      final HashMap.Entry<K, V>[] table = this.table;

      while (result == null && index < table.length) {

        result = table[index++];
      }

      e = result;
    }
  }


  private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {


    @NotNull
    @Override
    public Iterator<Map.Entry<K, V>> iterator() {

      return new HashIterator<Map.Entry<K, V>>() {

        @Override
        public Map.Entry<K, V> next() {

          return nextEntry();
        }
      };
    }


    @Override
    public boolean contains(Object o) {

      if (!(o instanceof Map.Entry)) {

        return false;
      }

      final Map.Entry<K, V> e = (Map.Entry<K, V>)o;

      final V value = get(e.getKey());

      return value != null && value.equals(e.getValue());
    }


    @Override
    public boolean remove(Object o) {

      if (!(o instanceof Map.Entry)) {

        return false;
      }

      final Map.Entry<K, V> e = (Map.Entry<K, V>)o;

      return HashMap.this.remove(e.getKey()) != null;
    }


    @Override
    public int size() {

      return size;
    }


    @Override
    public void clear() {

      HashMap.this.clear();
    }
  }


  private final class KeySet extends AbstractSet<K> {


    @NotNull
    @Override
    public Iterator<K> iterator() {

      return new HashIterator<K>() {

        @Override
        public K next() {

          return nextEntry().key;
        }
      };
    }


    @Override
    public int size() {

      return size;
    }


    @Override
    public boolean contains(Object o) {

      return HashMap.this.containsKey(o);
    }


    @Override
    public boolean remove(Object o) {

      return HashMap.this.remove(o) != null;
    }


    @Override
    public void clear() {

      HashMap.this.clear();
    }
  }


  private final class Values extends AbstractCollection<V> {


    @NotNull
    @Override
    public Iterator<V> iterator() {

      return new HashIterator<V>() {

        @Override
        public V next() {

          return nextEntry().value;
        }
      };
    }


    @Override
    public int size() {

      return size;
    }


    @Override
    public boolean contains(Object o) {

      return containsValue(o);
    }


    @Override
    public void clear() {

      HashMap.this.clear();
    }
  }
}