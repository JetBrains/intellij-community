// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmark for FileNameCache
 */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(4)
public class VFSFileNameCacheBenchmark {

  @State(Scope.Benchmark)
  public static class Context {
    @Param("5000000")//5M files ~ huge project
    public int FILE_RECORDS_COUNT = 5_000_000;

    private int maxFileId;
    private int minFileId;

    @Setup
    public void setup(FSRecordsContext vfsContext) throws Exception {
      FSRecordsImpl vfs = vfsContext.vfs();
      PersistentFSRecordsStorage records = vfs.connection().getRecords();
      for (int i = 0; i < FILE_RECORDS_COUNT; i++) {
        int fileId = records.allocateRecord();
        if (minFileId == 0) {
          minFileId = fileId;
        }
        //just to be sure, not really needed in benchmark:
        records.setNameId(fileId, vfs.getNameId("fileName" + i));
      }
      maxFileId = records.maxAllocatedID();


      //FIXME: FileNameCache is static now, and uses static FSRecords.getInstance() instance as a underlying source
      //       of names -- so we must set this static FSRecords.impl for benchmark now, but it is much better to
      //       get rid of static FileNameCache, and make it an instance, and a part of FSRecordsImpl instance.

      Field implField = FSRecords.class.getDeclaredField("impl");
      implField.setAccessible(true);
      implField.set(null, vfs); //Basically: FSRecords.impl = vfs;
    }

    private int generateFileId(ThreadLocalRandom rnd) {
      return rnd.nextInt(minFileId, maxFileId + 1);
    }
  }


  @Benchmark
  public CharSequence getFileNameOfRandomFile(Context mainContext,
                                              FSRecordsContext vfsContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    int fileId = mainContext.generateFileId(rnd);
    return vfsContext.vfs().getName(fileId);
  }


  @Benchmark
  public int _baseline_rnd_(Context mainContext) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return mainContext.generateFileId(rnd);
  }


  public static void main(final String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .jvmArgs(
        "-Djava.awt.headless=true",
        "-Dvfs.use-fast-names-enumerator=true"
      )
      .threads(1)
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      .include("\\W" + VFSFileNameCacheBenchmark.class.getSimpleName() + "\\..*")
      .build();

    new Runner(opt).run();
  }
}
