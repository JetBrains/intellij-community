// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.util.SystemInfoRt;
import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

// ContainerUtil requires trove in classpath
public final class CollectionFactory {
  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentWeakMap() {
    return new ConcurrentWeakHashMap<>(0.75f);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentWeakValueMap() {
    return new ConcurrentWeakValueHashMap<>();
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentSoftValueMap() {
    return new ConcurrentSoftValueHashMap<>();
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentWeakIdentityMap() {
    return new ConcurrentWeakHashMap<>(HashingStrategy.identity());
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> Map<K, V> createWeakMap() {
    //noinspection deprecation
    return new WeakHashMap<>(4, 0.8f, HashingStrategy.canonical());
  }

  @Contract(value = "_,_,_ -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentWeakKeySoftValueIdentityMap(int initialCapacity,
                                                                                                float loadFactor,
                                                                                                int concurrencyLevel) {
    //noinspection deprecation
    return new ConcurrentWeakKeySoftValueHashMap<>(initialCapacity, loadFactor, concurrencyLevel, HashingStrategy.identity());
  }

  public static @NotNull <K, V> Map<K, V> createWeakIdentityMap(int initialCapacity, float loadFactor) {
    //noinspection deprecation
    return new WeakHashMap<>(initialCapacity, loadFactor, HashingStrategy.identity());
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentWeakKeyWeakValueMap() {
    return new ConcurrentWeakKeyWeakValueHashMap<>(100, 0.75f, Runtime.getRuntime().availableProcessors(), HashingStrategy.canonical());
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentWeakKeyWeakValueIdentityMap() {
    return new ConcurrentWeakKeyWeakValueHashMap<>(100, 0.75f, Runtime.getRuntime().availableProcessors(), HashingStrategy.identity());
  }

  @Contract(value = "_,_,_ -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentWeakMap(int initialCapacity,
                                                                            float loadFactor,
                                                                            int concurrencyLevel) {
    return new ConcurrentWeakHashMap<>(initialCapacity, loadFactor, concurrencyLevel, HashingStrategy.canonical());
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentWeakKeySoftValueMap() {
    return createConcurrentWeakKeySoftValueMap(100, 0.75f, Runtime.getRuntime().availableProcessors());
  }

  @Contract(value = "_,_,_,-> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentWeakKeySoftValueMap(int initialCapacity,
                                                                                        float loadFactor,
                                                                                        int concurrencyLevel) {
    //noinspection deprecation
    return new ConcurrentWeakKeySoftValueHashMap<>(initialCapacity, loadFactor, concurrencyLevel, HashingStrategy.canonical());
  }

  public static @NotNull <T> Map<CharSequence, T> createCharSequenceMap(boolean caseSensitive, int expectedSize, float loadFactor) {
    return new Object2ObjectOpenCustomHashMap<>(expectedSize, loadFactor, FastUtilHashingStrategies.getCharSequenceStrategy(caseSensitive));
  }

  public static @NotNull Set<CharSequence> createCharSequenceSet(boolean caseSensitive, int expectedSize, float loadFactor) {
    return new ObjectOpenCustomHashSet<>(expectedSize, loadFactor, FastUtilHashingStrategies.getCharSequenceStrategy(caseSensitive));
  }

  public static @NotNull Set<CharSequence> createCharSequenceSet(List<? extends CharSequence> items) {
    return new ObjectOpenCustomHashSet<>(items, FastUtilHashingStrategies.getCharSequenceStrategy(true));
  }

  public static @NotNull Set<CharSequence> createCharSequenceSet(boolean caseSensitive, int expectedSize) {
    return new ObjectOpenCustomHashSet<>(expectedSize, FastUtilHashingStrategies.getCharSequenceStrategy(caseSensitive));
  }

  public static @NotNull Set<CharSequence> createCharSequenceSet(boolean caseSensitive) {
    return new ObjectOpenCustomHashSet<>(FastUtilHashingStrategies.getCharSequenceStrategy(caseSensitive));
  }

  public static @NotNull <T> Map<CharSequence, T> createCharSequenceMap(boolean caseSensitive) {
    return new Object2ObjectOpenCustomHashMap<>(FastUtilHashingStrategies.getCharSequenceStrategy(caseSensitive));
  }

  public static @NotNull <T> Map<CharSequence, T> createCharSequenceMap(int capacity, float loadFactory, boolean caseSensitive) {
    return new Object2ObjectOpenCustomHashMap<>(capacity, loadFactory, FastUtilHashingStrategies.getCharSequenceStrategy(caseSensitive));
  }

  public static @NotNull Set<String> createCaseInsensitiveStringSet() {
    return new ObjectOpenCustomHashSet<>(FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  public static @NotNull Set<String> createCaseInsensitiveStringSet(@NotNull Collection<String> items) {
    return new ObjectOpenCustomHashSet<>(items, FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  public static <V> @NotNull Map<String, V> createCaseInsensitiveStringMap() {
    return new Object2ObjectOpenCustomHashMap<>(FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  public static <V> @NotNull Map<String, V> createCaseInsensitiveStringMap(@NotNull Map<String, V> source) {
    return new Object2ObjectOpenCustomHashMap<>(source, FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  @Contract(value = "_,_,_ -> new", pure = true)
  @SuppressWarnings("SameParameterValue")
  static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentSoftKeySoftValueMap(int initialCapacity,
                                                                                 float loadFactor,
                                                                                 int concurrencyLevel) {
    return new ConcurrentSoftKeySoftValueHashMap<>(initialCapacity, loadFactor, concurrencyLevel, HashingStrategy.canonical());
  }

  @Contract(value = "_,_,_ -> new", pure = true)
  @SuppressWarnings("SameParameterValue")
  static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentSoftKeySoftValueIdentityMap(int initialCapacity,
                                                                                         float loadFactor,
                                                                                         int concurrencyLevel) {
    return new ConcurrentSoftKeySoftValueHashMap<>(initialCapacity, loadFactor, concurrencyLevel, HashingStrategy.identity());
  }

  public static @NotNull Set<String> createFilePathSet() {
    return SystemInfoRt.isFileSystemCaseSensitive ? new HashSet<>() : createCaseInsensitiveStringSet();
  }

  public static @NotNull Set<String> createFilePathSet(int expectedSize) {
    return createFilePathSet(expectedSize, SystemInfoRt.isFileSystemCaseSensitive);
  }

  public static @NotNull Set<String> createFilePathSet(int expectedSize, boolean isFileSystemCaseSensitive) {
    return isFileSystemCaseSensitive
           ? new HashSet<>(expectedSize)
           : new ObjectOpenCustomHashSet<>(expectedSize, FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  public static @NotNull Set<String> createFilePathSet(@NotNull Collection<String> paths, boolean isFileSystemCaseSensitive) {
    return isFileSystemCaseSensitive
           ? new HashSet<>(paths)
           : new ObjectOpenCustomHashSet<>(paths, FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  public static @NotNull Set<String> createFilePathSet(String @NotNull[] paths, boolean isFileSystemCaseSensitive) {
    //noinspection SSBasedInspection
    return isFileSystemCaseSensitive
           ? new HashSet<>(Arrays.asList(paths))
           : new ObjectOpenCustomHashSet<>(paths, FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  public static @NotNull Set<String> createFilePathSet(@NotNull Collection<String> paths) {
    return createFilePathSet(paths, SystemInfoRt.isFileSystemCaseSensitive);
  }

  public static @NotNull <V> Map<String, V> createFilePathMap() {
    return SystemInfoRt.isFileSystemCaseSensitive ? new HashMap<>() : createCaseInsensitiveStringMap();
  }

  public static @NotNull <V> Map<String, V> createFilePathMap(int expectedSize) {
    return createFilePathMap(expectedSize, SystemInfoRt.isFileSystemCaseSensitive);
  }

  public static @NotNull <V> Map<String, V> createFilePathMap(int expectedSize, boolean isFileSystemCaseSensitive) {
    return isFileSystemCaseSensitive
           ? new HashMap<>(expectedSize)
           : new Object2ObjectOpenCustomHashMap<>(expectedSize, FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  public static @NotNull Set<String> createFilePathLinkedSet() {
    return SystemInfoRt.isFileSystemCaseSensitive
           ? createSmallMemoryFootprintLinkedSet()
           : new ObjectLinkedOpenCustomHashSet<>(FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  /**
   * Create linked map with key hash strategy according to file system path case sensitivity.
   */
  public static @NotNull <V> Map<String, V> createFilePathLinkedMap() {
    return SystemInfoRt.isFileSystemCaseSensitive
           ? createSmallMemoryFootprintLinkedMap()
           : new Object2ObjectLinkedOpenCustomHashMap<>(FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  /**
   * Returns a {@link Map} implementation with slightly faster access for very big maps (>100K keys) and a bit smaller memory footprint
   * than {@link LinkedHashMap}, with predictable iteration order. Null keys and values are permitted.
   * Use sparingly only when performance considerations are utterly important; in all other cases please prefer {@link LinkedHashMap}.
   */
  @Contract(value = "-> new", pure = true)
  public static <K, V> @NotNull Map<K, V> createSmallMemoryFootprintLinkedMap() {
    //noinspection SSBasedInspection
    return new Object2ObjectLinkedOpenHashMap<>();
  }

  /**
   * Return a {@link Map} implementation with slightly faster access for very big maps (>100K keys) and a bit smaller memory footprint
   * than {@link HashMap}. Null keys and values are permitted. Use sparingly only when performance considerations are utterly important;
   * in all other cases please prefer {@link HashMap}.
   */
  @Contract(value = "-> new", pure = true)
  public static <K, V> @NotNull Map<K, V> createSmallMemoryFootprintMap() {
    //noinspection SSBasedInspection
    return new Object2ObjectOpenHashMap<>();
  }

  /** See {@link #createSmallMemoryFootprintMap()}. */
  @Contract(value = "_ -> new", pure = true)
  public static <K, V> @NotNull Map<K, V> createSmallMemoryFootprintMap(int expected) {
    //noinspection SSBasedInspection
    return new Object2ObjectOpenHashMap<>(expected);
  }

  /** See {@link #createSmallMemoryFootprintMap()}. */
  @Contract(value = "_ -> new", pure = true)
  public static <K, V> @NotNull Map<K, V> createSmallMemoryFootprintMap(@NotNull Map<? extends K, ? extends V> map) {
    //noinspection SSBasedInspection
    return new Object2ObjectOpenHashMap<>(map);
  }

  /** See {@link #createSmallMemoryFootprintMap()}. */
  @Contract(value = "_,_ -> new", pure = true)
  public static <K, V> @NotNull Map<K, V> createSmallMemoryFootprintMap(int expected, float loadFactor) {
    //noinspection SSBasedInspection
    return new Object2ObjectOpenHashMap<>(expected, loadFactor);
  }

  /**
   * Returns a linked-keys (i.e. iteration order is the same as the insertion order) {@link Set} implementation with slightly faster access for very big collection (>100K keys) and a bit smaller memory footprint
   * than {@link HashSet}. Null keys are permitted. Use sparingly only when performance considerations are utterly important;
   * in all other cases please prefer {@link HashSet}.
   */
  @Contract(value = "-> new", pure = true)
  public static <K> @NotNull Set<K> createSmallMemoryFootprintLinkedSet() {
    //noinspection SSBasedInspection
    return new ObjectLinkedOpenHashSet<>();
  }

  /**
   * Returns a {@link Set} implementation with slightly faster access for very big collections (>100K keys) and a bit smaller memory footprint
   * than {@link HashSet}. Null keys are permitted. Use sparingly only when performance considerations are utterly important;
   * in all other cases please prefer {@link HashSet}.
   */
  @Contract(value = "-> new", pure = true)
  public static <K> @NotNull Set<K> createSmallMemoryFootprintSet() {
    //noinspection SSBasedInspection
    return new ObjectOpenHashSet<>();
  }

  /** See {@link #createSmallMemoryFootprintSet()}. */
  @Contract(value = "_-> new", pure = true)
  public static <K> @NotNull Set<K> createSmallMemoryFootprintSet(int expected) {
    //noinspection SSBasedInspection
    return new ObjectOpenHashSet<>(expected);
  }

  /** See {@link #createSmallMemoryFootprintSet()}. */
  @Contract(value = "_-> new", pure = true)
  public static <K> @NotNull Set<K> createSmallMemoryFootprintSet(@NotNull Collection<? extends K> collection) {
    //noinspection SSBasedInspection
    return new ObjectOpenHashSet<>(collection);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> ConcurrentMap<K,V> createConcurrentSoftMap() {
    return new ConcurrentSoftHashMap<>();
  }

  public static @NotNull <K, V> Map<K, V> createSoftIdentityMap() {
    //noinspection deprecation
    return new SoftHashMap<>(HashingStrategy.identity());
  }

  public static void trimMap(@NotNull Map<?, ?> map) {
    if (map instanceof Object2ObjectOpenHashMap<?, ?>) {
      ((Object2ObjectOpenHashMap<?, ?>)map).trim();
    }
    else if (map instanceof Object2ObjectOpenCustomHashMap) {
      ((Object2ObjectOpenCustomHashMap<?, ?>)map).trim();
    }
  }

  public static void trimSet(@NotNull Set<?> set) {
    if (set instanceof ObjectOpenHashSet<?>) {
      ((ObjectOpenHashSet<?>)set).trim();
    }
    else if (set instanceof ObjectOpenCustomHashSet) {
      ((ObjectOpenCustomHashSet<?>)set).trim();
    }
  }
}
