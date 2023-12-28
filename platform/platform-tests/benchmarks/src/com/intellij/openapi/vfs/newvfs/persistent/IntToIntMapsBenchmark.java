// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleHashMap;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import com.intellij.util.io.AbstractIntToIntBtree;
import com.intellij.util.io.IntToIntBtree;
import com.intellij.util.io.IntToIntBtreeLockFree;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Compare persistent [int->int] map implementations: BTree-based vs ExtendibleHashMap based on memory-mapped files
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(1)
public class IntToIntMapsBenchmark {

  private static final int TREE_PAGE_SIZE = 32_768;

  private static final int TOTAL_KEYS = 1 << 23;

  private static final int SAMPLES = 16;


  @State(Scope.Benchmark)
  public static class BTreeContext {
    @Param({"legacy", "lock-free"})
    public String btreeImplementation;

    public File file;

    public AbstractIntToIntBtree bTree;

    public StorageLockContext lockContext = new StorageLockContext(true, true, true);

    public Int2IntOpenHashMap generatedKeyValues;
    public int[] generatedKeys;

    public int[] result = {0};

    @Setup
    public void setup() throws Exception {
      file = FileUtil.createTempFile("IntToIntBtree", "tst", /*deleteOnExit: */ true);
      if (btreeImplementation.equals("legacy")) {
        bTree = new IntToIntBtree(
          TREE_PAGE_SIZE,
          file.toPath(),
          lockContext,
          /*createAnew: */ true
        );
      }
      else if (btreeImplementation.equals("lock-free")) {
        bTree = new IntToIntBtreeLockFree(
          TREE_PAGE_SIZE,
          file.toPath(),
          lockContext,
          /*createAnew: */ true, new PageContentLockingStrategy.SharedLockLockingStrategy()
        );
      }
      else {
        throw new IllegalStateException(
          "Unrecognized btreeImplementation(=" + btreeImplementation + ") -- must be 'legacy' or 'lock-free'");
      }


      generatedKeyValues = generateKeyValues(TOTAL_KEYS);
      for (Map.Entry<Integer, Integer> e : generatedKeyValues.int2IntEntrySet()) {
        bTree.put(e.getKey(), e.getValue());
      }
      generatedKeys = generatedKeyValues.keySet().toIntArray();
    }

    @TearDown
    public void tearDown() throws Exception {
      if (bTree != null) {
        bTree.doClose();
      }
      if (file != null) {
        file.delete();
      }
    }
  }

  @State(Scope.Benchmark)
  public static class ExtendibleHashMapContext {

    public File file;

    public ExtendibleHashMap map;

    public Int2IntOpenHashMap generatedKeyValues;
    public int[] generatedKeys;

    @Setup
    public void setup() throws Exception {
      file = FileUtil.createTempFile("IntToIntBtree", "tst", /*deleteOnExit: */ true);
      map = ExtendibleMapFactory.largeSize().open(file.toPath());


      generatedKeyValues = generateKeyValues(TOTAL_KEYS);
      for (Map.Entry<Integer, Integer> e : generatedKeyValues.int2IntEntrySet()) {
        map.put(e.getKey(), e.getValue());
      }
      generatedKeys = generatedKeyValues.keySet().toIntArray();
    }

    @TearDown
    public void tearDown() throws Exception {
      if (map != null) {
        map.close();
      }
      if (file != null) {
        file.delete();
      }
    }
  }

  private static Int2IntOpenHashMap generateKeyValues(int keysCount) {
    final Int2IntOpenHashMap keyValues = new Int2IntOpenHashMap(keysCount);
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < keysCount; i++) {
      final int key = rnd.nextInt();
      final int value = rnd.nextInt();
      keyValues.put(key, value);
    }
    return keyValues;
  }


  @Benchmark
  @OperationsPerInvocation(SAMPLES)
  public void lookupRandomExistentKey_BTree(BTreeContext context) throws IOException {
    int[] keys = context.generatedKeys;
    int[] result = context.result;
    AbstractIntToIntBtree bTree = context.bTree;
    ThreadLocalRandom rnd = ThreadLocalRandom.current();

    for (int i = 0; i < SAMPLES; i++) {
      int index = rnd.nextInt(keys.length);
      int key = keys[index];
      bTree.get(key, result);
    }
  }

  @Benchmark
  @OperationsPerInvocation(SAMPLES)
  public void updateRandomExistingKey_BTree(BTreeContext context) throws IOException {
    int[] keys = context.generatedKeys;
    AbstractIntToIntBtree bTree = context.bTree;
    ThreadLocalRandom rnd = ThreadLocalRandom.current();

    for (int i = 0; i < SAMPLES; i++) {
      int index = rnd.nextInt(keys.length);
      int key = keys[index];
      bTree.put(key, key);
    }
  }


  @Benchmark
  @OperationsPerInvocation(SAMPLES)
  public void lookupRandomExistentKey_EMap(ExtendibleHashMapContext context) throws IOException {
    int[] keys = context.generatedKeys;
    ExtendibleHashMap map = context.map;
    ThreadLocalRandom rnd = ThreadLocalRandom.current();

    for (int i = 0; i < SAMPLES; i++) {
      int index = rnd.nextInt(keys.length);
      int key = keys[index];
      map.lookup(key, value -> true);
    }
  }

  @Benchmark
  @OperationsPerInvocation(SAMPLES)
  public void updateRandomExistingKey_EMap(ExtendibleHashMapContext context) throws IOException {
    int[] keys = context.generatedKeys;
    ExtendibleHashMap map = context.map;
    ThreadLocalRandom rnd = ThreadLocalRandom.current();

    for (int i = 0; i < SAMPLES; i++) {
      int index = rnd.nextInt(keys.length);
      int key = keys[index];
      map.put(key, key);
    }
  }


  public static void main(String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .mode(Mode.SampleTime)
      .include(IntToIntMapsBenchmark.class.getSimpleName() + ".*")
      .threads(1)
      .build();

    new Runner(opt).run();
  }
}
