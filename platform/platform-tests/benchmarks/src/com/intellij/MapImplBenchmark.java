// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmarks of {@link java.util.HashMap} vs {@link it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap}
 * ...it seems like HashMap consistently outperforms Object2ObjectOpenHashMap, even though I can't really get the reasons
 * TODO RC: add some simple HMap impl into the benchmarks
 */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 2, time = 5, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(1)
public class MapImplBenchmark {

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
    @Param({"HashMap", "OpenHashMap"})
    public String mapKind = "HashMap";

    public Map<Object, Object> mapImpl;

    @Setup
    public void setup(DataContext dataContext) throws Exception {
      mapImpl = switch (mapKind) {
        case "HashMap" -> new HashMap<>();

        case "OpenHashMap" ->
          //noinspection SSBasedInspection
          new Object2ObjectOpenHashMap<>();

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
      .forks(1)
      .threads(1)
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      .include("\\W" + MapImplBenchmark.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
