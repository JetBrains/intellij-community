// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.util.containers.LinkedCustomHashMap;
import com.intellij.util.containers.SLRUMap;
import com.intellij.util.containers.intcaches.SLRUIntObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmarks of {@link HashMap} vs {@link Object2ObjectOpenHashMap} (and some more)
 * ...it seems like HashMap consistently outperforms Object2ObjectOpenHashMap, even though I can't really get the reasons
 */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 2, time = 5, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(1)
public class MapsImplBenchmark {

  @State(Scope.Benchmark)
  public static class DataContext {
    @Param({"1000", "10000", "500000"})
    public int KEYS_COUNT = 500_000;

    public Object[] existingKeys;
    public Object[] nonExistingKeys;

    @Setup
    public void setup() throws Exception {
      existingKeys = new Object[KEYS_COUNT];
      nonExistingKeys = new Object[KEYS_COUNT];
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      for (int i = 0; i < KEYS_COUNT; i++) {
        int value = rnd.nextInt(0, Integer.MAX_VALUE);
        existingKeys[i] = value;
        nonExistingKeys[i] = -1 - value;
      }
    }

    public Object existingKey(ThreadLocalRandom rnd) {
      return existingKeys[rnd.nextInt(0, KEYS_COUNT)];
    }

    public Object nonExistingKey(ThreadLocalRandom rnd) {
      return nonExistingKeys[rnd.nextInt(0, KEYS_COUNT)];
    }
  }

  @State(Scope.Benchmark)
  public static class MapContext {
    @Param({/*"HashMap", /*"OpenHashMap", "LinkedCustomHashMap", "IntOpenMap", */"SLRUIntObjectMap", "SLRUMap"/*, "MRUMap"*/})
    public String mapKind = "HashMap";

    public Map<Object, Object> mapImpl;

    @Setup
    public void setup(DataContext dataContext) throws Exception {
      mapImpl = switch (mapKind) {
        case "HashMap" -> new HashMap<>();

        case "OpenHashMap" ->
          //noinspection SSBasedInspection
          new Object2ObjectOpenHashMap<>();

        case "LinkedCustomHashMap" -> new AbstractMap<>() {
          private final LinkedCustomHashMap<Object, Object> linkedMap = new LinkedCustomHashMap<>();

          @Override
          public @NotNull Set<Entry<Object, Object>> entrySet() {
            return linkedMap.entrySet();
          }

          @Override
          public Object get(Object key) {
            return linkedMap.get(key);
          }

          @Override
          public Object put(Object key, Object value) {
            return linkedMap.put(key, value);
          }
        };

        case "IntOpenMap" -> new AbstractMap<>() {
          private final Int2ObjectOpenHashMap<Object> map = new Int2ObjectOpenHashMap<>();

          @Override
          public @NotNull Set<Entry<Object, Object>> entrySet() {
            throw new UnsupportedOperationException("Method not implemented yet");
          }

          @Override
          public Object get(Object key) {
            return map.get(((Integer)key).intValue());
          }

          @Override
          public Object put(Object key, Object value) {
            return map.put(((Integer)key).intValue(), value);
          }
        };

        case "SLRUMap" -> new AbstractMap<>() {
          private final SLRUMap<Object, Object> map = new SLRUMap<>(dataContext.KEYS_COUNT * 3 / 4, dataContext.KEYS_COUNT / 4);

          @Override
          public @NotNull Set<Entry<Object, Object>> entrySet() {
            return Collections.emptySet();
          }

          @Override
          public Object put(Object key, Object value) {
            map.put(key, value);
            return null;
          }

          @Override
          public Object get(Object key) {
            return map.get(key);
          }
        };

        case "SLRUIntObjectMap" -> new AbstractMap<>() {
          private final SLRUIntObjectMap<Object> map = new SLRUIntObjectMap<>(
            dataContext.KEYS_COUNT * 3 / 4,
            dataContext.KEYS_COUNT / 4,
            (key, value) -> { /* nothing */ }
          );

          @Override
          public @NotNull Set<Entry<Object, Object>> entrySet() {
            return Collections.emptySet();
          }

          @Override
          public Object put(Object key, Object value) {
            map.put((Integer)key, value);
            return null;
          }

          @Override
          public Object get(Object key) {
            return map.get((Integer)key);
          }
        };

        case "MRUMap" -> new AbstractMap<>() {
          private final MRU_LRUCache<Object> map = new MRU_LRUCache<>(
            dataContext.KEYS_COUNT * 3 / 2,
            0 //TODO
          );

          @Override
          public @NotNull Set<Entry<Object, Object>> entrySet() {
            return Collections.emptySet();
          }

          @Override
          public Object put(Object key, Object value) {
            return map.put((Integer)key, value);
          }

          @Override
          public Object get(Object key) {
            return map.get((Integer)key);
          }
        };

        default -> throw new IllegalArgumentException("Unrecognized map kind: " + mapKind);
      };

      for (int i = 0; i < dataContext.KEYS_COUNT; i++) {
        mapImpl.put(
          dataContext.existingKeys[i],
          dataContext.existingKeys[i]
        );
      }
    }
  }

