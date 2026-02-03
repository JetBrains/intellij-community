// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import com.intellij.OutOfProcessRetries;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.*;

import java.io.IOException;

@SuppressWarnings("CallToPrintStackTrace")
public class JUnit5OutOfProcessRetrySessionListener implements LauncherSessionListener {
  private long suiteStarted = 0;
  private OutOfProcessRetries.OutOfProcessRetryListener listener;

  @Override
  public void launcherSessionOpened(LauncherSession session) {
    // Method could be called several times, don't override field
    if (listener == null) {
      listener = OutOfProcessRetries.getListenerForOutOfProcessRetry();
    }
    if (listener == null) {
      return;
    }
    session.getLauncher().registerTestExecutionListeners(new TestExecutionListener() {
      @Override
      public void testPlanExecutionStarted(TestPlan testPlan) {
        suiteStarted = System.nanoTime();
      }

      @Override
      public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        final TestExecutionResult.Status status = testExecutionResult.getStatus();
        final Throwable throwable = testExecutionResult.getThrowable().orElse(null);
        if (status != TestExecutionResult.Status.FAILED) {
          return;
        }
        testIdentifier.getSource().map(
          testSource ->
            testSource instanceof MethodSource ms ? ms.getClassName() :
            testSource instanceof ClassSource cs ? cs.getClassName() :
            null
        ).ifPresent(className -> listener.addError(className, throwable));
      }
    });
  }

  @Override
  public void launcherSessionClosed(LauncherSession session) {
    if (listener == null || suiteStarted == 0) return;
    try {
      listener.save();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
