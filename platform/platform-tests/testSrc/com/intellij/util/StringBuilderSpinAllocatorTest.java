/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class StringBuilderSpinAllocatorTest {
  private final String[] myStrings = new String[]{
    "First String is the smallest",
    "Second String is definitely larger than the first one",
    "Third String is a bit larger than the first one",
    "Fourth String is the largest amongst all the myStrings. Congrats! It must be even larger than it is."
  };

  @Test
  public void testPerformance() {
    assumeTrue(!com.intellij.testFramework.PlatformTestUtil.COVERAGE_ENABLED_BUILD);
    doTest(true);
    doTest(false);
  }

  private void doTest(boolean warmUp) {
    StringBuilder builder;
    int count = warmUp ? 1000 : 1000000;

    System.gc();
    System.runFinalization();
    TimeoutUtil.sleep(1000);

    long start = System.nanoTime();
    for (int i = 0; i < count; ++i) {
      builder = new StringBuilder();
      builder.append(myStrings[i & 3]);
      builder.append(builder.toString());
    }
    long regularTime = (System.nanoTime() - start) / 1000;

    System.gc();
    System.runFinalization();
    TimeoutUtil.sleep(1000);

    start = System.nanoTime();
    for (int i = 0; i < count; ++i) {
      builder = StringBuilderSpinAllocator.alloc();
      builder.append(myStrings[i & 3]);
      builder.append(builder.toString());
      StringBuilderSpinAllocator.dispose(builder);
    }
    long spinTime = (System.nanoTime() - start) / 1000;

    if (!warmUp) {
      System.out.println("StringBuilder regular allocations took: " + regularTime);
      System.out.println("StringBuilder spin allocations took: " + spinTime);
      assertTrue("regular:" + regularTime + "mks, spin:" + spinTime + "mks", spinTime < regularTime);
    }
  }
}
