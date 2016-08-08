/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

public class VBStyleCollection<E, K> extends ArrayList<E> {

  private HashMap<K, Integer> map = new HashMap<>();

  private ArrayList<K> lstKeys = new ArrayList<>();

  public VBStyleCollection() {
    super();
  }

  public VBStyleCollection(int initialCapacity) {
    super(initialCapacity);
    lstKeys = new ArrayList<>(initialCapacity);
    map = new HashMap<>(initialCapacity);
  }

  public VBStyleCollection(Collection<E> c) {
    super(c);
  }

  public boolean add(E element) {
    lstKeys.add(null);
    super.add(element);
    return true;
  }

  public boolean remove(Object element) {   // TODO: error on void remove(E element)
    throw new RuntimeException("not implemented!");
  }

  public boolean addAll(Collection<? extends E> c) {
    for (int i = c.size() - 1; i >= 0; i--) {
      lstKeys.add(null);
    }
    return super.addAll(c);
  }

  public void addAllWithKey(VBStyleCollection<E, K> c) {
    for (int i = 0; i < c.size(); i++) {
      addWithKey(c.get(i), c.getKey(i));
    }
  }

  public void addAllWithKey(Collection<E> elements, Collection<K> keys) {
    int index = super.size();

    for (K key : keys) {
      map.put(key, index++);
    }

    super.addAll(elements);
    lstKeys.addAll(keys);
  }

  public void addWithKey(E element, K key) {
    map.put(key, super.size());
    super.add(element);
    lstKeys.add(key);
  }

  // TODO: speed up the method
  public E putWithKey(E element, K key) {
    Integer index = map.get(key);
    if (index == null) {
      addWithKey(element, key);
    }
    else {
      return super.set(index, element);
    }
    return null;
  }

  public void add(int index, E element) {
    addToListIndex(index, 1);
    lstKeys.add(index, null);
    super.add(index, element);
  }

  public void addWithKeyAndIndex(int index, E element, K key) {
    addToListIndex(index, 1);
    map.put(key, new Integer(index));
    super.add(index, element);
    lstKeys.add(index, key);
  }

  public void removeWithKey(K key) {
    int index = map.get(key).intValue();
    addToListIndex(index + 1, -1);
    super.remove(index);
    lstKeys.remove(index);
    map.remove(key);
  }

  public E remove(int index) {
    addToListIndex(index + 1, -1);
    K obj = lstKeys.get(index);
    if (obj != null) {
      map.remove(obj);
    }
    lstKeys.remove(index);
    return super.remove(index);
  }

  public E getWithKey(K key) {
    Integer index = map.get(key);
    if (index == null) {
      return null;
    }
    return super.get(index.intValue());
  }

  public int getIndexByKey(K key) {
    return map.get(key).intValue();
  }

  public E getLast() {
    return super.get(super.size() - 1);
  }

  public boolean containsKey(K key) {
    return map.containsKey(key);
  }

  public void clear() {
    map.clear();
    lstKeys.clear();
    super.clear();
  }

  public VBStyleCollection<E, K> clone() {
    VBStyleCollection<E, K> c = new VBStyleCollection<>();
    c.addAll(new ArrayList<>(this));
    c.setMap(new HashMap<>(map));
    c.setLstKeys(new ArrayList<>(lstKeys));
    return c;
  }

  public void swap(int index1, int index2) {

    Collections.swap(this, index1, index2);
    Collections.swap(lstKeys, index1, index2);

    K key = lstKeys.get(index1);
    if (key != null) {
      map.put(key, new Integer(index1));
    }

    key = lstKeys.get(index2);
    if (key != null) {
      map.put(key, new Integer(index2));
    }
  }

  public HashMap<K, Integer> getMap() {
    return map;
  }

  public void setMap(HashMap<K, Integer> map) {
    this.map = map;
  }

  public K getKey(int index) {
    return lstKeys.get(index);
  }

  public ArrayList<K> getLstKeys() {
    return lstKeys;
  }

  public void setLstKeys(ArrayList<K> lstKeys) {
    this.lstKeys = lstKeys;
  }

  private void addToListIndex(int index, int diff) {
    for (int i = lstKeys.size() - 1; i >= index; i--) {
      K obj = lstKeys.get(i);
      if (obj != null) {
        map.put(obj, new Integer(i + diff));
      }
    }
  }
}
