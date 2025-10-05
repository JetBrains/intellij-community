// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.text.CharArrayUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmarks cost of different ways to compute a path of VirtualFile
 * <p>
 * Results: iterative is slightly worse than recursive, but not much
 */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(4)
public class VFSComputePathBenchmark {

  @State(Scope.Benchmark)
  public static class Context {
    //@Param("500000")//500k distinct file names
    public int UNIQUE_FILE_NAMES_COUNT = 500_000;

    @Param({"10", "14"})
    public int MAX_TREE_DEPTH = 10;
    @Param({"2", "3"})
    public int CHILDREN_PER_LEVEL_COUNT = 4;
    //2^13         =  8K files (small project)
    //4^13  = 2^25 = 32M files (big project)


    private int rootFolderId;

    private IntList fileIds;

    @Setup
    public void setup(FSRecordsContext vfsContext) throws Exception {
      FSRecordsImpl vfs = vfsContext.vfs();

      rootFolderId = vfs.createRecord();
      vfs.setFlags(rootFolderId, PersistentFS.Flags.IS_DIRECTORY);

      fileIds = new IntArrayList();
      createAndAttachChildren(vfs, rootFolderId, 1, MAX_TREE_DEPTH);
    }

    protected void createAndAttachChildren(FSRecordsImpl vfs,
                                           int parentId,
                                           int depth,
                                           int maxDepth) throws IOException {
      boolean leafLevel = (depth == maxDepth);
      PersistentFSRecordsStorage records = vfs.connection().records();
      ArrayList<ChildInfo> childrenInfos = new ArrayList<>();
      for (int i = 0; i < CHILDREN_PER_LEVEL_COUNT; i++) {
        int childId = vfs.createRecord();

        vfs.setParent(childId, parentId);

        if (!leafLevel) {
          vfs.setFlags(childId, PersistentFS.Flags.IS_DIRECTORY);
          createAndAttachChildren(vfs, childId, depth + 1, maxDepth);
        }
        else {
          fileIds.add(childId);
        }

        //avg(fileName.length in monorepo) = 27:
        String childName = "name.%022d".formatted(childId % UNIQUE_FILE_NAMES_COUNT);
        int nameId = vfs.getNameId(childName);
        records.updateNameId(childId, nameId);

        childrenInfos.add(new ChildInfoImpl(childId, nameId, null, null, null));
      }
      ListResult children = new ListResult(vfs.getModCount(parentId), childrenInfos, parentId);
      VirtualFile parentFile = new LightVirtualFile();//only needed to provide symlink info, which is void in our case
      vfs.update(parentFile, parentId, currentChildren -> children, /*allChildrenCached: */true);
    }

    private int randomFileId(ThreadLocalRandom rnd) {
      int index = rnd.nextInt(0, fileIds.size());
      return fileIds.getInt(index);
    }
  }


  @Benchmark
  public String computePathIteratively(Context mainContext,
                                       FSRecordsContext vfsContext) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    FSRecordsImpl vfs = vfsContext.vfs();
    int fileId = mainContext.randomFileId(rnd);

