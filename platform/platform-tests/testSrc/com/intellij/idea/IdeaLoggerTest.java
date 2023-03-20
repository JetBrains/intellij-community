// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static org.junit.Assert.*;

public class IdeaLoggerTest extends BareTestFixtureTestCase {
  @After
  public void dropCaches() {
    IdeaLogger.dropFrequentExceptionsCaches();
  }

  private static void log(Logger logger, Level priority, String message, Throwable t) {
    if (priority == Level.WARNING) {
      logger.warn(message, t);
    }
    else if (priority == Level.SEVERE) {
      logger.error(message, t);
    }
  }

  @Test
  public void testExceptionDoesNotGetLoggedTwice() {
    for (Level level : new Level[]{Level.SEVERE, Level.WARNING}) {
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

  @NotNull
  private static Logger getDelegate(@NotNull Map<? super Level, List<Pair<String, Throwable>>> diags) {
    java.util.logging.Logger julLogger = new java.util.logging.Logger("", null) {
      @Override
      public void log(java.util.logging.Level level, String msg, Throwable thrown) {
        if (thrown != null) {
          diags.computeIfAbsent(level, __ -> new ArrayList<>()).add(Pair.create(String.valueOf(msg), thrown));
        }
      }
    };
    julLogger.setLevel(Level.FINE);
    IdeaLogger logger = new IdeaLogger(julLogger);
    assertTrue(IdeaLogger.isMutingFrequentExceptionsEnabled());
    return logger;
  }
}
