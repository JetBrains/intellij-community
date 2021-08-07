// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.apache.log4j.Level;
import org.apache.log4j.Priority;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class MutedLoggerTest extends BareTestFixtureTestCase {
  private static final int FREQUENCY = 5;

  @BeforeClass
  public static void setFrequency() {
    IdeaLogger.setMutedExceptionFrequency(String.valueOf(FREQUENCY));
  }

  @AfterClass
  public static void resetFrequency() {
    IdeaLogger.setMutedExceptionFrequency("");
  }

  @After
  public void dropCaches() {
    IdeaLogger.dropFrequentExceptionsCaches();
  }

  @Test
  public void testDoesNotLogErrorTwice() {
    List<Pair<String, Throwable>> errors = ContainerUtil.createConcurrentList();
    List<Pair<String, Throwable>> warns = ContainerUtil.createConcurrentList();
    Throwable t = new Throwable();
    Logger logger = getDelegate(errors, warns);

    logger.error(t);
    logger.error(t);

    assertEquals("Too many errors posted: " + errors, 1, errors.size());
    assertEquals("Too many errors posted: " + warns, 0, warns.size());

    Pair<String, Throwable> firstError = errors.get(0);
    assertSame("Second error doesn't contain throwable: " + firstError, t, firstError.second);
  }
  @Test
  public void testDoesNotLogWarnTwice() {
    List<Pair<String, Throwable>> errors = ContainerUtil.createConcurrentList();
    List<Pair<String, Throwable>> warns = ContainerUtil.createConcurrentList();
    Throwable t = new Throwable();
    Logger logger = getDelegate(errors, warns);

    logger.warn(t);
    logger.warn(t);

    assertEquals("Too many errors posted: " + errors, 0, errors.size());
    assertEquals("Too many errors posted: " + warns, 1, warns.size());

    Pair<String, Throwable> firstError = warns.get(0);
    assertSame("Second error doesn't contain throwable: " + firstError, t, firstError.second);
  }

  @Test
  public void testErrorLogsRecurringException() {
    List<Pair<String, Throwable>> errors = ContainerUtil.createConcurrentList();
    List<Pair<String, Throwable>> warns = ContainerUtil.createConcurrentList();
    Throwable t = new Throwable();
    Logger logger = getDelegate(errors, warns);

    for (int i = 0; i < FREQUENCY; i++) {
      logger.error(t);
    }

    // exception, "exception was reported x times" message
    assertEquals("Too many errors posted: " + errors, 2, errors.size());
    UsefulTestCase.assertEmpty(warns);

    Pair<String, Throwable> firstError = errors.get(0);
    assertSame("Second error doesn't contain throwable: " + firstError, t, firstError.second);

    Pair<String, Throwable> thirdError = errors.get(1);
    assertEquals("Third error doesn't contain message and occurrences: " + thirdError, thirdError.first,
                 IdeaLogger.getExceptionWasAlreadyReportedNTimesMessage(t, FREQUENCY));
    assertNull("Third error contains throwable: " + thirdError, thirdError.second);
  }

  @Test
  public void testWarningLogsRecurringException() {
    List<Pair<String, Throwable>> errors = ContainerUtil.createConcurrentList();
    List<Pair<String, Throwable>> warns = ContainerUtil.createConcurrentList();
    Throwable t = new Throwable();
    Logger logger = getDelegate(errors, warns);

    for (int i = 0; i < FREQUENCY; i++) {
      logger.warn(t);
    }

    // exception, "exception was reported x times" message
    UsefulTestCase.assertEmpty(errors);
    assertEquals("Too many warns posted: " + warns, 2, warns.size());

    Pair<String, Throwable> firstError = warns.get(0);
    assertSame("Second error doesn't contain throwable: " + firstError, t, firstError.second);

    Pair<String, Throwable> thirdError = warns.get(1);
    assertEquals("Third error doesn't contain message and occurrences: " + thirdError, thirdError.first,
                 IdeaLogger.getExceptionWasAlreadyReportedNTimesMessage(t, FREQUENCY));
    assertNull("Third error contains throwable: " + thirdError, thirdError.second);
  }

  @NotNull
  private static Logger getDelegate(@NotNull List<? super Pair<String, Throwable>> errors, @NotNull List<? super Pair<String, Throwable>> warns) {
    org.apache.log4j.Logger log4j = new org.apache.log4j.Logger("") {
      @Override
      public void error(Object message, Throwable t) {
        errors.add(Pair.create(String.valueOf(message), t));
      }

      @Override
      public void error(Object message) {
        // ignore error header
      }

      @Override
      public void warn(Object message, Throwable t) {
        warns.add(Pair.create(String.valueOf(message), t));
      }

      @Override
      public void warn(Object message) {
        // ignore warn header
      }

      @Override
      public void log(Priority priority, Object message) {
        if (priority == Level.ERROR) error(message, null);
        else if (priority == Level.WARN) warn(message, null);
        else throw new IllegalArgumentException(priority.toString());
      }
    };
    log4j.setLevel(Level.WARN);
    IdeaLogger logger = new IdeaLogger(log4j);
    assertTrue(IdeaLogger.isMutingFrequentExceptionsEnabled());
    assertEquals(FREQUENCY, logger.REPORT_EVERY_NTH_FREQUENT_EXCEPTION);
    return logger;
  }
}
