// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.ConcurrencyUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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
    myMap = ContainerUtil.createConcurrentWeakKeyWeakValueMap();
  }

  public WeakInterner(@NotNull TObjectHashingStrategy<? super T> strategy) {
    myMap = new ConcurrentWeakKeyWeakValueHashMap<>(100, 0.75f, Runtime.getRuntime().availableProcessors(),
                                                    ContainerUtil.createHashingStrategy(strategy));
  }

  @Override
  @NotNull
  public T intern(@NotNull T name) {
    return ConcurrencyUtil.cacheOrGet(myMap, name, name);
  }

  @Override
  public void clear() {
    myMap.clear();
  }

  @Override
  @NotNull
  public Set<T> getValues() {
    return new THashSet<>(myMap.values());
  }
}
