// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.indexes;

import com.intellij.util.indexing.impl.*;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmark for {@link com.intellij.util.indexing.impl.MapIndexStorageCache} implementations
 */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = SECONDS)
@Measurement(iterations = 2, time = 15, timeUnit = SECONDS)
@Fork(1)
public class MapIndexCacheBenchmark {

  @State(Scope.Benchmark)
  public static class InputContext {

    @Param("20000")
    public int inputsCountToTestWith;

    public Integer[] inputs;

    @Setup
    public void setupBenchmark() throws Exception {
      IndexDebugProperties.IS_IN_STRESS_TESTS = true;

      inputs = new Integer[inputsCountToTestWith];
      for (int i = 0; i < inputs.length; i++) {
        inputs[i] = i;
      }
    }

    @Setup(Level.Iteration)
    public void setupIteration_resetIndex() throws Exception {
      index = 0;
    }

    private int index = 0;

    private Integer nextInput() {
      index = (index + 1) % inputs.length;
      return inputs[index];
    }
  }

  @State(Scope.Benchmark)
  public static class CacheContext {

    //1024 is a default value for multi-key indexes
    @Param({"1024", "16384"})
    public int cacheSize;


    private final MapIndexStorageCacheProvider storageLayoutProviderToTest = SlruIndexStorageCacheProvider.INSTANCE;

    public MapIndexStorageCache<Integer, Void> cache;


    @Setup
    public void setupBenchmark() throws Exception {
      IndexDebugProperties.IS_IN_STRESS_TESTS = true;

      cache = storageLayoutProviderToTest.createCache(
        key -> new ChangeTrackingValueContainer<>(ValueContainerImpl::createNewValueContainer),
        (key, container) -> {
        },
        EnumeratorIntegerDescriptor.INSTANCE,
        cacheSize
      );
    }

    @TearDown
    public void closeBenchmark() throws Exception {
      if (cache != null) {
        cache.invalidateAll();
      }
    }
  }


  @Benchmark
  public Integer baseline_nextInputGeneration(InputContext inputContext) throws Exception {
    return inputContext.nextInput();
  }


  @Benchmark
  public Object read(InputContext inputContext,
                     CacheContext storageContext) throws Exception {
    MapIndexStorageCache<Integer, Void> cache = storageContext.cache;
    Integer key = inputContext.nextInput();
    return cache.read(key);
  }


  public static void main(final String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(MapIndexCacheBenchmark.class.getSimpleName() + ".*")
      .jvmArgs("-Xmx4g")
      .forks(1)
      .threads(1)
      .build();

    new Runner(opt).run();
  }
}
