// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/**
 * Allow to reuse structurally equal objects to avoid memory being wasted on them. Note: objects are cached inside
 * and on hard references, so even the ones that are not used anymore will be still present in the memory.
 *
 * @see WeakInterner
 * @author peter
 */

public class Interner<T> {
  private final OpenTHashSet<T> mySet;

  public Interner() {
    mySet = new OpenTHashSet<T>();
  }

  public Interner(@NotNull Collection<? extends T> initialItems) {
    mySet = new OpenTHashSet<T>(initialItems);
  }

  public Interner(@NotNull TObjectHashingStrategy<T> strategy) {
    mySet = new OpenTHashSet<T>(strategy);
  }

  @NotNull
  public T intern(@NotNull T name) {
    T interned = mySet.get(name);
    if (interned != null) {
      return interned;
    }

    boolean added = mySet.add(name);
    assert added;

    return name;
  }

  public void clear() {
    mySet.clear();
  }

  @NotNull
  public Set<T> getValues() {
    return mySet;
  }

}
