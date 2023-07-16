// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Flags;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmarks different approaches to access CHILDREN (file attribute).
 * New API allows raw access to the segment page ByteBuffer, while old API allows only access through InputStream
 * over copied byte[] array.
 */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(3)
@Threads(4)
@State(Scope.Benchmark)
public class VFSChildrenAccessBenchmark {

  public static final int MAX_TREE_DEPTH = 4;

  @Param({"16", "64"})
  public int CHILDREN_COUNT = 16;


  public int rootFolderId;

  public PersistentFSTreeAccessor oldTreeAccessor;
  public PersistentFSTreeRawAccessor newTreeAccessor;

  private IntArrayList foldersIds = new IntArrayList();

  @Setup
  public void setup(FSRecordsContext vfsContext) throws Exception {
    FSRecordsImpl vfs = vfsContext.vfs();
    PersistentFSConnection connection = vfs.connection();
    PersistentFSAttributeAccessor attributeAccessor = vfs.attributeAccessor();

    PersistentFSRecordAccessor recordsAccessor = vfs.recordAccessor();
    oldTreeAccessor = new PersistentFSTreeAccessor(attributeAccessor, recordsAccessor, connection);
    newTreeAccessor = new PersistentFSTreeRawAccessor(attributeAccessor, recordsAccessor, connection);


    rootFolderId = vfs.createRecord();
    vfs.setFlags(rootFolderId, Flags.IS_DIRECTORY);
    attachChildren(vfs, rootFolderId, 1, MAX_TREE_DEPTH);
  }

  private void attachChildren(FSRecordsImpl vfs,
                              int folderId,
                              int depth,
                              int maxDepth) throws IOException {
    foldersIds.add(folderId);
    boolean leafLevel = (depth == maxDepth);
    PersistentFSRecordsStorage records = vfs.connection().getRecords();
    ArrayList<ChildInfo> childrenInfos = new ArrayList<>();
    for (int i = 0; i < CHILDREN_COUNT; i++) {
      int childId = vfs.createRecord();

      vfs.setParent(childId, folderId);

      int nameId;
      if (!leafLevel) {
        vfs.setFlags(childId, Flags.IS_DIRECTORY);
        attachChildren(vfs, childId, depth + 1, maxDepth);
        String childName = "dir" + i;
        nameId = vfs.getNameId(childName);
      }
      else {
        String childName = "file" + i;
        nameId = vfs.getNameId(childName);
      }

      records.setNameId(childId, nameId);

      childrenInfos.add(new ChildInfoImpl(
        childId,
        nameId,
        null, null, null
      ));
    }
    ListResult children = new ListResult(vfs.getModCount(folderId), childrenInfos, folderId);
    oldTreeAccessor.doSaveChildren(folderId, children);
  }

  private int tossFolderId() {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    int randomIndex = rnd.nextInt(foldersIds.size());
    return foldersIds.getInt(randomIndex);
  }

  @Benchmark
  public int[] listChildrenIds_old(FSRecordsContext vfsContext) throws IOException {
    int folderId = tossFolderId();
    return oldTreeAccessor.listIds(folderId);
  }

  @Benchmark
  public int[] listChildrenIds_new() throws IOException {
    int folderId = tossFolderId();
    return newTreeAccessor.listIds(folderId);
  }


  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED",
               "--add-opens=java.base/java.util=ALL-UNNAMED",
               "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
               "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
               "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
               "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED"

               //to enable 'new' API:
               //"-Dvfs.lock-free-impl.enable=true",
               //"-Dvfs.lock-free-impl.fraction-direct-memory-to-utilize=0.5",
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
