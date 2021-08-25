// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

/**
 * Specialized memory saving map implementation for UrlClassLoader to avoid extra dependencies.
 */
public final class IntObjectHashMap<T> {
  private final ArrayProducer<T[]> arrayFactory;
  private int size;
  private int[] keys;
  private T[] values;
  private T specialZeroValue;
  private boolean hasZeroValue;

  public interface ArrayProducer<T> {
    T produce(int value);
  }
  public IntObjectHashMap(ArrayProducer<T[]> arrayFactory) {
    this.arrayFactory = arrayFactory;
    keys = new int[16];
    values = arrayFactory.produce(keys.length);
  }

  public IntObjectHashMap(IntObjectHashMap<T> original) {
    arrayFactory = original.arrayFactory;
    keys = original.keys.clone();
    values = original.values.clone();
    size = original.size;
    specialZeroValue = original.specialZeroValue;
    hasZeroValue = original.hasZeroValue;
  }

  public int size() {
    return size + (hasZeroValue ? 1 : 0);
  }

  public void replaceByIndex(int index, int key, @NotNull T value) {
    if (key == 0) {
      specialZeroValue = value;
      hasZeroValue = true;
    }
    else {
      values[index] = value;
    }
  }

  public void addByIndex(int index, int key, @NotNull T value) {
    if (key == 0) {
      specialZeroValue = value;
      hasZeroValue = true;
      return;
    }

    if (size >= (2 * values.length) / 3) {
      rehash();
      index = hashIndex(keys, key);
    }

    size++;
    values[index] = value;
    keys[index] = key;
  }

  public void put(int key, @NotNull T value) {
    if (key == 0) {
      specialZeroValue = value;
      hasZeroValue = true;
      return;
    }

    if (size >= (2 * values.length) / 3) {
      rehash();
    }
    int index = hashIndex(keys, key);
    if (values[index] == null) {
      size++;
    }
    values[index] = value;
    if (keys[index] == 0) {
      keys[index] = key;
    }
  }

  public int index(int key) {
    return key == 0 ? Integer.MIN_VALUE : hashIndex(keys, key);
  }

  private static int hashIndex(int [] keys, int key) {
    int hash = (int)((key * 0x9E3779B9L) & 0x7fffffff);
    int index = hash & (keys.length - 1);

    int candidate;
    while ((candidate = keys[index]) != 0) {
      if (candidate == key) {
        return index;
      }
      if (index == 0) {
        index = keys.length;
      }
      index--;
    }

    return index;
  }

  private void rehash() {
    int[] newKeys = new int[keys.length << 1];
    T[] newValues = arrayFactory.produce(newKeys.length);

    for (int i = keys.length - 1; i >= 0; i--) {
      int key = keys[i];
      if (key != 0) {
        int index = hashIndex(newKeys, key);
        newValues[index] = values[i];
        if (newKeys[index] == 0) {
          newKeys[index] = key;
        }
      }
    }

    keys = newKeys;
    values = newValues;
  }

  public T get(int key) {
    return key == 0 ? specialZeroValue : values[hashIndex(keys, key)];
  }

  public T getByIndex(int index, int key) {
    return key == 0 ? specialZeroValue : values[index];
  }
}