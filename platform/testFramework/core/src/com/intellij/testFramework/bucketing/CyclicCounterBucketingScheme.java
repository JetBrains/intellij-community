// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.bucketing;

import com.intellij.TestCaseLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
@ApiStatus.Internal
public final class CyclicCounterBucketingScheme implements BucketingScheme {
  private final AtomicInteger myCyclicCounter = new AtomicInteger(0);
  private final HashMap<String, Integer> myBuckets = new HashMap<>();
  private final int myBucketsCount;
  private final int myCurrentBucketIndex;

  public CyclicCounterBucketingScheme() {
    this(TestCaseLoader.TEST_RUNNERS_COUNT, TestCaseLoader.TEST_RUNNER_INDEX);
  }

  public CyclicCounterBucketingScheme(int totalBucketsCount) {
    myBucketsCount = totalBucketsCount;
    myCurrentBucketIndex = TestCaseLoader.TEST_RUNNER_INDEX;
  }

  CyclicCounterBucketingScheme(int totalBucketsCount, int currentBucketIndex) {
    myBucketsCount = totalBucketsCount;
    myCurrentBucketIndex = currentBucketIndex;
  }

  @Override
  public boolean matchesCurrentBucket(@NotNull String testIdentifier) {
    return getBucketNumber(testIdentifier, myCurrentBucketIndex) == myCurrentBucketIndex;
  }

  public int getBucketNumber(@NotNull String testIdentifier, int currentBucketIndex) {
    var value = myBuckets.get(testIdentifier);

    if (value != null) {
      if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
        System.out.printf(
          "Fair bucket match: test identifier `%s` (already sieved to buckets), test index: %d, runner index/count: %d of %d, is matching bucket: %s%n",
          testIdentifier, value, currentBucketIndex, myBucketsCount, value == currentBucketIndex);
      }

      return value;
    }
    else {
      myBuckets.put(testIdentifier, myCyclicCounter.getAndIncrement() % myBucketsCount);
    }

    value = myBuckets.get(testIdentifier);

    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
      System.out.printf(
        "Fair bucket match: test identifier `%s`, test index: %d, runner index/count: %d of %d, is matching bucket: %s%n",
        testIdentifier, value, currentBucketIndex, myBucketsCount, value == currentBucketIndex);
    }

    return value;
  }

  /**
   * Init fair buckets for all test classes
   */
  @Override
  public void initialize() {
    if (!myBuckets.isEmpty()) return;

    System.out.println("Fair bucketing initialization started ...");
    long start = System.nanoTime();

    var testCaseClasses = TestCaseLoader.loadClassesForWarmup();
    Collections.shuffle(testCaseClasses, new Random(TestCaseLoader.TEST_RUNNERS_COUNT));

    testCaseClasses.forEach(testCaseClass -> getBucketNumber(testCaseClass.getName(), TestCaseLoader.TEST_RUNNER_INDEX));

    long durationNs = System.nanoTime() - start;
    System.out.printf("Fair bucketing initialization finished in %d ms.%n", durationNs / 1_000_000);
  }
}
