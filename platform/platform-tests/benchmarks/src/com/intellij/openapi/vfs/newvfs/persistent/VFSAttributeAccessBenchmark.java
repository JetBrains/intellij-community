// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.FileAttribute;
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
 * Benchmark for VFS attribute read/write.
 * Few different access modes:
 * 1. plain read/write via Input/OutputStream
 * 2. 'raw' read via lambda(ByteBuffer)
 * 3. 'fast attributes' -- experimental feature for int-attributes
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(4)
public class VFSAttributeAccessBenchmark {

  public static final FileAttribute TEST_STRING_ATTRIBUTE = new FileAttribute("TEST_STRING_ATTRIBUTE", 1, false);
  public static final FileAttribute TEST_INT_ATTRIBUTE = new FileAttribute("TEST_INT_ATTRIBUTE", 1, true);

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
        records.updateNameId(fileId, vfs.getNameId("test"));
      }
      maxFileId = records.maxAllocatedID();
    }

    private int generateFileId(ThreadLocalRandom rnd) {
      return rnd.nextInt(minFileId, maxFileId + 1);
    }
  }

  @State(Scope.Benchmark)
  public static class RegularAttributeContext {
    private PersistentFSAttributeAccessor attributeAccessor;

    @Setup
    public void setup(FSRecordsContext vfsContext,
                      Context mainContext) throws Exception {
      final FSRecordsImpl vfs = vfsContext.vfs();
      attributeAccessor = vfs.attributeAccessor();

      for (int fileId = mainContext.minFileId; fileId <= mainContext.maxFileId; fileId++) {
        try (var stream = attributeAccessor.writeAttribute(fileId, TEST_INT_ATTRIBUTE)) {
          stream.writeInt(10_542);
        }
        try (var stream = attributeAccessor.writeAttribute(fileId, TEST_STRING_ATTRIBUTE)) {
          stream.writeUTF("test string");
        }
      }
    }
  }

  @State(Scope.Benchmark)
  public static class FastIntAttributeContext {
    private SpecializedFileAttributes.IntFileAttributeAccessor fastAttributeAccessor;

    @Setup
    public void setup(FSRecordsContext vfsContext,
                      Context mainContext) throws Exception {
      FSRecordsImpl vfs = vfsContext.vfs();
      fastAttributeAccessor = SpecializedFileAttributes.specializeAsFastInt(vfs, TEST_INT_ATTRIBUTE);
    }

    @TearDown
    public void tearDown() throws Exception {
    }
  }


  @Benchmark
  public String readStringAttribute(Context mainContext,
                                    RegularAttributeContext regularAttributeContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    int fileId = mainContext.generateFileId(rnd);
    try (var stream = regularAttributeContext.attributeAccessor.readAttribute(fileId, TEST_STRING_ATTRIBUTE)) {
      return stream.readUTF();
    }
  }

  @Benchmark
  public void writeStringAttribute(Context mainContext,
                                   RegularAttributeContext regularAttributeContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    int fileId = mainContext.generateFileId(rnd);
    try (var stream = regularAttributeContext.attributeAccessor.writeAttribute(fileId, TEST_STRING_ATTRIBUTE)) {
      stream.writeUTF("test string");
    }
  }

  @Benchmark
  public int readIntAttribute(Context mainContext,
                              RegularAttributeContext regularAttributeContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    int fileId = mainContext.generateFileId(rnd);
    try (var stream = regularAttributeContext.attributeAccessor.readAttribute(fileId, TEST_INT_ATTRIBUTE)) {
      return stream.readInt();
    }
  }

  @Benchmark
  public int readIntAttributeRaw(Context mainContext,
                                 RegularAttributeContext regularAttributeContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    int fileId = mainContext.generateFileId(rnd);
    return regularAttributeContext.attributeAccessor.readAttributeRaw(
      fileId, TEST_INT_ATTRIBUTE,
      buffer -> buffer.getInt(0)
    );
  }

  @Benchmark
  public void writeIntAttribute(Context mainContext,
                                RegularAttributeContext regularAttributeContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    int fileId = mainContext.generateFileId(rnd);
    try (var stream = regularAttributeContext.attributeAccessor.writeAttribute(fileId, TEST_INT_ATTRIBUTE)) {
      stream.writeInt(10_542);
    }
  }


  @Benchmark
  public int readFastIntAttribute(Context mainContext,
                                  FastIntAttributeContext fastAttributeContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    int fileId = mainContext.generateFileId(rnd);
    return fastAttributeContext.fastAttributeAccessor.read(fileId, 0);
  }

  @Benchmark
  public void writeFastIntAttribute(Context mainContext,
                                    FastIntAttributeContext fastAttributeContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    int fileId = mainContext.generateFileId(rnd);
    int attributeValue = rnd.nextInt();
    fastAttributeContext.fastAttributeAccessor.write(fileId, attributeValue);
  }

  @Benchmark
  public void updateFastIntAttribute(Context mainContext,
                                     FastIntAttributeContext fastAttributeContext) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    int fileId = mainContext.generateFileId(rnd);
    fastAttributeContext.fastAttributeAccessor.update(fileId, attributeValue -> attributeValue + 17);
  }

  @Benchmark
  public int _baseline_rnd_(Context mainContext) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return mainContext.generateFileId(rnd);
  }


  public static void main(final String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED",
               "--add-opens=java.base/java.util=ALL-UNNAMED",
               "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
               "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
               "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
               "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
               "-Djava.awt.headless=true"

               //to enable 'new' API:
               //"-Dvfs.lock-free-impl.enable=true",
               //"-Dvfs.lock-free-impl.fraction-direct-memory-to-utilize=0.5",
      )
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      .include("\\W" + VFSAttributeAccessBenchmark.class.getSimpleName() + "\\..*")
      .build();

    new Runner(opt).run();
  }
}
