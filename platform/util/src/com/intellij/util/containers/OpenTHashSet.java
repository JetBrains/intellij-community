// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Deprecated
public class OpenTHashSet<T> extends THashSet<T> {
  public OpenTHashSet() {
  }

  public OpenTHashSet(final int initialCapacity, final float loadFactor, @NotNull TObjectHashingStrategy<T> strategy) {
    super(initialCapacity, loadFactor, strategy);
  }

  @Override
  public int index(final T obj) {
    return super.index(obj);
  }

  public T get(final int index) {
    //noinspection unchecked
    return (T)_set[index];
  }

  /**
   * Returns an element of this set equal to the given one. Can be used for interning objects to save memory.
   */
  @Nullable
  public T get(final T obj) {
    final int index = index(obj);
    return index < 0 ? null : get(index);
  }

  /**
   * Returns an element of this set equal to the given one, or adds the given one to the set and returns it.
   * Can be used for interning objects to save memory.
   */
  @Contract("!null -> !null")
  public T getOrAdd(final T obj) {
    final int index = insertionIndex(obj);

    boolean alreadyStored = index < 0;
    if (alreadyStored) return get(-index - 1);

    Object old = _set[index];
    _set[index] = obj;

    postInsertHook(old == null);
    return obj;
  }
}
