// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.openapi.util.SystemInfoRt;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

// ContainerUtil requires trove in classpath
@SuppressWarnings("UnnecessaryFullyQualifiedName")
public final class CollectionFactory {

  /**
   * Concurrent weak key:K -> strong value:V map.
   */
  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentWeakMap() {
    return new ConcurrentWeakHashMap<>(0.75f);
  }

  /**
   * Concurrent weak key:K -> strong value:V map.
   */
  @Contract(value = "_, -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentWeakMap(@NotNull HashingStrategy<? super K> strategy) {
    return new ConcurrentWeakHashMap<>(strategy);
  }

  /**
   * Concurrent weak key:String -> strong value:V map with case-insensitive hashing strategy.
   */
  @Contract(value = " -> new", pure = true)
  public static @NotNull <V> ConcurrentMap<@NotNull String, @NotNull V> createConcurrentWeakCaseInsensitiveMap() {
    return new ConcurrentWeakHashMap<>(HashingStrategy.caseInsensitive());
  }

  /**
   * Concurrent strong key:K -> weak value:V map
   */
  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentWeakValueMap() {
    return new ConcurrentWeakValueHashMap<>(null);
  }

  /**
   * Concurrent strong key:K -> soft value:V map
   */
  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentSoftValueMap() {
    return new ConcurrentSoftValueHashMap<>(null);
  }

  /**
   * Create {@link ConcurrentMap} with hard-referenced keys and weak-referenced values.
   * When the value get garbage-collected, the {@code evictionListener} is (eventually) invoked with this map and the corresponding key
   */
  @Contract(value = "_ -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentWeakValueMap(@NotNull BiConsumer<? super @NotNull ConcurrentMap<K,V>, ? super K> evictionListener) {
    return new ConcurrentWeakValueHashMap<>(evictionListener);
  }

  /**
   * Create {@link ConcurrentMap} with hard-referenced keys and soft-referenced values.
   * When the value get garbage-collected, the {@code evictionListener} is (eventually) invoked with this map and the corresponding key
   */
  @Contract(value = "_ -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentSoftValueMap(@NotNull BiConsumer<? super @NotNull ConcurrentMap<K,V>, ? super K> evictionListener) {
    return new ConcurrentSoftValueHashMap<>(evictionListener);
  }

  /**
   * Concurrent weak key:K -> strong value:V map with identity hashing strategy.
   */
  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentWeakIdentityMap() {
    return new ConcurrentWeakHashMap<>(HashingStrategy.identity());
  }

  /**
   * @deprecated use {@link java.util.WeakHashMap} instead
   */
  @Contract(value = " -> new", pure = true)
  @Deprecated
  public static @NotNull <K, V> Map<@NotNull K, V> createWeakMap() {
    return new java.util.WeakHashMap<>();
  }

