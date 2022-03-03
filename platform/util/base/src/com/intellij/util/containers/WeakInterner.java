// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Allow reusing structurally equal objects to avoid memory being wasted on them. Objects are cached on weak references
 * and garbage-collected when not needed anymore.
 *
 * Use {@link Interner#createWeakInterner()}.
 */
public class WeakInterner<T> extends Interner<T> {
  private final ConcurrentMap<T, T> map;

  protected WeakInterner() {
    map = CollectionFactory.createConcurrentWeakKeyWeakValueMap();
  }

  public WeakInterner(@NotNull HashingStrategy<? super T> strategy) {
    map = CollectionFactory.createConcurrentWeakKeyWeakValueMap(strategy);
  }

  @Override
  @NotNull
  public T intern(@NotNull T name) {
    T old = map.putIfAbsent(name, name);
    return old == null ? name : old;
  }

  @Override
  public void clear() {
    map.clear();
  }

  @Override
  @NotNull
  public Set<T> getValues() {
    return new HashSet<>(map.values());
  }
}
