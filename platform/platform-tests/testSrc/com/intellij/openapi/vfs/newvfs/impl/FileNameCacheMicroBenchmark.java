// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.IdeaTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author peter
 */
public class FileNameCacheMicroBenchmark {
  public static void main(String[] args) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        IdeaTestFixture fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR,
                                                                                                       "FileNameCacheMicroBenchmark").getFixture();
        fixture.setUp();
        long start = System.currentTimeMillis();
        runTest(200, "All names in cache");
        runTest(50000, "Cache almost overflows");
        runTest(120000, "Cache certain overflow");
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Total elapsed: " + elapsed/1000.0 +"s");

        fixture.tearDown();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    System.exit(0);
  }

  private static final TestIteration LONG_RANDOM_ACCESS = new TestIteration() {
    @Override
    public void doTest(int threadNumber, int[] ids, Random threadRandom, int queryCount) {
      final int blackHole = threadRandom.nextInt();

      for (int j = 0; j < queryCount; j++) {
        final CharSequence name = FileNameCache.getVFileName(ids[threadRandom.nextInt(ids.length)]);

        if (blackHole == name.hashCode() && blackHole+1 == name.hashCode()) {
          failure();
        }
      }
    }

    @Override
    public String toString() {
      return "random access";
    }
  };

  private static final TestIteration LONG_RANDOM_ACCESS_WITH_GET_PATH = new TestIteration() {
    @Override
    public void doTest(int threadNumber, int[] ids, Random threadRandom, int queryCount) {
      final int blackHole = threadRandom.nextInt();

      for (int j = 0; j < queryCount; j++) {
        final int hash = getPath(threadRandom.nextInt(ids.length), ids);

        if (blackHole == hash) {
          failure();
        }
      }
    }

    @Override
    public String toString() {
      return "random access + getPath";
    }
  };

  private static final TestIteration LINEAR_SCAN_AND_RANDOM_ACCESS_WITH_GET_PATH = new TestIteration() {
    @Override
    public void doTest(int threadNumber, int[] ids, Random threadRandom, int queryCount) {
      if (threadNumber % 2 == 1) { // linear scan for every second_case
        final int blackHole = threadRandom.nextInt();
        int currentId = 0;

        for(int j = 0; j < queryCount; ++j) {
          final int hash = getPath(currentId++, ids);
          if (currentId == ids.length) currentId = 0;
          if (blackHole == hash) {
            failure();
          }
        }

        return;
      }

      LONG_RANDOM_ACCESS_WITH_GET_PATH.doTest(threadNumber, ids, threadRandom, queryCount);
    }

    @Override
    public String toString() {
      return "linear scan + random access + getPath";
    }
  };

  private static void runTest(int nameCount, String name) throws InterruptedException, ExecutionException {
    System.out.println("----- " + name + " ------ name count: "+nameCount);

    Int2ObjectMap<CharSequence> map = generateNames(nameCount);
    final int[] ids = map.keySet().toIntArray();
    checkNames(map, ids);
    warmUp(ids);

    measureAverageTime(ids, 1, LONG_RANDOM_ACCESS);
    measureAverageTime(ids, 4, LONG_RANDOM_ACCESS);

    measureAverageTime(ids, 1, LONG_RANDOM_ACCESS_WITH_GET_PATH);
    measureAverageTime(ids, 4, LONG_RANDOM_ACCESS_WITH_GET_PATH);

    measureAverageTime(ids, 1, LINEAR_SCAN_AND_RANDOM_ACCESS_WITH_GET_PATH);
    measureAverageTime(ids, 4, LINEAR_SCAN_AND_RANDOM_ACCESS_WITH_GET_PATH);
  }

  private static boolean warmedUp;
  private static void warmUp(int[] ids) throws InterruptedException, ExecutionException {
    if (warmedUp) return;
    System.out.println("Warming up");
    for (int i = 0; i < 200000; i++) {
      runThreads(ids, 2, 1000, LONG_RANDOM_ACCESS);
    }

    Thread.sleep(10000);
    System.out.println("Warmup complete");
    warmedUp = true;
  }

  private static int getPath(int id, int[] ids) {
    int result = 0;

    while (id > 0) {
      result += FileNameCache.getVFileName(ids[id]).hashCode();
      id /= 10;
    }
    return result;
  }

  private static void measureAverageTime(int[] ids, int threadCount, TestIteration iteration) throws InterruptedException, ExecutionException {
    System.out.println("Running "+threadCount+" threads, using "+iteration);
    LongArrayList times = new LongArrayList();
    for (int i = 0; i < 10; i++) {
      long time = runThreads(ids, threadCount, 2000000/*0*/, iteration);
      System.out.println(time);
      times.add(time);
    }

    times.sort(null);
    long median = times.getLong(times.size() / 2);
    System.out.println("Median for " + threadCount + " threads: " + median+"ms");
    System.out.println();
  }

  private abstract static class TestIteration {
    abstract void doTest(int threadNumber, int[] ids, Random threadRandom, int queryCount);
    static void failure() {
      System.err.println("Failure");
      assert false;
    }
  }

  private static long runThreads(final int[] ids, int threadCount, final int queryCount, final TestIteration testIteration) throws InterruptedException, ExecutionException {
    long start = System.currentTimeMillis();
    List<Future<?>> futures = new ArrayList<>();
    Random seedRandom = new Random();
    for (int i = 0; i < threadCount; i++) {
      final Random threadRandom = new Random(seedRandom.nextInt());
      final int finalI = i;
      futures.add(ApplicationManager.getApplication().executeOnPooledThread(
        () -> testIteration.doTest(finalI, ids, threadRandom, queryCount)));
    }
    for (Future<?> future : futures) {
      future.get();
    }
    return System.currentTimeMillis() - start;
  }

  private static void checkNames(Int2ObjectMap<CharSequence> map, int[] ids) {
    for (int id : ids) {
      Assert.assertEquals(map.get(id), FileNameCache.getVFileName(id).toString());
    }
  }

  @NotNull
  private static Int2ObjectMap<CharSequence> generateNames(int nameCount) {
    Random random = new Random();
    Int2ObjectMap<CharSequence> map = new Int2ObjectOpenHashMap<>();
    for (int i = 0; i < nameCount; i++) {
      String name = "some_name_" + random.nextInt() + StringUtil.repeat("a", random.nextInt(10));
      int id = FileNameCache.storeName(name);
      map.put(id, name);
    }
    return map;
  }
}