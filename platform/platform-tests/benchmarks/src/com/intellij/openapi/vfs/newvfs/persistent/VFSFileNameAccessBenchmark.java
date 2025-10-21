// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.namecache.MRUFileNameCache;
import com.intellij.openapi.vfs.newvfs.persistent.namecache.SLRUFileNameCache;
import com.intellij.util.io.DataEnumeratorEx;
import com.intellij.util.io.ScannableDataEnumeratorEx;
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

    @Param({"5000", "30000", "80000", "500000"})//request only those files/filenames
    public int FILES_TO_ACCESS = 5_000;

    private int maxFileId;
    private int minFileId;

    private MRUFileNameCache mruFileNameCache;

    private SLRUFileNameCache slruFileNameCacheOverFakeEnumerator;
    private MRUFileNameCache mruFileNameCacheOverFakeEnumerator;
    private DataEnumeratorEx<String> fakeEnumerator;

    @Setup
    public void setup(FSRecordsContext vfsContext) throws Exception {
      FSRecordsImpl vfs = vfsContext.vfs();
      PersistentFSRecordsStorage records = vfs.connection().records();
      mruFileNameCache = new MRUFileNameCache(vfs.connection().names());

      for (int i = 0; i < FILE_RECORDS_COUNT; i++) {
        int fileId = records.allocateRecord();
        if (minFileId == 0) {
          minFileId = fileId;
        }
        //avg(fileName.length in monorepo) = 27 bytes:
        String name = "name.%022d".formatted(fileId % FILE_NAMES_COUNT);
        int nameId = vfs.getNameId(name);
        records.updateNameId(fileId, nameId);
      }
      maxFileId = records.maxAllocatedID();

      // name <=> id.toString()
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
      slruFileNameCacheOverFakeEnumerator = new SLRUFileNameCache(fakeEnumerator);
      mruFileNameCacheOverFakeEnumerator = new MRUFileNameCache(fakeEnumerator);
    }

    private int generateFileId(ThreadLocalRandom rnd) {
      return rnd.nextInt(minFileId, maxFileId + 1);
    }

    private int generateFileId(ThreadLocalRandom rnd,
                               int subsetSize) {
      return rnd.nextInt(maxFileId + 1 - subsetSize, maxFileId + 1);
    }
  }


  @Benchmark
  public CharSequence VFS_getNameOfRandomFile(Context mainContext,
                                              FSRecordsContext vfsContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    FSRecordsImpl vfs = vfsContext.vfs();

    int fileId = mainContext.generateFileId(rnd, mainContext.FILES_TO_ACCESS);
    return vfs.getName(fileId);
  }

  @Benchmark
  public CharSequence VFS_getNameOfRandomFile_viaMRUFileNameCache(Context mainContext,
                                                                  FSRecordsContext vfsContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    FSRecordsImpl vfs = vfsContext.vfs();
    MRUFileNameCache mruFileNameCache = mainContext.mruFileNameCache;

    int fileId = mainContext.generateFileId(rnd, mainContext.FILES_TO_ACCESS);
    int nameId = vfs.getNameIdByFileId(fileId);
    return mruFileNameCache.valueOf(nameId);
  }

  @Benchmark
  public CharSequence VFS_getNameOfRandomFile_BypassingFileNameCache(Context mainContext,
                                                                     FSRecordsContext vfsContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    FSRecordsImpl vfs = vfsContext.vfs();
    ScannableDataEnumeratorEx<String> names = vfs.connection().names();

    int fileId = mainContext.generateFileId(rnd, mainContext.FILES_TO_ACCESS);
    int nameId = vfs.getNameIdByFileId(fileId);
    return names.valueOf(nameId);
  }


  //========================= FileNameCache directly: ===========================================================

  @Benchmark
  public CharSequence valueOfRandomId_via_FakeEnumerator(Context mainContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    DataEnumeratorEx<String> fileNameCache = mainContext.fakeEnumerator;

    int nameId = rnd.nextInt(1, mainContext.FILES_TO_ACCESS);
    return fileNameCache.valueOf(nameId);
  }

  @Benchmark
  public CharSequence valueOfRandomId_via_SLRUFileNameCache(Context mainContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    SLRUFileNameCache fileNameCache = mainContext.slruFileNameCacheOverFakeEnumerator;

    int nameId = rnd.nextInt(1, mainContext.FILES_TO_ACCESS);
    return fileNameCache.valueOf(nameId);
  }

  @Benchmark
  public CharSequence valueOfRandomId_via_MRUFileNameCache(Context mainContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    MRUFileNameCache fileNameCache = mainContext.mruFileNameCacheOverFakeEnumerator;

    int nameId = rnd.nextInt(1, mainContext.FILES_TO_ACCESS);
    return fileNameCache.valueOf(nameId);
  }


  //========================= baselines: ===============================================================

  @Benchmark
  public int _baseline_VFS_getNameIdByRandomFileId(Context mainContext,
                                                   FSRecordsContext vfsContext) throws Exception {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    FSRecordsImpl vfs = vfsContext.vfs();

    int fileId = mainContext.generateFileId(rnd, mainContext.FILES_TO_ACCESS);
    return vfs.getNameIdByFileId(fileId);
  }

  @Benchmark
  public int _baseline_rnd_(Context mainContext) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return mainContext.generateFileId(rnd, mainContext.FILES_TO_ACCESS);
  }


  public static void main(final String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .jvmArgs(
        "-Djava.awt.headless=true",

        "-Dvfs.name-cache.check-names=false",
        "-Dvfs.name-cache.track-stats=false",

        "-Dvfs.name-cache.impl=mru",
        //"-Dvfs.name-cache.impl=none",
        //"-Dvfs.name-cache.impl=slru",

        "-Dvfs.use-fast-names-enumerator=false"
      )
      .threads(1)
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      .include("\\W" + VFSFileNameAccessBenchmark.class.getSimpleName() + "\\.")
      .build();

    new Runner(opt).run();
  }
}
