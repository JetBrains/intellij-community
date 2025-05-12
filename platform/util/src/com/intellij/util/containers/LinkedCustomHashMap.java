// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.containers.hash.EqualityPolicy;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.HashCommon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

@SuppressWarnings("DuplicatedCode")
@ApiStatus.Internal
public final class LinkedCustomHashMap<K, V> {
  private Entry<K, V>[] entries;
  private Entry<K, V> top;
  private Entry<K, V> back;

  /**
   * The mask for wrapping a position counter.
   */
  private transient int mask;
  /**
   * The current table size. Note that an additional element is allocated for storing the null key.
   */
  private transient int n;
  private final RemoveCallback<K, V> removeEldestEntry;
  /**
   * Threshold after which we rehash. It must be the table size times {@link #LOAD_FACTOR}.
   */
  private transient int maxFill;
  /**
   * Number of entries in the set.
   */
  private int size;
  /**
   * The acceptable load factor.
   */
  private static final float LOAD_FACTOR = 0.6f;

  private final EqualityPolicy<? super K> hashingStrategy;

  @TestOnly
  public LinkedCustomHashMap(@NotNull RemoveCallback<K, V> removeEldestEntry) {
    //noinspection unchecked
    this((EqualityPolicy<? super K>)EqualityPolicy.CANONICAL, removeEldestEntry);
  }

  @TestOnly
  public LinkedCustomHashMap() {
    //noinspection unchecked
    this((EqualityPolicy<? super K>)EqualityPolicy.CANONICAL, (map, eldest, key, value) -> {
      return false;
    });
  }

  @FunctionalInterface
  @ApiStatus.Internal
  public interface RemoveCallback<K, V> {
    boolean check(int size, Map.Entry<K, V> eldest, K key, V value);
  }

  LinkedCustomHashMap(@NotNull EqualityPolicy<? super K> hashingStrategy, @NotNull RemoveCallback<K, V> removeEldestEntry) {
    this.hashingStrategy = hashingStrategy;
    this.removeEldestEntry = removeEldestEntry;

    init();
  }

  private void init() {
    n = HashCommon.arraySize(Hash.DEFAULT_INITIAL_SIZE, LOAD_FACTOR);
    mask = n - 1;
    maxFill = HashCommon.maxFill(n, LOAD_FACTOR);
    //noinspection unchecked
    entries = new Entry[n + 1];
    size = 0;
  }

  public int size() {
    return size;
  }

  public boolean isEmpty() {
    return size == 0;
  }

  public void clear() {
    if (size == 0) {
      return;
    }

    top = back = null;
    init();
  }

  public V get(K key) {
    Entry<K, V>[] entries = this.entries;
    int hash = hashKey(key);
    int index = hash & mask;
    for (Entry<K, V> e = entries[index]; e != null; e = e.hashNext) {
      K entryKey;
      if (e.keyHash == hash && ((entryKey = e.key) == key || hashingStrategy.isEqual(entryKey, key))) {
        moveToTop(e);
        return e.value;
      }
    }
    return null;
  }

  private int hashKey(K key) {
    return key == null ? 0 : HashCommon.mix(hashingStrategy.getHashCode(key));
  }

  public V put(K key, @NotNull V value) {
    Entry<K, V>[] table = this.entries;
    int hash = hashKey(key);
    int index = hash & mask;
    for (Entry<K, V> e = table[index]; e != null; e = e.hashNext) {
      K entryKey;
      if (e.keyHash == hash && ((entryKey = e.key) == key || hashingStrategy.isEqual(entryKey, key))) {
        moveToTop(e);
        return e.setValue(value);
      }
    }

    Entry<K, V> e = new Entry<>(key, value, hash);
    e.hashNext = table[index];
    table[index] = e;
    Entry<K, V> top = this.top;
    e.next = top;
    if (top != null) {
      top.previous = e;
    }
    else {
      back = e;
    }
    this.top = e;
    size++;
    if (removeEldestEntry.check(size, back, back.key, back.value)) {
      V value1 = remove(back.key);
      assert value1 != null : "LinkedHashMap.Entry was not removed. Possibly mutable key: " + back.key;
    }
    else if (size >= maxFill) {
      rehash(HashCommon.arraySize(size + 1, LOAD_FACTOR));
    }
    return null;
  }

