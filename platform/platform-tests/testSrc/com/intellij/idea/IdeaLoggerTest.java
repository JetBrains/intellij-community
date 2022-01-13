// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import org.apache.log4j.Level;
import org.apache.log4j.Priority;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

public class IdeaLoggerTest extends BareTestFixtureTestCase {
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

  private static void log(Logger logger, Priority priority, String message, Throwable t) {
    if (priority == Level.DEBUG) {
      logger.debug(message, t);
    }
    else if (priority == Level.WARN) {
      logger.warn(message, t);
    }
    else if (priority == Level.ERROR) {
      logger.error(message, t);
    }
  }

  @Test
  public void testExceptionDoesNotGetLoggedTwice() {
    for (Level level : new Level[]{Level.ERROR, Level.WARN, Level.DEBUG}) {
      Map<Level, List<Pair<String, Throwable>>> diags = new ConcurrentHashMap<>();
      Throwable t = new Throwable(level.toString());
      Logger logger = getDelegate(diags);

      log(logger, level, null, t);
      log(logger, level, null, t);

      assertEquals("Too many " + level + " posted: " + diags, 1, diags.size());
      assertEquals("Too many " + level + " posted: " + diags, 1, diags.get(level).size());

      Pair<String, Throwable> first = diags.get(level).get(0);
      assertSame("Second error doesn't contain throwable: " + first, t, first.second);
    }
  }

  @Test
  public void testManyExceptionsInLogAreAccompaniedByTooManyExceptionsMessage() {
    for (Level level : new Level[]{Level.ERROR, Level.WARN, Level.DEBUG}) {
      Map<Level, List<Pair<String, Throwable>>> diags = new ConcurrentHashMap<>();
      Throwable t = new Throwable(level.toString());
      Logger logger = getDelegate(diags);

      for (int i = 0; i < FREQUENCY; i++) {
        log(logger, level, null, t);
      }

      // exception, "exception was reported x times" message
      List<Pair<String, Throwable>> diag = diags.get(level);
      assertEquals("Too many " + level + " posted: " + diags, 2, diag.size());
      assertEquals("Too many " + level + " posted: " + diags, 1, diags.size());

      Pair<String, Throwable> first = diag.get(0);
      assertSame("Second error doesn't contain throwable: " + first, t, first.second);

      Pair<String, Throwable> third = diag.get(1);
      assertEquals("Third error doesn't contain message and occurrences: " + third, third.first,
                   IdeaLogger.getExceptionWasAlreadyReportedNTimesMessage(t, FREQUENCY));
      assertNull("Third error contains throwable: " + third, third.second);
    }
  }

  @NotNull
  private static Logger getDelegate(@NotNull Map<? super Level, List<Pair<String, Throwable>>> diags) {
    org.apache.log4j.Logger log4j = new org.apache.log4j.Logger("") {
      @Override
      public void error(Object message, Throwable t) {
        diags.computeIfAbsent(Level.ERROR, __->new ArrayList<>()).add(Pair.create(String.valueOf(message), t));
      }

      @Override
      public void error(Object message) {
        // ignore error header
      }

      @Override
      public void warn(Object message, Throwable t) {
        diags.computeIfAbsent(Level.WARN, __->new ArrayList<>()).add(Pair.create(String.valueOf(message), t));
      }

      @Override
      public void warn(Object message) {
        // ignore warn header
      }

      @Override
      public void debug(Object message) {
        //
      }

      @Override
      public void debug(Object message, Throwable t) {
        diags.computeIfAbsent(Level.DEBUG, __->new ArrayList<>()).add(Pair.create(String.valueOf(message), t));
      }

      @Override
      public void log(Priority priority, Object message) {
        if (priority == Level.ERROR) error(message, null);
        else if (priority == Level.WARN) warn(message, null);
        else if (priority == Level.DEBUG) debug(message, null);
        else throw new IllegalArgumentException(priority.toString());
      }
    };
    log4j.setLevel(Level.DEBUG);
    IdeaLogger logger = new IdeaLogger(log4j);
    assertTrue(IdeaLogger.isMutingFrequentExceptionsEnabled());
    assertEquals(FREQUENCY, logger.REPORT_EVERY_NTH_FREQUENT_EXCEPTION);
    return logger;
  }
}
