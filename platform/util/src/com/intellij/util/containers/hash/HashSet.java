// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers.hash;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @deprecated Use {@link java.util.HashSet}
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
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
    Logger.getInstance(getClass()).warn(new Exception("Use java.util.HashSet instead"));
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
    final Entry<E> e = new Entry<>(key);
    e.hashNext = table[index];
    table[index] = e;
    size++;
    if (size > capacity) {
      rehash((int)(capacity * HashUtil.CAPACITY_MULTIPLE));
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
    }
    else {
      for (; ; ) {
        final Entry<E> last = e;
        e = e.hashNext;
        if (e == null) return false;
        if (e.keyHash == hash && ((entryKey = e.key) == key || entryKey.equals(key))) {
          last.hashNext = e.hashNext;
          break;
        }
      }
    }
    size--;
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

    Entry(final E key) {
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
