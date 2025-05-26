// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.intcaches;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.HashCommon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** Specialization of {@link com.intellij.util.containers.LinkedCustomHashMap} for int keys */
@SuppressWarnings("DuplicatedCode")
@ApiStatus.Internal
public final class LinkedCustomIntObjectHashMap<V> {
  /** Fixed map load factor (not customizable) */
  private static final float LOAD_FACTOR = 0.6f;

  private Entry<V>[] entries;
  private Entry<V> top;
  private Entry<V> back;

  /** The mask for wrapping a position counter. */
  private transient int tableSizeMask;
  /** The current table size */
  private transient int tableSize;
  /** Number of entries in the set. */
  private int actualEntriesCount;

  /** Threshold after which we rehash, = {@code tableSize * LOAD_FACTOR}. */
  private transient int rehashAfterEntriesCount;

  private final @NotNull RemovalPolicy<? super V> eldestEntryRemovalPolicy;

  LinkedCustomIntObjectHashMap(@NotNull RemovalPolicy<? super V> eldestEntryRemovalPolicy) {
    this.eldestEntryRemovalPolicy = eldestEntryRemovalPolicy;

    init();
  }

  @FunctionalInterface
  @ApiStatus.Internal
  public interface RemovalPolicy<V> {
    /**
     * Called each time {@link #put(int, Object)} operation inserts new entry in the map -- i.e. not called on updates
     * of already existent entry.
     * The eldestKey/eldestValue are of the oldest entry at the moment.
     * @return true if the oldest entry should be removed, false if not, and map should be extended
     */
    boolean shouldRemoveEldest(int currentEntriesCount, int eldestKey, V eldestValue);
  }

  private void init() {
    tableSize = HashCommon.arraySize(Hash.DEFAULT_INITIAL_SIZE, LOAD_FACTOR);
    tableSizeMask = tableSize - 1;
    rehashAfterEntriesCount = HashCommon.maxFill(tableSize, LOAD_FACTOR);
    //noinspection unchecked
    entries = new Entry[tableSize];
    actualEntriesCount = 0;
  }

  public int size() {
    return actualEntriesCount;
  }

  public boolean isEmpty() {
    return actualEntriesCount == 0;
  }

  public void clear() {
    if (actualEntriesCount == 0) {
      return;
    }

    top = back = null;
    init();
  }

  public V get(int key) {
    Entry<V>[] entries = this.entries;
    int hash = hashKey(key);
    int index = hash & tableSizeMask;
    for (Entry<V> e = entries[index]; e != null; e = e.hashNext) {
      if (e.key == key) {
        moveToTop(e);
        return e.value;
      }
    }
    return null;
  }

  private static int hashKey(int key) {
    return HashCommon.mix(key);
  }

  public V put(int key, @NotNull V value) {
    Entry<V>[] table = this.entries;
    int hash = hashKey(key);
    int index = hash & tableSizeMask;
    for (Entry<V> e = table[index]; e != null; e = e.hashNext) {
      if (e.key == key) {
        moveToTop(e);
        return e.setValue(value);
      }
    }

    Entry<V> e = new Entry<>(key, value);
    e.hashNext = table[index];
    table[index] = e;
    Entry<V> top = this.top;
    e.next = top;
    if (top != null) {
      top.previous = e;
    }
    else {
      back = e;
    }
    this.top = e;
    actualEntriesCount++;
    if (eldestEntryRemovalPolicy.shouldRemoveEldest(actualEntriesCount, back.key, back.value)) {
      V removedValue = remove(back.key);
      if (removedValue == null) {
        throw new ConcurrentModificationException("LinkedIntHashMap.Entry was not removed. Likely concurrent modification? " + back.key);
      }
    }
    else if (actualEntriesCount >= rehashAfterEntriesCount) {
      rehash(HashCommon.arraySize(actualEntriesCount + 1, LOAD_FACTOR));
    }
    return null;
  }

  public boolean containsKey(int key) {
    return get(key) != null;
  }

