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

import java.util.AbstractSet;

import java.util.Iterator;

import java.util.Set;



public class HashSet<E> extends AbstractSet<E> implements Set<E> {



  private Entry<E>[] table;

  private int capacity;

  private int size;

  private final float loadFactor;



  public HashSet() {

    this(0);

  }



  public HashSet(int capacity) {

    this(capacity, HashUtil.DEFAULT_LOAD_FACTOR);

  }



  public HashSet(int capacity, float loadFactor) {

    this.loadFactor = loadFactor;

    clear(capacity);

  }



  @Override
  public boolean contains(Object key) {

    final Entry<E>[] table = this.table;

    final int hash = HashUtil.hash(key);

    final int index = hash % table.length;



    for (Entry<E> e = table[index]; e != null; e = e.hashNext) {

      final E entryKey;

      if (e.keyHash == hash && ((entryKey = e.key) == key || entryKey.equals(key))) {

        return true;

      }

    }



    return false;

  }



  @Override
  public boolean add(E key) {

    final Entry<E>[] table = this.table;

    final int hash = HashUtil.hash(key);

    final int index = hash % table.length;



    for (Entry<E> e = table[index]; e != null; e = e.hashNext) {

      final E entryKey;

      if (e.keyHash == hash && ((entryKey = e.key) == key || entryKey.equals(key))) {

        return false;

      }

    }



    final Entry<E> e = new Entry<E>(key);

    e.hashNext = table[index];

    table[index] = e;

    size = size + 1;



    if (size > capacity) {

      rehash((int) (capacity * HashUtil.CAPACITY_MULTIPLE));

    }

    return true;

  }



  @Override
  public boolean remove(Object key) {

    final Entry<E>[] table = this.table;

    final int hash = HashUtil.hash(key);

    final int index = hash % table.length;

    Entry<E> e = table[index];



    if (e == null) return false;



    E entryKey;

    if (e.keyHash == hash && ((entryKey = e.key) == key || entryKey.equals(key))) {

      table[index] = e.hashNext;

    } else {

      for (; ;) {

        final Entry<E> last = e;

        e = e.hashNext;

        if (e == null) return false;

        if (e.keyHash == hash && ((entryKey = e.key) == key || entryKey.equals(key))) {

          last.hashNext = e.hashNext;

          break;

        }

      }

    }

    size = size - 1;

    return true;

  }



  @NotNull
  @Override
  public Iterator<E> iterator() {

    return new HashSetIterator<E>() {

      @Override
      public E next() {

        return nextEntry().key;

      }

    };

  }



  @Override
  public int size() {

    return size;

  }



  @Override
  public boolean isEmpty() {

    return size() == 0;

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

    final Iterator<Entry<E>> entries = new HashSetIterator<Entry<E>>() {

      @Override
      public Entry<E> next() {

        return nextEntry();

      }

    };

    init(capacity);

    final Entry<E>[] table = this.table;

    final int tableLen = table.length;

    while (entries.hasNext()) {

      final Entry<E> e = entries.next();

      final int hash = e.keyHash % tableLen;

      e.hashNext = table[hash];

      table[hash] = e;

    }

  }



  private static class Entry<E> {



    private final E key;

    private final int keyHash;

    private Entry<E> hashNext;



    public Entry(final E key) {

      this.key = key;

      keyHash = HashUtil.hash(key);

    }

  }



  private abstract class HashSetIterator<T> implements Iterator<T> {



    private final Entry<E>[] table = HashSet.this.table;

    private int index = 0;

    private Entry<E> e = null;

    private Entry<E> last;



    HashSetIterator() {

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

      HashSet.this.remove(last.key);

      last = null;

    }



    protected Entry<E> nextEntry() {

      final Entry<E> result = last = e;

      initNextEntry();

      return result;

    }



    private void initNextEntry() {

      Entry<E> result = e;

      if (result != null) {

        result = result.hashNext;

      }

      final Entry<E>[] table = this.table;

      while (result == null && index < table.length) {

        result = table[index++];

      }

      e = result;

    }

  }

}

