/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.IdeaTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import javax.swing.*;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author peter
 */
public class FileNameCacheMicroBenchmark {

  public static void main(String[] args) throws Exception {
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        try {
          IdeaTestFixture fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR).getFixture();
          fixture.setUp();

          runTest(200);
          runTest(50000);

          fixture.tearDown();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });

    System.exit(0);
  }

  private static void runTest(int nameCount) throws InterruptedException, ExecutionException {
    System.out.println("-----------");
    System.out.println("nameCount = " + nameCount);

    TIntObjectHashMap<CharSequence> map = generateNames(nameCount);
    final int[] ids = map.keys();
    checkNames(map, ids);
    warmUp(ids);

    measureAverageTime(ids, 1);
    measureAverageTime(ids, 3);
  }

  private static boolean warmedUp;
  private static void warmUp(int[] ids) throws InterruptedException, ExecutionException {
    if (warmedUp) return;
    for (int i = 0; i < 200000; i++) {
      runThreads(ids, 2, 1000);
    }

    Thread.sleep(10000);
    System.out.println("Warmup complete");
    warmedUp = true;
  }

  private static void measureAverageTime(int[] ids, int threadCount) throws InterruptedException, ExecutionException {
    TLongArrayList times = new TLongArrayList();
    for (int i = 0; i < 11; i++) {
      long time = runThreads(ids, threadCount, 20000000);
      System.out.println(time);
      times.add(time);
    }

    times.sort();
    System.out.println("Median for " + threadCount + " threads: " + times.get(times.size() / 2));
    System.out.println();
  }

  private static long runThreads(final int[] ids, int threadCount, final int queryCount) throws InterruptedException, ExecutionException {
    long start = System.currentTimeMillis();
    List<Future<?>> futures = ContainerUtil.newArrayList();
    Random seedRandom = new Random();
    for (int i = 0; i < threadCount; i++) {
      final Random threadRandom = new Random(seedRandom.nextInt());
      futures.add(ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          for (int j = 0; j < queryCount; j++) {
            FileNameCache.getVFileName(ids[threadRandom.nextInt(ids.length)]);
          }
        }
      }));
    }
    for (Future<?> future : futures) {
      future.get();
    }
    return System.currentTimeMillis() - start;
  }

  private static void checkNames(TIntObjectHashMap<CharSequence> map, int[] ids) {
    for (int id : ids) {
      Assert.assertEquals(map.get(id), FileNameCache.getVFileName(id).toString());
    }
  }

  @NotNull
  private static TIntObjectHashMap<CharSequence> generateNames(int nameCount) {
    Random random = new Random();
    TIntObjectHashMap<CharSequence> map = new TIntObjectHashMap<CharSequence>();
    for (int i = 0; i < nameCount; i++) {
      String name = "some_name_" + random.nextInt() + StringUtil.repeat("a", random.nextInt(10));
      int id = FileNameCache.storeName(name);
      map.put(id, name);
    }
    return map;
  }
}