  public V remove(int key) {
    Entry<V>[] entries = this.entries;
    int hash = hashKey(key);
    int index = hash & tableSizeMask;
    Entry<V> e = entries[index];
    if (e == null) {
      return null;
    }

    if (e.key == key) {
      entries[index] = e.hashNext;
    }
    else {
      for (; ; ) {
        Entry<V> last = e;
        e = e.hashNext;
        if (e == null) {
          return null;
        }
        if (e.key == key) {
          last.hashNext = e.hashNext;
          break;
        }
      }
    }
    unlink(e);
    actualEntriesCount--;
    return e.value;
  }

  public @NotNull Set<Integer> keySet() {
    return new KeySet();
  }

  public @NotNull Collection<V> values() {
    return new Values();
  }

  public @NotNull Set<Entry<V>> entrySet() {
    return new EntrySet();
  }

  private void moveToTop(Entry<V> e) {
    Entry<V> top = this.top;
    if (top == e) {
      return;
    }

    Entry<V> prev = e.previous;
    Entry<V> next = e.next;
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

  private void unlink(Entry<V> e) {
    Entry<V> prev = e.previous;
    Entry<V> next = e.next;
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

    // help GC
    e.previous = null;
    e.next = null;
  }

  private void rehash(int newTableSize) {
    tableSizeMask = newTableSize - 1;
    tableSize = newTableSize;
    rehashAfterEntriesCount = HashCommon.maxFill(tableSize, LOAD_FACTOR);

    //noinspection unchecked
    Entry<V>[] entries = new Entry[newTableSize];
    for (Entry<V> e = back; e != null; e = e.previous) {
      int index = hashKey(e.key) & tableSizeMask;
      e.hashNext = entries[index];
      entries[index] = e;
    }

    this.entries = entries;
  }

  public static final class Entry<V> implements Map.Entry<Integer, V> {
    private final int key;

    private V value;

    /** next item in hash-table bucket */
    private Entry<V> hashNext;

    private Entry<V> next;
    private Entry<V> previous;

    Entry(int key, V value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public Integer getKey() {
      return key();
    }

    public int key() {
      return key;
    }

    @Override
    public V getValue() {
      return value;
    }

    @Override
    public V setValue(V value) {
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
      LinkedCustomIntObjectHashMap.this.remove(last.key);
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
      if (!(o instanceof Map.Entry)) {
        return false;
      }

      @SuppressWarnings("unchecked")
      Map.Entry<Integer, V> e = (Map.Entry<Integer, V>)o;
      V value = get(e.getKey());
      return value != null && value.equals(e.getValue());
    }

    @Override
    public boolean remove(Object o) {
      if (!(o instanceof Map.Entry)) {
        return false;
      }

      @SuppressWarnings("unchecked")
      Map.Entry<Integer, V> e = (Map.Entry<Integer, V>)o;
      return LinkedCustomIntObjectHashMap.this.remove(e.getKey()) != null;
    }

    @Override
    public int size() {
      return actualEntriesCount;
    }

    @Override
    public void clear() {
      LinkedCustomIntObjectHashMap.this.clear();
    }
  }

  private final class KeySet extends AbstractSet<Integer> {
    @Override
    public @NotNull Iterator<Integer> iterator() {
      return new LinkedHashIterator<Integer>() {
        @Override
        public Integer next() {
          return nextEntry().key;
        }
      };
    }

    @Override
    public int size() {
      return actualEntriesCount;
    }

    @Override
    public boolean contains(Object o) {
      return containsKey((Integer)o);
    }

    @Override
    public boolean remove(Object o) {
      return LinkedCustomIntObjectHashMap.this.remove((Integer)o) != null;
    }

    @Override
    public void clear() {
      LinkedCustomIntObjectHashMap.this.clear();
    }
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
      return actualEntriesCount;
    }

    @Override
    public boolean contains(Object o) {
      if (o == null) {
        return false;
      }

      for (Entry<V> entry : entries) {
        if (entry != null && entry.value.equals(o)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void clear() {
      LinkedCustomIntObjectHashMap.this.clear();
    }
  }
}
