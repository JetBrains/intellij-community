// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmarks different approaches to access CHILDREN (file attribute).
 * New API allows raw access to the segment page ByteBuffer, while old API allows only access through InputStream
 * over copied byte[] array.
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class VFSChildrenAccessBenchmark {

  @Param({"2", "8", "64"})
  public int CHILDREN_COUNT = 16;


  public int folderId;

  public PersistentFSTreeAccessor oldTreeAccessor;
  public PersistentFSTreeRawAccessor newTreeAccessor;

  @Setup
  public void setup(FSRecordsContext vfsContext) throws Exception {
    FSRecordsImpl vfs = vfsContext.vfs();
    final PersistentFSConnection connection = vfs.connection();
    final PersistentFSAttributeAccessor attributeAccessor = vfs.attributeAccessor();

    folderId = vfs.createRecord();

    oldTreeAccessor = new PersistentFSTreeAccessor(attributeAccessor, connection);
    newTreeAccessor = new PersistentFSTreeRawAccessor(attributeAccessor, connection);


    ArrayList<ChildInfo> childrenInfos = new ArrayList<>();
    for (int i = 0; i < CHILDREN_COUNT; i++) {
      int childId = vfs.createRecord();
      int nameId = vfs.getNameId("child_" + i);
      childrenInfos.add(new ChildInfoImpl(
        childId,
        nameId,
        null, null, null
      ));
    }
    ListResult children = new ListResult(vfs.getModCount(folderId), childrenInfos, folderId);
    oldTreeAccessor.doSaveChildren(folderId, children);
  }

  @Benchmark
  public int[] listChildrenIds_old() throws IOException {
    return oldTreeAccessor.listIds(folderId);
  }

  @Benchmark
  public int[] listChildrenIds_new() throws IOException {
    return newTreeAccessor.listIds(folderId);
  }


  public static void main(final String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED",
               "--add-opens=java.base/java.util=ALL-UNNAMED",
               "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
               "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
               "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
               "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",

               //to enable 'new' API:
               //"-Dvfs.lock-free-impl.enable=true",
               //"-Dvfs.lock-free-impl.fraction-direct-memory-to-utilize=0.5",
               "-Dvfs.use-streamlined-attributes-storage=true"
      )
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      .include(VFSChildrenAccessBenchmark.class.getSimpleName() + ".*")
      .threads(1)
      .build();

    new Runner(opt).run();
  }
}
