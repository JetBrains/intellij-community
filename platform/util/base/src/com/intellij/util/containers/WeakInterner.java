// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Allow to reuse structurally equal objects to avoid memory being wasted on them. Objects are cached on weak references
 * and garbage-collected when not needed anymore.
 *
 * @author peter
 */
public class WeakInterner<T> extends Interner<T> {
  private final ConcurrentMap<T, T> myMap;

  /**
   * @deprecated Use {@link Interner#createWeakInterner()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public WeakInterner() {
    myMap = CollectionFactory.createConcurrentWeakKeyWeakValueMap();
  }

  public WeakInterner(@NotNull HashingStrategy<? super T> strategy) {
    myMap = CollectionFactory.createConcurrentWeakKeyWeakValueMap(strategy);
  }

  @Override
  @NotNull
  public T intern(@NotNull T name) {
    T old = myMap.putIfAbsent(name, name);
    return old == null ? name : old;
  }

  @Override
  public void clear() {
    myMap.clear();
  }

  @Override
  @NotNull
  public Set<T> getValues() {
    return new HashSet<>(myMap.values());
  }
}
