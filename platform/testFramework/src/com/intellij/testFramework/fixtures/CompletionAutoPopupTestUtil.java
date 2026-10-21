// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Completion auto-popup helpers for a JUnit 5 test.
 * This is the replacement of {@link CompletionAutoPopupTestCase}.
 */
@TestOnly
public final class CompletionAutoPopupTestUtil {
  private CompletionAutoPopupTestUtil() {
  }

  /**
   * Runs {@code runnable} with the completion auto-popup enabled, as {@link CompletionAutoPopupTestCase} ran each test.
   * Put the whole test body in {@code runnable}.
   * Call it from a thread that is not the EDT.
   */
  public static <E extends Throwable> void runWithAutoPopupEnabled(@NotNull CodeInsightTestFixture fixture,
                                                                   @NotNull ThrowableRunnable<E> runnable) throws E {
    try {
      new CompletionAutoPopupTester(fixture).runWithAutoPopupEnabled(runnable::run);
    }
    catch (Throwable e) {
      //noinspection unchecked
      throw (E)e;
    }
  }

  /**
   * Types {@code s} in the editor of {@code fixture} one character at a time.
   * After each character, it waits for the auto-popup and for the completion.
   * Call it from a thread that is not the EDT, inside {@link #runWithAutoPopupEnabled}.
   */
  public static void type(@NotNull CodeInsightTestFixture fixture, @NotNull String s) {
    new CompletionAutoPopupTester(fixture).typeWithPauses(s);
  }

  /**
   * Returns the active lookup of {@code fixture}, or {@code null} when no lookup shows.
   * Call it from any thread.
   */
  public static @Nullable LookupImpl getLookup(@NotNull CodeInsightTestFixture fixture) {
    return (LookupImpl)fixture.getLookup();
  }
}