  @Benchmark
  public Object _baseline(DataContext dataContext) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return dataContext.existingKey(rnd);
  }

  @Benchmark
  public Object lookupExistingKey(DataContext dataContext,
                                  MapContext mapContext) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return mapContext.mapImpl.get(dataContext.existingKey(rnd));
  }

  @Benchmark
  public Object lookupNonExistingKey(DataContext dataContext,
                                     MapContext mapContext) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return mapContext.mapImpl.get(dataContext.nonExistingKey(rnd));
  }

  public static void main(final String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .jvmArgs()
      //.forks(1)
      .threads(1)
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      .jvmArgs("-XX:+UseG1GC")//UseSerialGC, UseParallelGC, UseG1GC
      .include("\\W" + MapsImplBenchmark.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}

class MRU_LRUCache<Value> {

  private final CacheEntry<Value>[] probationCache;
  private final int probationCacheSizeMask;

  private final CacheEntry<Value>[] protectedCache;
  private final int protectedCacheSizeMask;


  MRU_LRUCache(int probationCacheSize,
               int protectedCacheSize) {
    if (probationCacheSize <= 1) {
      throw new IllegalArgumentException("probationCacheSize(=" + probationCacheSize + ") must be > 1");
    }

    int probationCacheSizePow2 = roundUpToPowerOf2(probationCacheSize);
    probationCacheSizeMask = (probationCacheSizePow2 - 1);
    //noinspection unchecked
    probationCache = new CacheEntry[probationCacheSizePow2];

    int protectedCacheSizePow2 = roundUpToPowerOf2(probationCacheSize);
    protectedCacheSizeMask = (protectedCacheSizePow2 - 1);
    //noinspection unchecked
    protectedCache = new CacheEntry[protectedCacheSizePow2];

    //TODO small MRU cache (lock free?) + larger LRU cache, with ring buffer (int[]) for LRU ordering (so there is no GC barriers
    //     on LRU order updates)

    //TODO RC: dedicated benchmark for MapIndexStorageCache implementations. To some degree, IndexStorageLayoutBenchmark could do,
    //         also -- but dedicated benchmarks are much better. Keys distribution is a big factor in cache performance!
  }

  public Value get(int key) {
    int hash = hash(key);
    int index = hash & probationCacheSizeMask;
    CacheEntry<Value> entry = probationCache[index];
    if (entry != null && entry.key == key) {
      //TODO move to protected cache
      return entry.value;
    }

    return null;
  }

  public Value put(int key, Value value) {
    int hash = hash(key);
    int index = hash & probationCacheSizeMask;
    CacheEntry<Value> oldEntry = probationCache[index];
    probationCache[index] = new CacheEntry<>(key, value);
    if (oldEntry != null) {
      return oldEntry.value;
    }
    return null;
  }

  private static int hash(int key) {
    return key * 0x9E3779B9; // fibonacci hash
  }

  private static int roundUpToPowerOf2(int value) {
    if (value <= 0) {
      return 1;
    }
    // Subtract 1 to handle case when value is already a power of 2
    return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
  }


  private record CacheEntry<Value>(int key, Value value) {
  }
}
