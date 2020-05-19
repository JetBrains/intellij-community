// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @see MultiMap#createConcurrentSet()
 * @author peter
 */
public class ConcurrentMultiMap<K, V> extends MultiMap<K, V> {
  public static final int DEFAULT_CAPACITY = 16;
  public static final float DEFAULT_LOAD_FACTOR = 0.75f;
  public static final int DEFAULT_CONCURRENCY_LEVEL = ConcurrentIntObjectHashMap.NCPU;

  public ConcurrentMultiMap() {
    super(new ConcurrentHashMap<>());
  }

  public ConcurrentMultiMap(int initialCapacity, float loadFactor) {
    super(new ConcurrentHashMap<>(initialCapacity, loadFactor));
  }

  @Override
  protected @NotNull Collection<V> createCollection() {
    return ContainerUtil.createLockFreeCopyOnWriteList();
  }
}
