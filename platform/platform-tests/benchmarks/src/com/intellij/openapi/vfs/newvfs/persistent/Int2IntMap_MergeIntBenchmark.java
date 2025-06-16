// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.psi.impl.cache.impl.id.IdEntryToScopeMapImpl;
import com.intellij.psi.search.UsageSearchContext;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/** {@link Int2IntOpenHashMap#mergeInt(int, int, java.util.function.IntBinaryOperator)} under different conditions */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 15, timeUnit = SECONDS)
@Fork(1)
@Threads(1)
public class Int2IntMap_MergeIntBenchmark {

  @State(Scope.Benchmark)
  public static class DataContext {

    private static final int[] MASKS = {
      UsageSearchContext.IN_CODE,
      UsageSearchContext.IN_COMMENTS,
      UsageSearchContext.IN_STRINGS,
      UsageSearchContext.IN_FOREIGN_LANGUAGES,
      UsageSearchContext.IN_PLAIN_TEXT
    };

    //static {
    //  MASKS = new int[129];
    //  for (int i = 0; i < MASKS.length; i++) {
    //    MASKS[i] = i;
    //  }
    //}

    private static final int TOTAL_IDs = 1_000_000;

    @Setup
    public void setup() throws Exception {
    }

    @TearDown
    public void tearDown() throws Exception {
    }

    private int nextKey(Random rnd) {
      return rnd.nextInt(-TOTAL_IDs / 2, TOTAL_IDs / 2);
    }

    private int nextMask(Random rnd) {
      return MASKS[rnd.nextInt(MASKS.length)];
    }
  }

  @State(Scope.Benchmark)
  public static class MapContext {
    @Param({"Int2IntOpenHashMap", "withCustomDefaultValue", "withCustomMergeInt"})
    protected String mapKind = "";

    public Int2IntMap map;

    @Setup
    public void setup() throws Exception {
      map = switch (mapKind) {
        case "Int2IntOpenHashMap" -> new Int2IntOpenHashMap();
        case "withCustomDefaultValue" -> new Int2IntOpenHashMap() {{
          defaultReturnValue(-1);
        }};
        case "withCustomMergeInt" -> new IdEntryToScopeMapImpl.Int2IntOpenHashMapWithFastMergeInt();
        default -> throw new IllegalStateException("Unexpected value: " + mapKind);
      };
    }
  }

  @Benchmark
  public int _baseline(DataContext dataContext) {
    //estimate cost of ~overhead:
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return dataContext.nextKey(rnd) ^ dataContext.nextMask(rnd);
  }


  @Benchmark
  public int mergeInt(DataContext dataContext,
                      MapContext mapContext) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();

    int key = dataContext.nextKey(rnd);
    int mask = dataContext.nextMask(rnd);


    return mapContext.map.mergeInt(key, mask, (prev, cur) -> prev | cur);
  }

  public static void main(String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .forks(1)
      .include(Int2IntMap_MergeIntBenchmark.class.getSimpleName() + ".*")
      //.threads(4)
      .build();

    new Runner(opt).run();
  }
}
