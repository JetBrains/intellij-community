// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Allow reusing structurally equal objects to avoid memory being wasted on them. Objects are cached on weak references
 * and garbage-collected when not needed anymore.
 * <p>
 * Use {@link Interner#createWeakInterner()}.
 */
@ApiStatus.Internal
public class WeakInterner<T> extends Interner<T> {
  private final ConcurrentMap<T, T> map;

  protected WeakInterner() {
    map = CollectionFactory.createConcurrentWeakKeyWeakValueMap();
  }

  public WeakInterner(@NotNull HashingStrategy<? super T> strategy) {
    map = CollectionFactory.createConcurrentWeakKeyWeakValueMap(strategy);
  }

  @Override
  public @NotNull T intern(@NotNull T name) {
    T old = map.putIfAbsent(name, name);
    return old == null ? name : old;
  }

  @Override
  public void clear() {
    map.clear();
  }

  @Override
  public @NotNull @Unmodifiable Set<T> getValues() {
    return Collections.unmodifiableSet(new HashSet<>(map.values()));
  }
}
