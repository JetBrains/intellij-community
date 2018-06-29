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
package com.intellij.util;

import com.intellij.concurrency.JobLauncher;
import com.intellij.testFramework.LightPlatformTestCase;
import org.junit.Assert;

import java.util.Collections;
import java.util.Random;

public class StringBuilderSpinAllocatorTester {

  public static void main(String[] args) {
    LightPlatformTestCase.initApplication(); 
    testSequentialPerformance();
    testConcurrentPerformance();
    System.exit(0);
  }

  public static final int THREADS = 1000;

  private static void testSequentialPerformance() {
    for (int i=0; i<10; i++) {
      long spinTime = time(count, spinAlloc);
      long regularTime = time(count, regularAlloc);
      System.out.println("regular: " + regularTime + "; spin :" +spinTime+"; ratio: "+(10*spinTime/regularTime)/10.0+" times");
    }
  }
  private static void testConcurrentPerformance() {
    for (int i=0; i<10; i++) {
      long spinTime = concurrentTime(count/THREADS, spinAlloc);
      long regularTime = concurrentTime(count/THREADS, regularAlloc);
      System.out.println("concurrent regular: " + regularTime + "; spin :" +spinTime+"; ratio: "+(10*spinTime/regularTime)/10.0+" times");
    }
  }

  private static long concurrentTime(int count, final Runnable action) {
    return time(count, () -> {
      boolean ok =
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Collections.nCopies(THREADS, null), null, o -> {
          action.run();
          return true;
        });

      Assert.assertTrue(ok);
    });
  }

  static final int count = 100000;
  static final int iter = 1000;
  static Runnable spinAlloc = new Runnable() {
    @SuppressWarnings("deprecation")
    @Override
    public void run() {
      for (int i = 0; i < iter; ++i) {
        StringBuilder builder = null;
        try {
          builder = StringBuilderSpinAllocator.alloc();
          if (randomField == 0x78962343) {
            System.out.println("xxx"+builder);
          }
        }
        finally {
          StringBuilderSpinAllocator.dispose(builder);
        }
      }
    }
  };
  static Runnable regularAlloc = new Runnable() {
    @SuppressWarnings("MismatchedQueryAndUpdateOfStringBuilder")
    @Override
    public void run() {
      for (int i = 0; i < iter; ++i) {
        StringBuilder builder = new StringBuilder();
        if (randomField == 0x78962343) {
          System.out.println("xxx"+builder);
        }
      }
    }
  };
  private static volatile int randomField = new Random().nextInt();

  private static long time(int count, final Runnable action) {
    long start = System.nanoTime();
    for (int i = 0; i < count; ++i) {
      action.run();
    }
    long spinTime = (System.nanoTime() - start) / 1000;
    randomField++;
    return spinTime;
  }
}
