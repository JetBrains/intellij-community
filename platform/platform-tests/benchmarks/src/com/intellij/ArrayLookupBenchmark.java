// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Compare linear search vs binary search
 * On Apple M1 Max binary search starts outperforming linear search ~between array length 32 and 64
 */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 2, time = 5, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(1)
public class ArrayLookupBenchmark {

  @State(Scope.Benchmark)
  public static class DataContext {
    @Param({"8", "16", "32", "64"})
    public int ARRAY_SIZE = 64;

    public Integer[] objects;
    public int[] ints;

    @Setup
    public void setup() throws Exception {
      objects = new Integer[ARRAY_SIZE];
      ints = new int[ARRAY_SIZE];
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      for (int i = 0; i < ARRAY_SIZE; i++) {
        int value = rnd.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
        ints[i] = value;
        objects[i] = value;
      }
      Arrays.sort(ints);
      Arrays.sort(objects);
    }

    public Integer randomObjectKey() {
      return randomIntKey();
    }

    public int randomIntKey() {
      //should be ~1/2 of array values
      return 0;
    }
  }

  @Benchmark
  public Object _baseline() {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return rnd.nextInt(0, Integer.MAX_VALUE);
  }

  @Benchmark
  public Object lookupIntLinear(DataContext dataContext) {
    int keyToLookup = dataContext.randomIntKey();
    int[] ints = dataContext.ints;

    for (int i = 0; i < ints.length; i++) {
      int key = ints[i];
      if (key > keyToLookup) {
        return i;
      }
    }
    return -1;
  }

  @Benchmark
  public Object lookupObjectLinear(DataContext dataContext) {
    Integer keyToLookup = dataContext.randomObjectKey();
    Integer[] objects = dataContext.objects;

    for (int i = 0; i < objects.length; i++) {
      Integer key = objects[i];
      if (key.compareTo(keyToLookup) > 0) {
        return i;
      }
    }
    return -1;
  }


  @Benchmark
  public Object lookupIntBinary(DataContext dataContext) {
    int keyToLookup = dataContext.randomIntKey();

    return Arrays.binarySearch(dataContext.ints, keyToLookup);
  }

  @Benchmark
  public Object lookupObjectBinary(DataContext dataContext) {
    Integer keyToLookup = dataContext.randomObjectKey();

    return Arrays.binarySearch(dataContext.objects, keyToLookup);
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
      .include("\\W" + ArrayLookupBenchmark.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
