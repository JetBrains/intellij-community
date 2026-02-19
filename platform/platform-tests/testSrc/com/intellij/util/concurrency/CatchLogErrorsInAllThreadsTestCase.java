// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.TestLoggerFactory;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public abstract class CatchLogErrorsInAllThreadsTestCase extends TestCase {
  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  private final AtomicReference<Throwable> loggedError = new AtomicReference<>();

  @Override
  public void runBare() throws Throwable {
    LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
      @Override
      public @NotNull Set<Action> processError(@NotNull String category, @NotNull String message, String @NotNull [] details, @Nullable Throwable t) {
        if (t != null) {
          loggedError.compareAndSet(null, t);
        }
        return EnumSet.allOf(Action.class);//Action.ALL;
      }
    }, ()->super.runBare());
  }

  @Override
  protected void tearDown() throws Exception {
    //noinspection SSBasedInspection
    try {
      Throwable e = loggedError.getAndSet(null);
      if (e != null) {
        throw new AssertionError(e);
      }
    }
    finally {
      super.tearDown();
    }
  }
}
