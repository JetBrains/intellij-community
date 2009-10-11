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



  public int size() {

    return size;

  }



  public boolean isEmpty() {

    return size() == 0;

  }



  public void clear() {

    clear(0);

  }



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



  public V put(final K key, final @NotNull V value) {

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

    size = size + 1;



    if (size > capacity) {

      rehash((int) (capacity * HashUtil.CAPACITY_MULTIPLE));

    }

    return null;

  }



  public boolean containsKey(final Object key) {

    return get(key) != null;

  }



  public V remove(final Object key) {

    final Entry<K, V>[] table = this.table;

    final int hash = HashUtil.hash(key);

    final int index = hash % table.length;

    Entry<K, V> e = table[index];



    if (e == null) return null;



    K entryKey;

    if (e.keyHash == hash && ((entryKey = e.key) == key || entryKey.equals(key))) {

      table[index] = e.hashNext;

    } else {

      for (; ;) {

        final Entry<K, V> last = e;

        e = e.hashNext;

        if (e == null) return null;

        if (e.keyHash == hash && ((entryKey = e.key) == key || entryKey.equals(key))) {

          last.hashNext = e.hashNext;

          break;

        }

      }

    }

    size = size - 1;

    return e.value;

  }



  public Set<K> keySet() {

    return new KeySet();

  }



  public Collection<V> values() {

    return new Values();

  }



  public Set<Map.Entry<K, V>> entrySet() {

    return new EntrySet();

  }



  private void init(int capacity) {

    table = new Entry[HashUtil.adjustTableSize((int) (capacity / loadFactor))];

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

      final Entry<K, V> e = (Entry<K, V>) entries.next();

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



    public K getKey() {

      return key;

    }



    public V getValue() {

      return value;

    }



    public V setValue(final V value) {

      final V result = this.value;

      this.value = value;

      return result;

    }

  }



  private abstract class HashIterator<T> implements Iterator<T> {



    private final Entry<K, V>[] table = HashMap.this.table;

    private int index = 0;

    private Entry<K, V> e = null;

    private Entry<K, V> last;



    HashIterator() {

      initNextEntry();

    }



    public boolean hasNext() {

      return e != null;

    }



    public void remove() {

      if (last == null) {

        throw new IllegalStateException();

      }

      HashMap.this.remove(last.key);

      last = null;

    }



    protected Entry<K, V> nextEntry() {

      final Entry<K, V> result = last = e;

      initNextEntry();

      return result;

    }



    private void initNextEntry() {

      Entry<K, V> result = e;

      if (result != null) {

        result = result.hashNext;

      }

      final Entry<K, V>[] table = this.table;

      while (result == null && index < table.length) {

        result = table[index++];

      }

      e = result;

    }

  }



  private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {



    public Iterator<Map.Entry<K, V>> iterator() {

      return new HashIterator<Map.Entry<K, V>>() {

        public Map.Entry<K, V> next() {

          return nextEntry();

        }

      };

    }



    public boolean contains(Object o) {

      if (!(o instanceof Map.Entry)) {

        return false;

      }

      final Map.Entry<K, V> e = (Map.Entry<K, V>) o;

      final V value = get(e.getKey());

      return value != null && value.equals(e.getValue());

    }



    public boolean remove(Object o) {

      if (!(o instanceof Map.Entry)) {

        return false;

      }

      final Map.Entry<K, V> e = (Map.Entry<K, V>) o;

      return HashMap.this.remove(e.getKey()) != null;

    }



    public int size() {

      return size;

    }



    public void clear() {

      HashMap.this.clear();

    }

  }



  private final class KeySet extends AbstractSet<K> {



    public Iterator<K> iterator() {

      return new HashIterator<K>() {

        public K next() {

          return nextEntry().key;

        }

      };

    }



    public int size() {

      return size;

    }



    public boolean contains(Object o) {

      return HashMap.this.containsKey(o);

    }



    public boolean remove(Object o) {

      return HashMap.this.remove(o) != null;

    }



    public void clear() {

      HashMap.this.clear();

    }

  }



  private final class Values extends AbstractCollection<V> {



    public Iterator<V> iterator() {

      return new HashIterator<V>() {

        public V next() {

          return nextEntry().value;

        }

      };

    }



    public int size() {

      return size;

    }



    public boolean contains(Object o) {

      return containsValue(o);

    }



    public void clear() {

      HashMap.this.clear();

    }

  }

}