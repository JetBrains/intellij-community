package com.intellij.util;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class StringBuilderSpinAllocatorTest {

  private final String[] myStrings = new String[]{
    "First String is the smallest",
    "Second String is definitely larger than the first one",
    "Third String is a bit larger than the first one",
    "Fourth String is the largest amongst all the myStrings. Congrats! It must be even larger than it is."
  };

  @Before
  public void setUp() throws Exception {
    // warm-up
    new StringBuilder();
    StringBuilderSpinAllocator.dispose(StringBuilderSpinAllocator.alloc());
  }

  @Test
  public void testPerformance() throws InterruptedException {
    StringBuilder builder;
    final int count = 1000000;

    long start = System.nanoTime();
    for (int i = 0; i < count; ++i) {
      builder = new StringBuilder();
      builder.append(myStrings[i & 3]);
      builder.append(builder.toString());
    }
    final long regularTime = (System.nanoTime() - start)/1000;
    System.out.println("StringBuilder regular allocations took: " + regularTime);

    System.gc();
    System.runFinalization();
    Thread.sleep(2000);

    start = System.nanoTime();
    for (int i = 0; i < count; ++i) {
      builder = StringBuilderSpinAllocator.alloc();
      builder.append(myStrings[i & 3]);
      builder.append(builder.toString());
      StringBuilderSpinAllocator.dispose(builder);
    }
    final long spinTime = (System.nanoTime() - start)/1000;
    System.out.println("StringBuilder spin allocations took: " + spinTime);

    if (!com.intellij.testFramework.PlatformTestUtil.COVERAGE_ENABLED_BUILD) {
      assertTrue("regular:" + regularTime + "mks, spin:" + spinTime + "mks", spinTime < regularTime);
    }
  }
}
