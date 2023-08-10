// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.namecache.SLRUFileNameCache;
import com.intellij.util.io.DataEnumeratorEx;
import com.intellij.util.io.ScannableDataEnumeratorEx;
import org.jetbrains.annotations.Nullable;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmarks cost of access to fileName in VFS: file name by fileId, file name by nameId
 */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(4)
public class VFSFileNameAccessBenchmark {

  @State(Scope.Benchmark)
  public static class Context {
    @Param("5000000")//5M files ~ huge project
    public int FILE_RECORDS_COUNT = 5_000_000;

    @Param("500000")//500k distinct file names
    public int FILE_NAMES_COUNT = 500_000;

    private int maxFileId;
    private int minFileId;
    private SLRUFileNameCache fileNameCacheOverFakeEnumerator;
    private DataEnumeratorEx<String> fakeEnumerator;

    @Setup
    public void setup(FSRecordsContext vfsContext) throws Exception {
      FSRecordsImpl vfs = vfsContext.vfs();
      PersistentFSRecordsStorage records = vfs.connection().getRecords();
      for (int i = 0; i < FILE_RECORDS_COUNT; i++) {
        int fileId = records.allocateRecord();
        if (minFileId == 0) {
          minFileId = fileId;
        }
        String name = "fileName" + (i % FILE_NAMES_COUNT);
        int nameId = vfs.getNameId(name);
        records.setNameId(fileId, nameId);
      }
      maxFileId = records.maxAllocatedID();

      fakeEnumerator = new DataEnumeratorEx<>() {
        @Override
        public int tryEnumerate(String value) throws IOException {
          return Integer.parseInt(value);
        }

        @Override
        public int enumerate(String value) throws IOException {
          return Integer.parseInt(value);
        }

        @Override
        public String valueOf(int id) throws IOException {
          return Integer.toString(id);
        }
      };
      fileNameCacheOverFakeEnumerator = new SLRUFileNameCache(fakeEnumerator);
    }

    private int generateFileId(ThreadLocalRandom rnd) {
      return rnd.nextInt(minFileId, maxFileId + 1);
    }
  }


  @Benchmark
  public CharSequence getNameOfRandomFile(Context mainContext,
                                          FSRecordsContext vfsContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    FSRecordsImpl vfs = vfsContext.vfs();

    int fileId = mainContext.generateFileId(rnd);
    return vfs.getName(fileId);
  }

  @Benchmark
  public CharSequence getNameOfRandomFile_BypassingFileNameCache(Context mainContext,
                                                                 FSRecordsContext vfsContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    FSRecordsImpl vfs = vfsContext.vfs();
    ScannableDataEnumeratorEx<String> names = vfs.connection().getNames();

    int fileId = mainContext.generateFileId(rnd);
    int nameId = vfs.getNameIdByFileId(fileId);
    return names.valueOf(nameId);
  }


  //========================= FileNameCache directly: ===========================================================

  @Benchmark
  public CharSequence valueOfRandomId_viaFakeEnumerator(Context mainContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    SLRUFileNameCache fileNameCache = mainContext.fileNameCacheOverFakeEnumerator;

    int nameId = rnd.nextInt(1, mainContext.FILE_RECORDS_COUNT);
    return fileNameCache.valueOf(nameId);
  }

  @Benchmark
  public CharSequence valueOfRandomId_viaFileNameCache(Context mainContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    SLRUFileNameCache fileNameCache = mainContext.fileNameCacheOverFakeEnumerator;

    int nameId = rnd.nextInt(1, mainContext.FILE_RECORDS_COUNT);
    return fileNameCache.valueOf(nameId);
  }


  //========================= baselines: ===============================================================

  @Benchmark
  public int _baseline_getNameIdByRandomFileId(Context mainContext,
                                               FSRecordsContext vfsContext) throws Exception {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    FSRecordsImpl vfs = vfsContext.vfs();

    int fileId = mainContext.generateFileId(rnd);
    return vfs.getNameIdByFileId(fileId);
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

        "-Dvfs.name-cache.check-names=false",
        "-Dvfs.name-cache.track-stats=false",
        //"-Dvfs.name-cache.enable=false",

        "-Dvfs.use-fast-names-enumerator=false"
      )
      .threads(1)
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      .include("\\W" + VFSFileNameAccessBenchmark.class.getSimpleName() + "\\..*")
      .build();

    new Runner(opt).run();
  }
}
