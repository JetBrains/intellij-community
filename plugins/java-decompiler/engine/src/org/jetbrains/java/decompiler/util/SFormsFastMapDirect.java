// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jetbrains.java.decompiler.util.Universe.UniversedSet;

public class SFormsFastMapDirect {

  private final Map<Integer, UniversedSet<Integer>> elements;

  public SFormsFastMapDirect() {
    elements = new HashMap<>();
  }

  public SFormsFastMapDirect(SFormsFastMapDirect map) {
    elements = new HashMap<>(map.elements);
  }

  public SFormsFastMapDirect getCopy() {
    SFormsFastMapDirect copy = new SFormsFastMapDirect(this);
    copy.elements.replaceAll((k, v) -> v.getCopy());
    return copy;
  }

  public int size() {
    return elements.size();
  }

  public boolean isEmpty() {
    return elements.isEmpty();
  }

  public void put(int key, UniversedSet<Integer> value) {
    elements.put(key, value);
  }

  public void removeAllFields() {
    elements.keySet().removeIf(key -> key < 0);
  }

  public boolean containsKey(int key) {
    return elements.containsKey(key);
  }

  public UniversedSet<Integer> get(int key) {
    return elements.get(key);
  }

  public void complement(SFormsFastMapDirect map) {
    Set<Integer> toRemove = new HashSet<>();
    elements.forEach((k, v) -> {
      if (map.containsKey(k)) {
        v.complement(map.get(k));
        if (v.isEmpty()) {
          toRemove.add(k);
        }
      }
    });
    elements.keySet().removeAll(toRemove);
  }

  public void intersection(SFormsFastMapDirect map) {
    Set<Integer> toRemove = new HashSet<>();
    elements.forEach((k, v) -> {
      if (map.containsKey(k)) {
        v.intersection(map.get(k));
        if (v.isEmpty()) {
          toRemove.add(k);
        }
      } else {
        toRemove.add(k);
      }
    });
    elements.keySet().removeAll(toRemove);
  }

  public void union(SFormsFastMapDirect map) {
    Map<Integer, UniversedSet<Integer>> toAdd = new HashMap<>();
    map.elements.forEach((k, v) -> {
      if (elements.containsKey(k)) {
        elements.get(k).union(v);
      } else {
        toAdd.put(k, v.getCopy());
      }
    });
    elements.putAll(toAdd);
  }

  public String toString() {
    return elements.toString();
  }

  public List<Entry<Integer, UniversedSet<Integer>>> entryList() {
    return new ArrayList<>(elements.entrySet());
  }
}