// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class MutedLoggerTest extends BareTestFixtureTestCase {
  private static final Integer FREQUENCY = 5;
  private static final String HASH_PATTERN = ".* '-?\\d+'";

  @NotNull
  private static String getFrequencyPattern(int frequency) {
    return HASH_PATTERN + ".* " + frequency + " .*";
  }

  @BeforeClass
  public static void setFrequency() {
    System.setProperty("ide.muted.error.logger.frequency", FREQUENCY.toString());
  }

  @AfterClass
  public static void resetFrequency() {
    System.clearProperty("ide.muted.error.logger.frequency");
  }

  @After
  public void dropCaches() {
    MutedLogger.dropCaches();
  }

  @Test
  public void testDoesNotLogExceptionTwice() {
    List<Pair<String, Throwable>> errors = ContainerUtil.createConcurrentList();
    Throwable t = new Throwable();
    Logger logger = MutedLogger.of(getDelegate(errors));

    logger.error(t);
    logger.error(t);

    assertEquals("Too many errors posted: " + errors, 2, errors.size());

    Pair<String, Throwable> firstError = errors.get(0);
    assertTrue("First error doesn't contain hash: " + firstError, firstError.first.matches(HASH_PATTERN + ": " + t));
    assertNull("First error contains throwable: " + firstError, firstError.second);

    Pair<String, Throwable> secondError = errors.get(1);
    assertEquals("Second error doesn't contain throwable: " + secondError, t, secondError.second);
  }

  @Test
  public void testLogsRecurringExceptionHash() {
    List<Pair<String, Throwable>> errors = ContainerUtil.createConcurrentList();
    Throwable t = new Throwable();
    Logger logger = MutedLogger.of(getDelegate(errors));

    for (int i = 0; i < FREQUENCY; i++) {
      logger.error(t);
    }

    assertEquals("Too many errors posted: " + errors, 3, errors.size());

    Pair<String, Throwable> firstError = errors.get(0);
    assertTrue("First error doesn't contain hash: " + firstError, firstError.first.matches(HASH_PATTERN + ": " + t));
    assertNull("First error contains throwable: " + firstError, firstError.second);

    Pair<String, Throwable> secondError = errors.get(1);
    assertEquals("Second error doesn't contain throwable: " + secondError, t, secondError.second);

    Pair<String, Throwable> thirdError = errors.get(2);
    assertTrue("Third error doesn't contain hash and occurrences: " + thirdError, thirdError.first.matches(getFrequencyPattern(FREQUENCY)));
    assertNull("Third error contains throwable: " + thirdError, thirdError.second);
  }

  @Test
  public void testLogsResultsOnDropCaches() throws InterruptedException {
    List<Pair<String, Throwable>> errors = ContainerUtil.createConcurrentList();
    Throwable t = new Throwable();
    Logger logger = MutedLogger.of(getDelegate(errors));

    logger.error(t);
    logger.error(t);
    MutedLogger.dropCaches();

    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
    while (errors.size() != 3 && System.nanoTime() < deadline) TimeUnit.MILLISECONDS.sleep(1);  // cache maintenance is performed on pooled thread

    assertEquals("Too many errors posted: " + errors, 3, errors.size());

    Pair<String, Throwable> firstError = errors.get(0);
    assertTrue("First error doesn't contain hash: " + firstError, firstError.first.matches(HASH_PATTERN + ": " + t));
    assertNull("First error contains throwable: " + firstError, firstError.second);

    Pair<String, Throwable> secondError = errors.get(1);
    assertEquals("Second error doesn't contain throwable: " + secondError, t, secondError.second);

    Pair<String, Throwable> thirdError = errors.get(2);
    assertTrue("Third error doesn't contain hash and occurrences: " + thirdError, thirdError.first.matches(getFrequencyPattern(2)));
    assertNull("Third error contains throwable: " + thirdError, thirdError.second);
  }

  @NotNull
  private static Logger getDelegate(@NotNull List<Pair<String, Throwable>> errors) {
    return new DefaultLogger("") {
      @Override
      public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
        errors.add(Pair.create(message, t));
      }
    };
  }
}
