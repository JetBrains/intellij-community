// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import it.unimi.dsi.fastutil.ints.IntArrays;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * https://youtrack.jetbrains.com/issue/IDEA-316813
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
public class InvertedNameIndexBenchmark {


  @State(Scope.Benchmark)
  public static class Context {
    @Param({"1000", "2000", "4000", "8000", "16000"})
    public int SAME_NAME_FILES_COUNT;

    @SuppressWarnings("FieldMayBeStatic")
    public final int nameId = 42;
    public int[] fileIds;

    public final InvertedNameIndex invertedNameIndex = new InvertedNameIndex();

    @Setup
    public void setup() {
      this.fileIds = IntStream.range(1000, 1000 + SAME_NAME_FILES_COUNT)
        .toArray();

      IntArrays.shuffle(this.fileIds, ThreadLocalRandom.current());

      for (int fileId : this.fileIds) {
        invertedNameIndex.updateFileName(fileId, nameId, InvertedNameIndex.NULL_NAME_ID);
      }
    }
  }

  @Benchmark
  public void removeManyFilesWithSameName(final Context context) {
    final int[] fileIdsToRemove = context.fileIds;
    final int nameId = context.nameId;
    final InvertedNameIndex invertedNameIndex = context.invertedNameIndex;
    for (int fileId : fileIdsToRemove) {
      invertedNameIndex.updateFileName(fileId, InvertedNameIndex.NULL_NAME_ID, nameId);
    }
  }

  public static void main(String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .mode(Mode.SingleShotTime)
      .include(InvertedNameIndexBenchmark.class.getSimpleName() + ".*")
      .threads(1)
      .build();

    new Runner(opt).run();
  }
}
