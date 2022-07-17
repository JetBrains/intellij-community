// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.concurrency;

import com.intellij.util.containers.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Creates various concurrent collections (e.g., maps, sets) which can be customized with {@link HashingStrategy}
 */
public final class ConcurrentCollectionFactory {
  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentIdentityMap() {
    return new ConcurrentHashMap<>(HashingStrategy.identity());
  }

  @Contract(pure = true)
  public static @NotNull <T, V> ConcurrentMap<@NotNull T, @NotNull V> createConcurrentMap(@NotNull HashingStrategy<? super T> hashStrategy) {
    return new ConcurrentHashMap<>(hashStrategy);
  }

  @Contract(pure = true)
  public static @NotNull <T, V> ConcurrentMap<@NotNull T, @NotNull V> createConcurrentMap() {
    return new ConcurrentHashMap<>(HashingStrategy.canonical());
  }

  @Contract(pure = true)
  public static @NotNull <T, V> ConcurrentMap<@NotNull T, @NotNull V> createConcurrentMap(int initialCapacity,
                                                                                          float loadFactor,
                                                                                          int concurrencyLevel,
                                                                                          @NotNull HashingStrategy<? super T> hashStrategy) {
    return new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel, hashStrategy);
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<@NotNull T> createConcurrentSet(@NotNull HashingStrategy<? super T> hashStrategy) {
    return Collections.newSetFromMap(createConcurrentMap(hashStrategy));
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<@NotNull T> createConcurrentIdentitySet() {
    return Collections.newSetFromMap(createConcurrentMap(HashingStrategy.identity()));
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<@NotNull T> createConcurrentIdentitySet(int initialCapacity) {
    return Collections.newSetFromMap(createConcurrentMap(initialCapacity, 0.75f, 16, HashingStrategy.identity()));
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<@NotNull T> createConcurrentSet(int initialCapacity, @NotNull HashingStrategy<? super T> hashStrategy) {
    return Collections.newSetFromMap(createConcurrentMap(initialCapacity, 0.75f, 16, hashStrategy));
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<@NotNull T> createConcurrentSet(int initialCapacity,
                                                                 float loadFactor,
                                                                 int concurrencyLevel,
                                                                 @NotNull HashingStrategy<? super T> hashStrategy) {
    return Collections.newSetFromMap(createConcurrentMap(initialCapacity, loadFactor, concurrencyLevel, hashStrategy));
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <V> ConcurrentLongObjectMap<@NotNull V> createConcurrentLongObjectMap() {
    return new ConcurrentLongObjectHashMap<>();
  }

  @Contract(value = "_ -> new", pure = true)
  public static @NotNull <V> ConcurrentLongObjectMap<@NotNull V> createConcurrentLongObjectMap(int initialCapacity) {
    return new ConcurrentLongObjectHashMap<>(initialCapacity);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <V> ConcurrentIntObjectMap<@NotNull V> createConcurrentIntObjectMap() {
    return new ConcurrentIntObjectHashMap<>();
  }

  @Contract(value = "_,_,_ -> new", pure = true)
  public static @NotNull <V> ConcurrentIntObjectMap<@NotNull V> createConcurrentIntObjectMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
    return new ConcurrentIntObjectHashMap<>(initialCapacity, loadFactor, concurrencyLevel);
  }
  @Contract(value = " -> new", pure = true)
  public static @NotNull <V> ConcurrentIntObjectMap<@NotNull V> createConcurrentIntObjectSoftValueMap() {
    return new ConcurrentIntKeySoftValueHashMap<>();
  }
  @Contract(value = " -> new", pure = true)
  public static @NotNull <V> ConcurrentIntObjectMap<@NotNull V> createConcurrentIntObjectWeakValueMap() {
    return new ConcurrentIntKeyWeakValueHashMap<>();
  }
}