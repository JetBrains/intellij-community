// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.dev.InvertedNameIndexOverIntToIntMultimap;
import com.intellij.platform.util.io.storages.intmultimaps.NonDurableNonParallelIntToMultiIntMap;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMap;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import it.unimi.dsi.fastutil.ints.IntArrays;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.intellij.openapi.vfs.newvfs.persistent.InvertedNameIndex.NULL_NAME_ID;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * https://youtrack.jetbrains.com/issue/IDEA-316813
 */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
public class InvertedNameIndexBenchmark {


  @State(Scope.Benchmark)
  public static class Context {
    @Param({"1000", "4000", "16000"})
    public int SAME_NAME_FILES_COUNT;

    @Param({"legacy", "in-memory-int2int-map", "durable-int2int-map"})
    public String implementation;

    @SuppressWarnings("FieldMayBeStatic")
    public final int nameId = 42;
    public int[] fileIds;

    public InvertedNameIndex invertedNameIndex;

    private ExtendibleHashMap durableMap = null;

    @Setup
    public void setup() throws Exception {
      invertedNameIndex = switch (implementation) {
        case "legacy" -> new DefaultInMemoryInvertedNameIndex();
        case "in-memory-int2int-map" -> new InvertedNameIndexOverIntToIntMultimap(new NonDurableNonParallelIntToMultiIntMap(SAME_NAME_FILES_COUNT, 0.3f));
        case "durable-int2int-map" -> {
          File file = File.createTempFile("InvertedNameIndexBenchmark", "mmap");
          durableMap = ExtendibleMapFactory
            .largeSize()
            .open(file.toPath());
          yield new InvertedNameIndexOverIntToIntMultimap(durableMap);
        }

        default -> throw new IllegalStateException("Unknown implementation [" + implementation + "]");
      };

      this.fileIds = IntStream.range(1000, 1000 + SAME_NAME_FILES_COUNT)
        .toArray();

      IntArrays.shuffle(this.fileIds, ThreadLocalRandom.current());

      for (int fileId : this.fileIds) {
        invertedNameIndex.updateFileName(fileId, NULL_NAME_ID, nameId);
      }
    }

    @TearDown
    public void tearDown() throws Exception {
      if (durableMap != null) {
        durableMap.closeAndClean();
      }
    }

  }

  @Benchmark
  public void removeManyFilesWithSameName(final Context context) {
    int[] fileIdsToRemove = context.fileIds;
    int nameId = context.nameId;
    InvertedNameIndex invertedNameIndex = context.invertedNameIndex;
    for (int fileId : fileIdsToRemove) {
      invertedNameIndex.updateFileName(fileId, nameId, NULL_NAME_ID);
    }
  }

  public static void main(String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .mode(Mode.AverageTime)
      .include(InvertedNameIndexBenchmark.class.getSimpleName() + ".*")
      .threads(1)
      .build();

    new Runner(opt).run();
  }
}
