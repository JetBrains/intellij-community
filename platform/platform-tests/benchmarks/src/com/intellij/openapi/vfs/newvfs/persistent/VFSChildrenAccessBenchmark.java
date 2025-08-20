// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Flags;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;
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
import static org.openjdk.jmh.runner.options.TimeValue.seconds;

/**
 * Benchmarks different approaches to access CHILDREN (file attribute).
 * New API allows raw access to the segment page ByteBuffer, while old API allows only access through InputStream
 * over copied byte[] array.
 */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@Fork(3)
@Threads(4)
@SuppressWarnings("ClassEscapesDefinedScope")
public class VFSChildrenAccessBenchmark {

  public static final int MAX_TREE_DEPTH = 4;


  @State(Scope.Benchmark)
  public static abstract class TreeAccessorBenchmark {

    @Param({"16", "64"})
    public int CHILDREN_COUNT = 16;

    public int rootFolderId;

    public PersistentFSTreeAccessor treeAccessor;

    private final IntArrayList foldersIds = new IntArrayList();

    protected abstract PersistentFSTreeAccessor createTreeAccessor(FSRecordsImpl vfs) throws IOException;

    @Setup
    public void setup(FSRecordsContext vfsContext) throws Exception {
      FSRecordsImpl vfs = vfsContext.vfs();

      treeAccessor = createTreeAccessor(vfs);

      rootFolderId = vfs.createRecord();
      vfs.setFlags(rootFolderId, Flags.IS_DIRECTORY);
      attachChildren(vfs, rootFolderId, 1, MAX_TREE_DEPTH);
    }

    protected void attachChildren(FSRecordsImpl vfs,
                                  int folderId,
                                  int depth,
                                  int maxDepth) throws IOException {
      foldersIds.add(folderId);
      boolean leafLevel = (depth == maxDepth);
      PersistentFSRecordsStorage records = vfs.connection().records();
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

        records.updateNameId(childId, nameId);

        childrenInfos.add(new ChildInfoImpl(
          childId,
          nameId,
          null, null, null
        ));
      }
      ListResult children = new ListResult(vfs.getModCount(folderId), childrenInfos, folderId);
      treeAccessor.doSaveChildren(folderId, children);
    }

    protected int tossFolderId() {
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      int randomIndex = rnd.nextInt(foldersIds.size());
      return foldersIds.getInt(randomIndex);
    }

    @Benchmark
    public boolean forEachChildId(FSRecordsContext vfsContext) throws IOException {
      int folderId = tossFolderId();
      return treeAccessor.forEachChild(folderId, childId -> false);
    }

    @Benchmark
    public @NotNull ListResult listChildren(FSRecordsContext vfsContext) throws IOException {
      int folderId = tossFolderId();
      return treeAccessor.doLoadChildren(folderId);
    }

    @Benchmark
    public void updateChildren(FSRecordsContext vfsContext) throws IOException {
      int folderId = tossFolderId();
      ListResult children = treeAccessor.doLoadChildren(folderId);
      treeAccessor.doSaveChildren(folderId, children);
    }
  }

  @State(Scope.Benchmark)
  public static class LegacyTreeAccessorBenchmark extends TreeAccessorBenchmark {
    @Override
    protected PersistentFSTreeAccessor createTreeAccessor(FSRecordsImpl vfs) {
      return new PersistentFSTreeAccessor(vfs.attributeAccessor(), vfs.recordAccessor(), vfs.connection());
    }
  }

  @State(Scope.Benchmark)
  public static class RawTreeAccessorBenchmark extends TreeAccessorBenchmark {
    @Override
    protected PersistentFSTreeAccessor createTreeAccessor(FSRecordsImpl vfs) {
      return new PersistentFSTreeRawAccessor(vfs.attributeAccessor(), vfs.recordAccessor(), vfs.connection());
    }
  }


  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .jvmArgs(
        //to enable 'new' API:
        //"-Dvfs.lock-free-impl.enable=true",
        //"-Dvfs.lock-free-impl.fraction-direct-memory-to-utilize=0.5",
      )
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      .include(VFSChildrenAccessBenchmark.class.getSimpleName() + ".*TreeAccessor.*")
      .threads(4)
      .forks(2)
      .mode(Mode.AverageTime)
      .timeUnit(NANOSECONDS)
      .warmupIterations(2).warmupTime(seconds(1))
      .measurementIterations(3).measurementTime(seconds(2))
      .build();

    new Runner(opt).run();
  }
}
