// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.mock.MockVirtualFile;
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
import static org.openjdk.jmh.runner.options.TimeValue.seconds;

/**
 * Benchmarks relative cost of various FSRecordsImpl.update() parts: loading, saving, locking, symlink processing...
 */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@Fork(3)
@Threads(4)
@SuppressWarnings("ClassEscapesDefinedScope")
public class VFSChildrenUpdateBenchmark {

  public static final int MAX_TREE_DEPTH = 4;


  @State(Scope.Benchmark)
  public static class ChildrenUpdateBenchmark {

    public static final MockVirtualFile FAKE_PARENT_FILE = MockVirtualFile.file("");
    @Param({"16", "64"})
    public int CHILDREN_COUNT = 16;

    public int rootFolderId;

    private final IntArrayList foldersIds = new IntArrayList();

    @Setup
    public void setup(FSRecordsContext vfsContext) throws Exception {
      FSRecordsImpl vfs = vfsContext.vfs();

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
      vfs.treeAccessor().doSaveChildren(
        folderId,
        new ListResult(vfs.getModCount(folderId), childrenInfos, folderId)
      );
      int flags = vfs.getFlags(folderId);
      vfs.setFlags(folderId, flags | Flags.CHILDREN_CACHED);
    }

    protected int tossFolderId() {
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      int randomIndex = rnd.nextInt(foldersIds.size());
      return foldersIds.getInt(randomIndex);
    }


    @Benchmark
    public ListResult listChildren(FSRecordsContext vfsContext) throws IOException {
      int folderId = tossFolderId();
      FSRecordsImpl vfs = vfsContext.vfs();
      ListResult children = vfs.list(folderId);
      return children;
    }

    @Benchmark
    public ListResult listChildren_AndSaveSame(FSRecordsContext vfsContext) throws IOException {
      int folderId = tossFolderId();
      FSRecordsImpl vfs = vfsContext.vfs();
      ListResult children = vfs.list(folderId);
      ListResult modifiedChildren = children;

      vfs.treeAccessor().doSaveChildren(folderId, modifiedChildren);
      return children;
    }

    @Benchmark
    public ListResult listChildren_AndSaveSame_WithSymlink(FSRecordsContext vfsContext) throws IOException {
      int folderId = tossFolderId();
      FSRecordsImpl vfs = vfsContext.vfs();
      ListResult children = vfs.list(folderId);
      ListResult modifiedChildren = children;

      vfs.updateSymlinksForNewChildren(FAKE_PARENT_FILE, children, modifiedChildren);

      vfs.treeAccessor().doSaveChildren(folderId, modifiedChildren);
      return children;
    }

    @Benchmark
    public ListResult updateChildren_ReturnSame(FSRecordsContext vfsContext) throws IOException {
      int folderId = tossFolderId();
      FSRecordsImpl vfs = vfsContext.vfs();
      return vfs.update(FAKE_PARENT_FILE, folderId, children -> children, true);
    }

    @Benchmark
    public ListResult updateChildren_ModifyingModCount(FSRecordsContext vfsContext) throws IOException {
      int folderId = tossFolderId();
      FSRecordsImpl vfs = vfsContext.vfs();
      return vfs.update(FAKE_PARENT_FILE, folderId, children -> {
        return new ListResult(1, children.children, folderId);
      }, true);
    }
  }


  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .jvmArgs()
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      .include(VFSChildrenUpdateBenchmark.class.getSimpleName() + ".*")
      .threads(10)
      .forks(2)
      .mode(Mode.AverageTime)
      .timeUnit(NANOSECONDS)
      .warmupIterations(2).warmupTime(seconds(1))
      .measurementIterations(3).measurementTime(seconds(2))
      .build();

    new Runner(opt).run();
  }
}