  /**
   * Weak keys hard values hash map.
   * Null keys are NOT allowed
   * Null values ARE allowed
   */
  @Contract(value = "_,_,_ -> new", pure = true)
  public static @NotNull <K, V> Map<@NotNull K, V> createWeakMap(int initialCapacity, float loadFactor, @NotNull HashingStrategy<? super K> hashingStrategy) {
    return new WeakHashMap<>(initialCapacity, loadFactor, hashingStrategy);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> Map<@NotNull K,V> createWeakKeySoftValueMap() {
    return new WeakKeySoftValueHashMap<>();
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> Map<@NotNull K,V> createWeakKeyWeakValueMap() {
    return new WeakKeyWeakValueHashMap<>();
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> Map<@NotNull K,V> createSoftKeySoftValueMap() {
    return new SoftKeySoftValueHashMap<>();
  }

  @Contract(value = "_,_,_ -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentWeakKeySoftValueIdentityMap(int initialCapacity,
                                                                                                float loadFactor,
                                                                                                int concurrencyLevel) {
    //noinspection deprecation
    return new ConcurrentWeakKeySoftValueHashMap<>(initialCapacity, loadFactor, concurrencyLevel, HashingStrategy.identity());
  }

  public static @NotNull <K, V> Map<@NotNull K, V> createWeakIdentityMap(int initialCapacity, float loadFactor) {
    return createWeakMap(initialCapacity, loadFactor, HashingStrategy.identity());
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentWeakKeyWeakValueMap() {
    return new ConcurrentWeakKeyWeakValueHashMap<>(100, 0.75f, Runtime.getRuntime().availableProcessors(), HashingStrategy.canonical());
  }

  @Contract(value = "_ -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentWeakKeyWeakValueMap(@NotNull HashingStrategy<? super K> strategy) {
    return new ConcurrentWeakKeyWeakValueHashMap<>(100, 0.75f, Runtime.getRuntime().availableProcessors(), strategy);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentWeakKeyWeakValueIdentityMap() {
    return new ConcurrentWeakKeyWeakValueHashMap<>(100, 0.75f, Runtime.getRuntime().availableProcessors(), HashingStrategy.identity());
  }

  @Contract(value = "_,_,_,_ -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentWeakMap(int initialCapacity,
                                                                            float loadFactor,
                                                                            int concurrencyLevel,
                                                                            @NotNull HashingStrategy<? super K> hashingStrategy) {
    return new ConcurrentWeakHashMap<>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentWeakKeySoftValueMap() {
    return createConcurrentWeakKeySoftValueMap(100, 0.75f, Runtime.getRuntime().availableProcessors(), HashingStrategy.canonical());
  }

  @Contract(value = "_,_,_,_-> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentWeakKeySoftValueMap(int initialCapacity,
                                                                                        float loadFactor,
                                                                                        int concurrencyLevel,
                                                                                        @NotNull HashingStrategy<? super K> hashingStrategy) {
    //noinspection deprecation
    return new ConcurrentWeakKeySoftValueHashMap<>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  public static @NotNull <T> Map<CharSequence, T> createCharSequenceMap(boolean caseSensitive, int expectedSize, float loadFactor) {
    return new Object2ObjectOpenCustomHashMap<>(expectedSize, loadFactor, FastUtilHashingStrategies.getCharSequenceStrategy(caseSensitive));
  }

  public static @NotNull Set<CharSequence> createCharSequenceSet(boolean caseSensitive, int expectedSize, float loadFactor) {
    return new ObjectOpenCustomHashSet<>(expectedSize, loadFactor, FastUtilHashingStrategies.getCharSequenceStrategy(caseSensitive));
  }

  public static @NotNull Set<CharSequence> createCharSequenceSet(@NotNull List<? extends CharSequence> items) {
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
  public static @NotNull Set<String> createCaseInsensitiveStringSet(int initialSize) {
    return new ObjectOpenCustomHashSet<>(initialSize, FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  public static <V> @NotNull Map<String, V> createCaseInsensitiveStringMap() {
    return new Object2ObjectOpenCustomHashMap<>(FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  public static <V> @NotNull Map<String, V> createCaseInsensitiveStringMap(int expectedSize) {
    return new Object2ObjectOpenCustomHashMap<>(expectedSize, FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  public static <V> @NotNull Map<String, V> createCaseInsensitiveStringMap(@NotNull Map<String, V> source) {
    return new Object2ObjectOpenCustomHashMap<>(source, FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  @Contract(value = "_,_,_ -> new", pure = true)
  @SuppressWarnings("SameParameterValue")
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentSoftKeySoftValueMap(int initialCapacity,
                                                                                        float loadFactor,
                                                                                        int concurrencyLevel) {
    return new ConcurrentSoftKeySoftValueHashMap<>(initialCapacity, loadFactor, concurrencyLevel, HashingStrategy.canonical());
  }

  @Contract(value = "_,_,_ -> new", pure = true)
  @SuppressWarnings("SameParameterValue")
  static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentSoftKeySoftValueIdentityMap(int initialCapacity,
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

  public static @NotNull Set<String> createFilePathSet(String @NotNull [] paths, boolean isFileSystemCaseSensitive) {
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
           ? new LinkedHashSet<>()
           : new ObjectLinkedOpenCustomHashSet<>(FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  public static @NotNull Set<String> createFilePathLinkedSet(@NotNull Set<String> source) {
    return SystemInfoRt.isFileSystemCaseSensitive
           ? new LinkedHashSet<>(source)
           : new ObjectLinkedOpenCustomHashSet<>(source, FastUtilHashingStrategies.getCaseInsensitiveStringStrategy());
  }

  /**
   * Create a linked map with key hash strategy according to file system path case sensitivity.
   */
  public static @NotNull <V> Map<String, V> createFilePathLinkedMap() {
    return SystemInfoRt.isFileSystemCaseSensitive
           ? new LinkedHashMap<>()
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
   * than {@link java.util.HashMap}. Null keys and values are permitted. Use sparingly only when performance considerations are utterly important;
   * in all other cases please prefer {@link java.util.HashMap}.
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
   * than {@link java.util.HashSet}. Null keys are permitted. Use sparingly only when performance considerations are utterly important;
   * in all other cases please prefer {@link java.util.HashSet}.
   */
  @Contract(value = "-> new", pure = true)
  public static <K> @NotNull Set<K> createSmallMemoryFootprintLinkedSet() {
    return new ObjectLinkedOpenHashSet<>();
  }

  /**
   * Returns a {@link Set} implementation with slightly faster access for very big collections (>100K keys) and a bit smaller memory footprint
   * than {@link java.util.HashSet}. Null keys are permitted. Use sparingly only when performance considerations are utterly important;
   * in all other cases please prefer {@link java.util.HashSet}.
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

  /**
   * Soft keys hard values hash map.
   * Null keys are NOT allowed
   * Null values are allowed
   */
  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> Map<@NotNull K, V> createSoftMap() {
    return new SoftHashMap<>(4);
  }

  @Contract(value = "_ -> new", pure = true)
  static @NotNull <K,V> Map<@NotNull K,V> createSoftMap(@NotNull HashingStrategy<? super K> strategy) {
    return new SoftHashMap<>(strategy);
  }

  /**
   * Create {@link Map} with soft-referenced keys and hard-referenced values.
   * When the key get garbage-collected, the {@code evictionListener} is (eventually) invoked with this map and the corresponding value
   */
  @Contract(value = "_ -> new", pure = true)
  public static @NotNull <K,V> Map<@NotNull K,V> createSoftMap(@Nullable BiConsumer<? super @NotNull Map<K, V>, ? super V> evictionListener) {
    return new SoftHashMap<>(10, HashingStrategy.canonical(), evictionListener);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentSoftMap() {
    return new ConcurrentSoftHashMap<>(null);
  }

  /**
   * Create {@link ConcurrentMap} with soft-referenced keys and hard-referenced values.
   * When the key get garbage-collected, the {@code evictionListener} is (eventually) invoked with this map and the corresponding value
   */
  @Contract(value = "_ -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentSoftMap(@NotNull BiConsumer<? super @NotNull ConcurrentMap<K,V>, ? super V> evictionListener) {
    return new ConcurrentSoftHashMap<>(evictionListener);
  }

  @Contract(value = "_,_,_,_-> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<@NotNull K, @NotNull V> createConcurrentSoftMap(int initialCapacity,
                                                                                     float loadFactor,
                                                                                     int concurrencyLevel,
                                                                                     @NotNull HashingStrategy<? super K> hashingStrategy) {
    return new ConcurrentSoftHashMap<>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
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

  public static <K,V> @NotNull Map<K,V> createCustomHashingStrategyMap(@NotNull HashingStrategy<? super K> strategy) {
    return new Object2ObjectOpenCustomHashMap<>(adaptStrategy(strategy));
  }

  private static @NotNull <K> Hash.Strategy<K> adaptStrategy(@NotNull HashingStrategy<? super K> strategy) {
    return new FastUtilHashingStrategies.SerializableHashStrategy<K>() {
      @Override
      public int hashCode(@Nullable K o) {
        return strategy.hashCode(o);
      }

      @Override
      public boolean equals(@Nullable K a, @Nullable K b) {
        return strategy.equals(a, b);
      }
    };
  }

  public static <K,V> @NotNull Map<K,V> createCustomHashingStrategyMap(int expected, @NotNull HashingStrategy<? super K> strategy) {
    return new Object2ObjectOpenCustomHashMap<>(expected, adaptStrategy(strategy));
  }

  public static <K> @NotNull Set<K> createCustomHashingStrategySet(@NotNull HashingStrategy<? super K> strategy) {
    return new ObjectOpenCustomHashSet<>(adaptStrategy(strategy));
  }

  public static <K,V> @NotNull Map<K, V> createLinkedCustomHashingStrategyMap(@NotNull HashingStrategy<? super K> strategy) {
    return new Object2ObjectLinkedOpenCustomHashMap<>(adaptStrategy(strategy));
  }

  public static <K> @NotNull Set<K> createLinkedCustomHashingStrategySet(@NotNull HashingStrategy<? super K> strategy) {
    return new ObjectLinkedOpenCustomHashSet<>(adaptStrategy(strategy));
  }
}
