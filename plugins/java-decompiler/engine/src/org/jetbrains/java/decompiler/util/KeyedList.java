// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.util;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A list that can be queried both by index and by a key associated with each element.
 *
 * @param <K> The key type
 * @param <E> The element type
 */
public class KeyedList<K, E> extends AbstractList<E> {

  private class Entry {
    public Entry(K key, E value) {
      this.key = key;
      this.value = value;
    }
    public final K key;
    public E value;
  }

  private final List<Entry> entries;
  private final Map<K, Integer> indices;

  public KeyedList() {
    entries = new ArrayList<>();
    indices = new HashMap<>();
  }

  public KeyedList(int initialCapacity) {
    entries = new ArrayList<>(initialCapacity);
    indices = new HashMap<>(initialCapacity);
  }

  private KeyedList(KeyedList<K, E> other) {
    entries = new ArrayList<>(other.entries);
    indices = new HashMap<>(other.indices);
  }

  public void addAllWithKey(Collection<E> elements, Collection<K> keys) {
    assert elements.size() == keys.size();

    Iterator<K> keyIterator = keys.iterator();
    Iterator<E> valueIterator = elements.iterator();

    int index = entries.size();
    while (keyIterator.hasNext() && valueIterator.hasNext()) {
      K key = keyIterator.next();
      E value = valueIterator.next();
      modCount++;
      entries.add(new Entry(key, value));
      indices.put(key, index++);
    }

    assert !keyIterator.hasNext() : "Should have used all keys!";
    assert !valueIterator.hasNext() : "Should have used all values!";
  }

  public void addWithKey(E element, K key) {
    modCount++;
    indices.put(key, entries.size());
    entries.add(new Entry(key, element));
  }

  @Override
  public E set(int index, E element) {
    Entry entry = entries.get(index);
    E oldValue = entry.value;
    entry.value = element;
    return oldValue;
  }

  public E putWithKey(E element, K key) {
    Integer index = indices.get(key);
    if (index == null) {
      addWithKey(element, key);
      return null;
    }
    else {
      return set(index, element);
    }
  }

  @Override
  public void add(int index, E element) {
    modCount++;
    offsetIndices(index, 1);
    entries.add(index, new Entry(null, element));
  }

  public void addWithKeyAndIndex(int index, E element, K key) {
    modCount++;
    offsetIndices(index, 1);
    entries.add(index, new Entry(key, element));
    indices.put(key, index);
  }

  public void removeWithKey(K key) {
    modCount++;
    int index = getIndexByKey(key);
    if (index == -1) {
      throw new IllegalArgumentException(key + " is not a key to the list");
    }
    entries.remove(index);
    indices.remove(key);
    offsetIndices(index + 1, -1);
  }

  @Override
  public E remove(int index) {
    modCount++;
    Entry e = entries.remove(index);
    indices.remove(e.key);
    offsetIndices(index + 1, -1);
    return e.value;
  }

  public E getWithKey(K key) {
    int index = getIndexByKey(key);
    if (index == -1) {
      return null;
    }
    return get(index);
  }

  @Override
  public E get(int index) {
    return entries.get(index).value;
  }

  public int getIndexByKey(K key) {
    return containsKey(key) ? indices.get(key) : -1;
  }

  public E getLast() {
    return entries.get(entries.size() - 1).value;
  }

  public boolean containsKey(K key) {
    return indices.containsKey(key);
  }

  @Override
  public int size() {
    return entries.size();
  }

  @Override
  public void clear() {
    entries.clear();
    indices.clear();
  }

  @Override
  public KeyedList<K, E> clone() {
    return new KeyedList<>(this);
  }

  public K getKey(int index) {
    return entries.get(index).key;
  }

  public List<K> getLstKeys() {
    return entries.stream().map(e -> e.key).collect(Collectors.toList());
  }

  /**
   * Offsets all indices that are >= start by diff (which may be negative).
   */
  private void offsetIndices(int start, int diff) {
    indices.entrySet().stream()
        .filter(e -> e.getValue() >= start)
        .forEach(e -> e.setValue(e.getValue() + diff));
  }
}