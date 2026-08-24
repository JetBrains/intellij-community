// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/// Compares complete fixed-size workloads across [PersistentLongMap] implementations.
///
/// Every benchmark invocation performs either the number of operations stated in its method name or the number
/// selected by its load parameter. Because the class uses [Mode#SingleShotTime] and does not use
/// `OperationsPerInvocation`, JMH reports the total time for the complete batch in milliseconds.
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(value = 0)
@Threads(1)
@Timeout(time = 10, timeUnit = TimeUnit.MINUTES)
public class PersistentLongMapBenchmark {
  /// Builds a dense persistent map containing [PersistentLongMapState#entryCount] entries.
  @Benchmark
  public PersistentLongMap<Object> buildDensePersistentMap(PersistentLongMapState state) {
    PersistentLongMap<Object> map = PersistentLongMap.Companion.empty(state.implementation);
    for (int index = 0; index < state.entryCount; index++) {
      map = map.put((long)index + 1L, PRESENT_VALUE);
    }
    return map;
  }

  /// Performs [MAP_LOOKUP_CALLS] successful lookups in a deterministic permutation of the populated keys.
  @Benchmark
  public long lookup10MDensePersistentMap(PersistentLongMapState state) {
    PersistentLongMap<Object> map = state.map;
    long[] lookupKeys = state.lookupKeys;
    long checksum = 0L;

    for (int index = 0; index < MAP_LOOKUP_CALLS; index++) {
      if (map.getUnchecked(lookupKeys[index]) == PRESENT_VALUE) {
        checksum += lookupKeys[index];
      }
    }
    return checksum;
  }

  /// Replaces [MAP_REPLACE_CALLS] existing values while retaining only the newest persistent-map version.
  @Benchmark
  public PersistentLongMap<Object> replace100KDensePersistentMapEntries(PersistentLongMapState state) {
    PersistentLongMap<Object> map = state.map;
    long[] lookupKeys = state.lookupKeys;

    for (int index = 0; index < MAP_REPLACE_CALLS; index++) {
      map = map.put(lookupKeys[index], REPLACEMENT_VALUE);
    }
    return map;
  }

  /// Removes every populated key once in a deterministic permutation.
  @Benchmark
  public PersistentLongMap<Object> removeAllDensePersistentMapEntries(PersistentLongMapState state) {
    PersistentLongMap<Object> map = state.map;
    long[] removalKeys = state.removalKeys;

    for (int index = 0; index < removalKeys.length; index++) {
      map = map.remove(removalKeys[index]);
    }
    return map;
  }

  /// Parameterized dense-marker-ID workload covering every persistent-map implementation and load.
  @State(Scope.Thread)
  public static class PersistentLongMapState {
    @Param({
      "MAP_16", "VECTOR_32", "VECTOR_64", "PAGED_VECTOR_128", "PAGED_VECTOR_256", "CHAMP", "CHAMP_64"
    })
    public PersistentLongMapImplementation implementation;

    @Param({"1000", "10000", "100000"})
    public int entryCount;

    PersistentLongMap<Object> map;
    long[] lookupKeys;
    long[] removalKeys;

    /// Builds the selected implementation and deterministic access orders outside the measured region.
    @Setup(Level.Trial)
    public void setUp() {
      PersistentLongMap<Object> currentMap = PersistentLongMap.Companion.empty(implementation);
      for (int index = 0; index < entryCount; index++) {
        currentMap = currentMap.put((long)index + 1L, PRESENT_VALUE);
      }
      map = currentMap;

      lookupKeys = buildDenseKeyOrder(entryCount, MAP_LOOKUP_CALLS);
      removalKeys = buildDenseKeyOrder(entryCount, entryCount);
    }
  }

  private static final int MAP_LOOKUP_CALLS = 10_000_000;
  private static final int MAP_REPLACE_CALLS = 100_000;
  private static final int MAP_ACCESS_STEP = 8_191;

  private static final Object PRESENT_VALUE = new Object();
  private static final Object REPLACEMENT_VALUE = new Object();

  /// Runs this benchmark class through JMH.
  static void main() throws Exception {
    System.setProperty("jmh.separateClasspathJAR", "true");

    final Options options = new OptionsBuilder()
      .jvmArgs()
      //.forks(1)
      .forks(0)
      .threads(1)
      .jvmArgsAppend("-Djmh.separateClasspathJAR=true")
      .include("\\W" + PersistentLongMapBenchmark.class.getSimpleName() + ".*")
      .build();

    new Runner(options).run();
  }

  private static long[] buildDenseKeyOrder(int entryCount, int operationCount) {
    long[] result = new long[operationCount];
    int keyIndex = 0;

    for (int index = 0; index < result.length; index++) {
      result[index] = (long)keyIndex + 1L;
      keyIndex = (keyIndex + MAP_ACCESS_STEP) % entryCount;
    }
    return result;
  }
}
