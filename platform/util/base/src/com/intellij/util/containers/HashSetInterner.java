// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Allow reusing structurally equal objects to avoid memory being wasted on them.
 * Note: objects are cached inside and on hard references, so even the ones that are not used anymore will be still present in the memory.
 *
 * @see WeakInterner
 */
@ApiStatus.NonExtendable
@ApiStatus.Internal
public class HashSetInterner<T> extends Interner<T> {
  private final ObjectOpenHashSet<T> set;

  public HashSetInterner() {
    //noinspection SSBasedInspection
    set = new ObjectOpenHashSet<>();
  }

  @Override
  public @NotNull T intern(@NotNull T name) {
    return set.addOrGet(name);
  }

  public T get(@NotNull T name) {
    return set.get(name);
  }

  @Override
  public void clear() {
    set.clear();
  }

  @Override
  public @NotNull Set<T> getValues() {
    return set;
  }
}
