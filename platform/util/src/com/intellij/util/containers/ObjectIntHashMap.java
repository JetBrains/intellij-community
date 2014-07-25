package com.intellij.util.containers;

import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * return -1 instead of 0 if no such mapping exists
 */
public class ObjectIntHashMap<K> extends TObjectIntHashMap<K> {
  public ObjectIntHashMap(int initialCapacity) {
    super(initialCapacity);
  }

  public ObjectIntHashMap(@NotNull TObjectHashingStrategy<K> strategy) {
    super(strategy);
  }

  public ObjectIntHashMap(int initialCapacity, @NotNull TObjectHashingStrategy<K> strategy) {
    super(initialCapacity, strategy);
  }

  public ObjectIntHashMap() {
    super();
  }

  @Override
  public final int get(K key) {
    int index = index(key);
    return index < 0 ? -1 : _values[index];
  }
}
