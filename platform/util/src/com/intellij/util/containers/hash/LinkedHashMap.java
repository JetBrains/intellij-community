// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.hash;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Deprecated
public class LinkedHashMap<K, V> extends AbstractMap<K, V> implements Map<K, V> {
  private Entry<K, V>[] table;
  private Entry<K, V> top;
  private Entry<K, V> back;
  private int capacity;
  private int size;
  private final float loadFactor;
  private final EqualityPolicy<? super K> hashingStrategy;
  private final boolean accessOrder;

  public LinkedHashMap() {
    //noinspection rawtypes,unchecked
    this(0, HashUtil.DEFAULT_LOAD_FACTOR, (EqualityPolicy)EqualityPolicy.CANONICAL, false);
  }

  public LinkedHashMap(int capacity, boolean accessOrder) {
    //noinspection rawtypes,unchecked
    this(capacity, HashUtil.DEFAULT_LOAD_FACTOR, (EqualityPolicy)EqualityPolicy.CANONICAL, accessOrder);
  }

  public LinkedHashMap(@NotNull EqualityPolicy<? super K> hashingStrategy) {
    this(0, HashUtil.DEFAULT_LOAD_FACTOR, hashingStrategy, false);
  }

  public LinkedHashMap(int capacity, float loadFactor, @NotNull EqualityPolicy<? super K> hashingStrategy, boolean accessOrder) {
    this.loadFactor = loadFactor;
    this.hashingStrategy = hashingStrategy;
    clear(capacity);
    this.accessOrder = accessOrder;
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
  public V get(Object key) {
    Entry<K, V>[] table = this.table;
    int hash = HashUtil.hash((K)key, hashingStrategy);
    int index = hash % table.length;

    for (Entry<K, V> e = table[index]; e != null; e = e.hashNext) {
      final K entryKey;
      if (e.keyHash == hash && ((entryKey = e.key) == key || hashingStrategy.isEqual(entryKey, (K)key))) {
        moveToTop(e);
        return e.value;

      }
    }
    return null;
  }

  @Override
  public V put(final K key, final @NotNull V value) {
    final Entry<K, V>[] table = this.table;
    final int hash = HashUtil.hash(key, hashingStrategy);
    final int index = hash % table.length;
    for (Entry<K, V> e = table[index]; e != null; e = e.hashNext) {
      final K entryKey;
      if (e.keyHash == hash && ((entryKey = e.key) == key || hashingStrategy.isEqual(entryKey, key))) {
        moveToTop(e);
        return e.setValue(value);
      }
    }
    final Entry<K, V> e = new Entry<K, V>(key, value, hash);
    e.hashNext = table[index];
    table[index] = e;
    final Entry<K, V> top = this.top;
    e.next = top;
    if (top != null) {
      top.previous = e;
    }
    else {
      back = e;
    }
    this.top = e;
    size++;
    if (removeEldestEntry(back, back.key, back.value)) {
      doRemoveEldestEntry();
    }
    else if (size > capacity) {
      rehash((int)(capacity * HashUtil.CAPACITY_MULTIPLE));
    }
    return null;
  }

  public void doRemoveEldestEntry() {
    final V val = remove(back.key);
    assert val != null : "LinkedHashMap.Entry was not removed. Possibly mutable key: " + back.key;
  }

  @Override
  public boolean containsKey(final Object key) {
    return get(key) != null;
  }

  @Override
  public V remove(final Object key) {
    final Entry<K, V>[] table = this.table;
    final int hash = HashUtil.hash((K)key, hashingStrategy);
    final int index = hash % table.length;
    Entry<K, V> e = table[index];
    if (e == null) {
      return null;
    }
    K entryKey;
    if (e.keyHash == hash && ((entryKey = e.key) == key || hashingStrategy.isEqual(entryKey, (K)key))) {
      table[index] = e.hashNext;
    }
    else {
      for (; ;) {
        final Entry<K, V> last = e;
        e = e.hashNext;
        if (e == null) {
          return null;
        }
        if (e.keyHash == hash && ((entryKey = e.key) == key || hashingStrategy.isEqual(entryKey, (K)key))) {
          last.hashNext = e.hashNext;
          break;
        }
      }
    }
    unlink(e);
    size--;
    return e.value;
  }

  @Override
  public @NotNull Set<K> keySet() {
    return new KeySet();
  }

  @Override
  public @NotNull Collection<V> values() {
    return new Values();
  }

  @Override
  public @NotNull Set<Map.Entry<K, V>> entrySet() {
    return new EntrySet();
  }

  protected boolean removeEldestEntry(Map.Entry<K, V> eldest, K key, V value) {
    return removeEldestEntry(eldest);
  }

  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return false;
  }

  private void init(int capacity) {
    table = new Entry[HashUtil.adjustTableSize((int)(capacity / loadFactor))];
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

  public @Nullable K getLastKey() {
    return top != null ? top.key : null;
  }

  public @Nullable V getLastValue() {
    return top != null ? top.value : null;
  }

  public @Nullable K getFirstKey() {
    return back != null ? back.key :  null;
  }

  private void moveToTop(final Entry<K, V> e) {
    if (!accessOrder) {
      return;
    }

    final Entry<K, V> top = this.top;
    if (top != e) {
      final Entry<K, V> prev = e.previous;
      final Entry<K, V> next = e.next;
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

  private void unlink(final Entry<K, V> e) {
    final Entry<K, V> prev = e.previous;
    final Entry<K, V> next = e.next;
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
    table = new Entry[HashUtil.adjustTableSize((int)(capacity / loadFactor))];
    this.capacity = capacity;
    final Entry<K, V>[] table = this.table;
    final int tableLen = table.length;
    for (Entry<K, V> e = back; e != null; e = e.previous) {
      final int hash = e.keyHash % tableLen;
      e.hashNext = table[hash];
      table[hash] = e;
    }
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

    private LinkedHashMap.Entry<K, V> e = back;
    private LinkedHashMap.Entry<K, V> last;

    @Override
    public boolean hasNext() {
      return e != null;
    }

    @Override
    public void remove() {
      if (last == null) {
        throw new IllegalStateException();
      }
      LinkedHashMap.this.remove(last.key);
      last = null;
    }

    protected LinkedHashMap.Entry<K, V> nextEntry() {
      final LinkedHashMap.Entry<K, V> result = last = e;
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
      return LinkedHashMap.this.remove(e.getKey()) != null;
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public void clear() {
      LinkedHashMap.this.clear();
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
      return containsKey(o);
    }

    @Override
    public boolean remove(Object o) {
      return LinkedHashMap.this.remove(o) != null;
    }

    @Override
    public void clear() {
      LinkedHashMap.this.clear();
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
      return containsValue(o);
    }

    @Override
    public void clear() {
      LinkedHashMap.this.clear();
    }
  }
}