  public boolean containsKey(K key) {
    return get(key) != null;
  }

  public V remove(K key) {
    Entry<K, V>[] entries = this.entries;
    int hash = hashKey(key);
    int index = hash & mask;
    Entry<K, V> e = entries[index];
    if (e == null) {
      return null;
    }

    K entryKey;
    if (e.keyHash == hash && ((entryKey = e.key) == key || hashingStrategy.isEqual(entryKey, key))) {
      entries[index] = e.hashNext;
    }
    else {
      for (; ; ) {
        Entry<K, V> last = e;
        e = e.hashNext;
        if (e == null) {
          return null;
        }
        if (e.keyHash == hash && ((entryKey = e.key) == key || hashingStrategy.isEqual(entryKey, key))) {
          last.hashNext = e.hashNext;
          break;
        }
      }
    }
    unlink(e);
    size--;
    return e.value;
  }

  public @NotNull Set<K> keySet() {
    return new KeySet();
  }

  public @NotNull Collection<V> values() {
    return new Values();
  }

  public @NotNull Set<Map.Entry<K, V>> entrySet() {
    return new EntrySet();
  }

  private void moveToTop(Entry<K, V> e) {
    Entry<K, V> top = this.top;
    if (top == e) {
      return;
    }

    Entry<K, V> prev = e.previous;
    Entry<K, V> next = e.next;
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

  private void unlink(Entry<K, V> e) {
    Entry<K, V> prev = e.previous;
    Entry<K, V> next = e.next;
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

  private void rehash(int newN) {
    mask = newN - 1;
    n = newN;
    maxFill = HashCommon.maxFill(n, LOAD_FACTOR);

    //noinspection unchecked
    Entry<K, V>[] entries = new Entry[newN];
    for (Entry<K, V> e = back; e != null; e = e.previous) {
      int hash = e.keyHash & mask;
      e.hashNext = entries[hash];
      entries[hash] = e;
    }

    this.entries = entries;
  }

  private static final class Entry<K, V> implements Map.Entry<K, V> {
    private final K key;
    private final int keyHash;
    private V value;
    private Entry<K, V> next;
    private Entry<K, V> previous;
    private Entry<K, V> hashNext;

    Entry(final K key, final V value, int hash) {
      this.key = key;
      keyHash = hash;
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

  private abstract class LinkedHashIterator<T> implements Iterator<T> {
    private Entry<K, V> e = back;
    private Entry<K, V> last;

    @Override
    public boolean hasNext() {
      return e != null;
    }

    @Override
    public void remove() {
      if (last == null) {
        throw new IllegalStateException();
      }
      LinkedCustomHashMap.this.remove(last.key);
      last = null;
    }

    protected Entry<K, V> nextEntry() {
      final Entry<K, V> result = last = e;
      e = result.previous;
      return result;
    }
  }

  private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
    @Override
    public @NotNull Iterator<Map.Entry<K, V>> iterator() {
      return new LinkedHashIterator<Map.Entry<K, V>>() {
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

      @SuppressWarnings("unchecked")
      Map.Entry<K, V> e = (Map.Entry<K, V>)o;
      V value = get(e.getKey());
      return value != null && value.equals(e.getValue());
    }

    @Override
    public boolean remove(Object o) {
      if (!(o instanceof Map.Entry)) {
        return false;
      }

      @SuppressWarnings("unchecked")
      Map.Entry<K, V> e = (Map.Entry<K, V>)o;
      return LinkedCustomHashMap.this.remove(e.getKey()) != null;
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public void clear() {
      LinkedCustomHashMap.this.clear();
    }
  }

  private final class KeySet extends AbstractSet<K> {
    @Override
    public @NotNull Iterator<K> iterator() {
      return new LinkedHashIterator<K>() {
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
      //noinspection unchecked
      return containsKey((K)o);
    }

    @Override
    public boolean remove(Object o) {
      //noinspection unchecked
      return LinkedCustomHashMap.this.remove((K)o) != null;
    }

    @Override
    public void clear() {
      LinkedCustomHashMap.this.clear();
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
      return size;
    }

    @Override
    public boolean contains(Object o) {
      if (o == null) {
        return false;
      }

      for (Entry<K, V> entry : entries) {
        if (entry != null && entry.value.equals(o)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void clear() {
      LinkedCustomHashMap.this.clear();
    }
  }
}
