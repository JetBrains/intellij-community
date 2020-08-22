// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
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
public class HashSetInterner<T> extends Interner<T> {
  private final ObjectOpenHashSet<T> mySet;

  public HashSetInterner() {
    mySet = new ObjectOpenHashSet<>();
  }

  public HashSetInterner(@NotNull Collection<? extends T> initialItems) {
    mySet = new ObjectOpenHashSet<>(initialItems);
  }

  @Override
  public @NotNull T intern(@NotNull T name) {
    return mySet.addOrGet(name);
  }

  public T get(@NotNull T name) {
    return mySet.get(name);
  }

  @Override
  public void clear() {
    mySet.clear();
  }

  @Override
  public @NotNull Set<T> getValues() {
    return mySet;
  }
}
