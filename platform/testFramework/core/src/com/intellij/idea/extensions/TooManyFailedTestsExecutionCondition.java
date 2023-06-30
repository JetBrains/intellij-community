// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea.extensions;

import org.junit.AssumptionViolatedException;
import org.junit.jupiter.api.extension.*;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class TooManyFailedTestsExecutionCondition implements ExecutionCondition, TestWatcher, BeforeEachCallback, BeforeAllCallback {
  private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("Not too many failed tests (yet)");
  private static final String MAX_FAILURE_TEST_COUNT_FLAG = "idea.max.failure.test.count";

  private static final int MAX_FAILURE_TEST_COUNT = Integer.parseInt(Objects.requireNonNullElse(
    System.getProperty(MAX_FAILURE_TEST_COUNT_FLAG), "150"
  ));

  private static final AtomicInteger ourFailedTestsCount = new AtomicInteger();
  private static final AtomicInteger ourExecutedTestsCount = new AtomicInteger();
  private ConditionEvaluationResult myDisabledReason = null;


  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    if (MAX_FAILURE_TEST_COUNT >= 0 && ourFailedTestsCount.get() > MAX_FAILURE_TEST_COUNT) {
      if (myDisabledReason == null) {
        String msg = "Too many errors (" +
                     ourFailedTestsCount.get() +
                     ", MAX_FAILURE_TEST_COUNT = " +
                     MAX_FAILURE_TEST_COUNT +
                     "). Executed: " +
                     ourExecutedTestsCount.get() +
                     " tests";
        myDisabledReason = ConditionEvaluationResult.disabled(msg);
      }
      return myDisabledReason;
    }
    return ENABLED;
  }

  @Override
  public void testFailed(ExtensionContext context, Throwable cause) {
    ourFailedTestsCount.incrementAndGet();
    ourExecutedTestsCount.incrementAndGet();
  }

  @Override
  public void testSuccessful(ExtensionContext context) {
    ourExecutedTestsCount.incrementAndGet();
  }

  @Override
  public void testAborted(ExtensionContext context, Throwable cause) {
    ourExecutedTestsCount.incrementAndGet();
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    failIfDisabled(context);
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    failIfDisabled(context);
  }

  private void failIfDisabled(ExtensionContext context) {
    ConditionEvaluationResult evaluated = evaluateExecutionCondition(context);
    if (evaluated.isDisabled()) {
      //noinspection OptionalGetWithoutIsPresent
      String reason = evaluated.getReason().get();
      throw new AssumptionViolatedException(reason);
    }
  }
}