    return computePathIteratively(vfs, fileId, mainContext.rootFolderId);
  }

  @Benchmark
  public String computePathIterativelyWithStringBuilder(Context mainContext,
                                                        FSRecordsContext vfsContext) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    FSRecordsImpl vfs = vfsContext.vfs();
    int fileId = mainContext.randomFileId(rnd);

    return computePathIterativelyWithStringBuilder(vfs, fileId, mainContext.rootFolderId);
  }

  @Benchmark
  public String computePathIterativelyWithStringBuilderAndThreadLocal(Context mainContext,
                                                                      FSRecordsContext vfsContext) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    FSRecordsImpl vfs = vfsContext.vfs();
    int fileId = mainContext.randomFileId(rnd);

    return computePathIterativelyWithStringBuilderAndThreadLocalList(vfs, fileId, mainContext.rootFolderId);
  }


  @Benchmark
  public String computePathRecursively(Context mainContext,
                                       FSRecordsContext vfsContext) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    FSRecordsImpl vfs = vfsContext.vfs();
    int fileId = mainContext.randomFileId(rnd);

    return computePathRecursively(vfs, fileId, mainContext.rootFolderId, 0).toString();
  }


  //========================= baselines: ===============================================================

  @Benchmark
  public int _baseline_VFS_getName_MAX_TREE_DEPTH_Times(Context mainContext,
                                                        FSRecordsContext vfsContext) throws Exception {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    FSRecordsImpl vfs = vfsContext.vfs();

    int fileId = mainContext.randomFileId(rnd);
    int hash = 0;
    for (int i = 0; i < mainContext.MAX_TREE_DEPTH; i++) {
      hash += vfs.getName(fileId).hashCode();
    }
    return hash;
  }

  @Benchmark
  public int _baseline_rnd_(Context mainContext) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return mainContext.randomFileId(rnd);
  }


  //The methods below are copied from VirtualFileSystemEntry, and re-factored to avoid PersistentFSImpl (because PersistentFSImpl
  // is too tightly coupled to the Application, which makes it impossible to use in JMH benchmarks because it can't be reliably
  // shut down after a benchmark), and instead to use FSRecordsImpl directly:

  private static StringBuilder computePathRecursively(FSRecordsImpl vfs,
                                                      int fileId,
                                                      int rootFileId,
                                                      int requiredBufferSize) {
    int parentId = vfs.getParent(fileId);
    if (parentId == rootFileId) {
      String rootPath = "/root/";
      return new StringBuilder(rootPath.length() + requiredBufferSize)
        .append(rootPath);
    }

    String fileName = vfs.getName(fileId);

    StringBuilder pathBuilder = computePathRecursively(
      vfs,
      parentId, rootFileId,
      requiredBufferSize + fileName.length() + 1
    );

    pathBuilder.append(fileName);
    if (requiredBufferSize > 0) { // requiredBufferSize=0 is the top calling frame, don't need trailing '/'
      pathBuilder.append('/');
    }
    return pathBuilder;
  }

  private static String computePathIteratively(FSRecordsImpl vfs,
                                               int fileId,
                                               int rootFolderId) {
    int length = 0;
    List<CharSequence> names = new ArrayList<>();
    for (; ; ) {
      int parentId = vfs.getParent(fileId);
      if (parentId == rootFolderId) {
        break;
      }

      String name = vfs.getName(fileId);
      if (length != 0) length++;
      length += name.length();
      names.add(name);

      fileId = parentId;
    }

    String rootPath = "/root/";
    int rootPathLength = rootPath.length();
    length += rootPathLength;

    char[] path = new char[length];
    int o = 0;
    CharArrayUtil.getChars(rootPath, path, o, rootPathLength);
    o += rootPathLength;
    for (int i = names.size() - 1; i >= 1; i--) {
      CharSequence name = names.get(i);
      int nameLength = name.length();
      CharArrayUtil.getChars(name, path, o, nameLength);
      o += nameLength;
      path[o++] = '/';
    }
    if (!names.isEmpty()) {
      CharSequence name = names.get(0);
      CharArrayUtil.getChars(name, path, o);
    }
    return new String(path);
  }

  private static String computePathIterativelyWithStringBuilder(FSRecordsImpl vfs,
                                                                int fileId,
                                                                int rootFolderId) {
    int length = 0;
    List<String> names = new ArrayList<>();
    for (; ; ) {
      int parentId = vfs.getParent(fileId);
      if (parentId == rootFolderId) {
        break;
      }

      String name = vfs.getName(fileId);
      if (length != 0) length++;
      length += name.length();
      names.add(name);

      fileId = parentId;
    }

    String rootPath = "/root/";
    length += rootPath.length();

    StringBuilder path = new StringBuilder(length);
    path.append(rootPath);
    for (int i = names.size() - 1; i >= 1; i--) {
      String name = names.get(i);
      path.append(name).append('/');
    }
    if (!names.isEmpty()) {
      String name = names.get(0);
      path.append(name);
    }
    return path.toString();
  }

  private static final ThreadLocal<ArrayList<String>> parentsNames = ThreadLocal.withInitial(ArrayList::new);

  private static String computePathIterativelyWithStringBuilderAndThreadLocalList(FSRecordsImpl vfs,
                                                                                  int fileId,
                                                                                  int rootFolderId) {
    List<String> names = parentsNames.get();
    try {
      int length = 0;
      for (; ; ) {
        int parentId = vfs.getParent(fileId);
        if (parentId == rootFolderId) {
          break;
        }

        String name = vfs.getName(fileId);
        if (length != 0) length++;
        length += name.length();
        names.add(name);

        fileId = parentId;
      }

      String rootPath = "/root/";
      length += rootPath.length();

      StringBuilder path = new StringBuilder(length);
      path.append(rootPath);
      for (int i = names.size() - 1; i >= 1; i--) {
        String name = names.get(i);
        path.append(name).append('/');
      }
      if (!names.isEmpty()) {
        String name = names.get(0);
        path.append(name);
      }
      return path.toString();
    }
    finally {
      names.clear();
    }
  }


  public static void main(final String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .jvmArgs(
        "-Dvfs.name-cache.check-names=false",
        "-Dvfs.name-cache.track-stats=false"
      )
      .threads(1)
      //.forks(0)
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      .include("\\W" + VFSComputePathBenchmark.class.getSimpleName() + "\\.*")//computePathRecursively\.*
      .build();

    new Runner(opt).run();
  }
}
