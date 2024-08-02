// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.hash;

import it.unimi.dsi.fastutil.HashCommon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The same as {@link LinkedHashMap} but adapted to store long keys.
 * Null values are not supported.
 *
 * NOTE: not the same as {@link java.util.LinkedHashMap} -- this implementation supports .accessOrder,
 * which re-orders entries on .get() 
 */
@ApiStatus.Internal
public class LongLinkedHashMap<V> {
  private static final long NULL_KEY = 0L;

  private Entry<V>[] table;
  private Entry<V> top;
  private Entry<V> back;
  private int capacity;
  private int size;
  private final float loadFactor;
  private final boolean accessOrder;

  public LongLinkedHashMap(int capacity, float loadFactor, boolean accessOrder) {
    this.loadFactor = loadFactor;
    clear(capacity);
    this.accessOrder = accessOrder;
  }

  public int size() {
    return size;
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  public void clear() {
    if (isEmpty()) {
      return;
    }
    clear(0);
  }

  public V get(final long key) {
    final Entry<V>[] table = this.table;
    final int hash = hashCode(key);
    final int index = hash % table.length;

    for (Entry<V> e = table[index]; e != null; e = e.hashNext) {
      if (e.keyHash == hash && e.key == key) {
        moveToTop(e);
        return e.value;
      }
    }
    return null;
  }

  public V put(final long key, final @NotNull V value) {
    final Entry<V>[] table = this.table;
    final int hash = hashCode(key);
    final int index = hash % table.length;
    for (Entry<V> e = table[index]; e != null; e = e.hashNext) {
      if (e.keyHash == hash && e.key == key) {
        moveToTop(e);
        return e.setValue(value);
      }
    }
    final Entry<V> e = new Entry<>(key, value, hash);
    e.hashNext = table[index];
    table[index] = e;
    final Entry<V> top = this.top;
    e.next = top;
    if (top != null) {
      top.previous = e;
    }
    else {
      back = e;
    }
    this.top = e;
    size++;
    if (removeEldestEntry(back)) {
      doRemoveEldestEntry();
    }
    else if (size > capacity) {
      rehash((int)(capacity * HashUtil.CAPACITY_MULTIPLE));
    }
    return null;
  }

  public void doRemoveEldestEntry() {
    final V val = remove(back.key);
    assert val != null : "Entry was not removed. Possibly mutable key: " + back.key;
  }

  public boolean containsKey(final long key) {
    return get(key) != null;
  }

  public V remove(final long key) {
    final Entry<V>[] table = this.table;
    final int hash = hashCode(key);
    final int index = hash % table.length;
    Entry<V> e = table[index];
    if (e == null) {
      return null;
    }

    if (e.keyHash == hash && (e.key == key)) {
      table[index] = e.hashNext;
    }
    else {
      while (true) {
        final Entry<V> last = e;
        e = e.hashNext;
        if (e == null) {
          return null;
        }
        if (e.keyHash == hash && (e.key == key)) {
          last.hashNext = e.hashNext;
          break;
        }
      }
    }
    unlink(e);
    size--;
    return e.value;
  }

  public @NotNull Collection<V> values() {
    return new Values();
  }

  protected boolean removeEldestEntry(Entry<V> eldest) {
    return false;
  }

  private void init(int capacity) {
    table = new Entry[HashCommon.arraySize(capacity, loadFactor)];
    top = back = null;
    this.capacity = capacity;
  }

  private void clear(int capacity) {
    if (capacity < HashUtil.MIN_CAPACITY) {
      capacity = HashUtil.MIN_CAPACITY;
    }
    init(capacity);
    size = 0;
  }

  public long getLastKey() {
    return top != null ? top.key : NULL_KEY;
  }

  public @Nullable V getLastValue() {
    return top != null ? top.value : null;
  }

  public long getFirstKey() {
    return back != null ? back.key : NULL_KEY;
  }

  private void moveToTop(final Entry<V> e) {
    if (!accessOrder) {
      return;
    }

    final Entry<V> top = this.top;
    if (top != e) {
      final Entry<V> prev = e.previous;
      final Entry<V> next = e.next;
      prev.next = next;
      if (next != null) {
        next.previous = prev;
      }
      else {
        back = prev;
      }
      top.previous = e;
      e.next = top;
      e.previous = null;
      this.top = e;
    }
  }

  private void unlink(final Entry<V> e) {
    final Entry<V> prev = e.previous;
    final Entry<V> next = e.next;
    if (prev != null) {
      prev.next = next;
    }
    else {
      top = next;
    }
    if (next != null) {
      next.previous = prev;
    }
    else {
      back = prev;
    }

    // Help GC
    e.previous = null;
    e.next = null;
  }

  private void rehash(int capacity) {
    table = new Entry[HashCommon.arraySize(capacity, loadFactor)];
    this.capacity = capacity;
    final Entry<V>[] table = this.table;
    final int tableLen = table.length;
    for (Entry<V> e = back; e != null; e = e.previous) {
      final int hash = e.keyHash % tableLen;
      e.hashNext = table[hash];
      table[hash] = e;
    }
  }


  public @NotNull Set<Entry<V>> entrySet() {
    return new EntrySet();
  }

  public static final class Entry<V> {
    private final long key;
    private final int keyHash;
    private V value;
    private Entry<V> next;
    private Entry<V> previous;
    private Entry<V> hashNext;

    Entry(long key, V value, int hash) {
      this.key = key;
      keyHash = hash;
      this.value = value;
    }

    public long getKey() {
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

  private abstract class LinkedHashIterator<T> implements Iterator<T> {
    private Entry<V> e = back;
    private Entry<V> last;

    @Override
    public boolean hasNext() {
      return e != null;
    }

    @Override
    public void remove() {
      if (last == null) {
        throw new IllegalStateException();
      }
      LongLinkedHashMap.this.remove(last.key);
      last = null;
    }

    protected Entry<V> nextEntry() {
      final Entry<V> result = last = e;
      e = result.previous;
      return result;
    }
  }

  private final class EntrySet extends AbstractSet<Entry<V>> {
    @Override
    public @NotNull Iterator<Entry<V>> iterator() {
      return new LinkedHashIterator<Entry<V>>() {
        @Override
        public Entry<V> next() {
          return nextEntry();
        }
      };
    }

    @Override
    public boolean contains(Object o) {
      if (!(o instanceof Entry)) {
        return false;
      }
      final Entry<V> e = (Entry<V>)o;
      final V value = get(e.getKey());
      return value != null && value.equals(e.getValue());
    }

    @Override
    public boolean remove(Object o) {
      if (!(o instanceof Entry)) {
        return false;
      }
      final Entry<V> e = (Entry<V>)o;
      return LongLinkedHashMap.this.remove(e.getKey()) != null;
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public void clear() {
      LongLinkedHashMap.this.clear();
    }
  }

  private boolean containsValue(Object value) {
    for (Entry<V> entry : entrySet()) {
      if (entry.getValue().equals(value)) {
        return true;
      }
    }
    return false;
  }

  private static int hashCode(long value) {
    return Long.hashCode(value) & 0x7fffffff;
  }

  private final class Values extends AbstractCollection<V> {
    @Override
    public @NotNull Iterator<V> iterator() {
      return new LinkedHashIterator<V>() {
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
      LongLinkedHashMap.this.clear();
    }
  }
}
