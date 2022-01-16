// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.util.SystemProperties;
import com.intellij.util.ThrowableRunnable;
import junit.extensions.TestDecorator;
import junit.framework.*;
import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.notification.StoppedByUserException;

import java.util.Enumeration;

/**
 * TeamCity note: <a href="https://www.jetbrains.com/help/teamcity/2021.1/build-failure-conditions.html#test-retry">test retry support</a> should be enabled for previously failed tests to be muted
 */
final class Retries {
  private static final int RETRY_NUMBER = SystemProperties.getIntProperty("intellij.build.test.retries.number", 0);
  private Retries() { }

  static JUnit4TestAdapterCache maybeEnable(JUnit4TestAdapterCache cache) {
    if (RETRY_NUMBER > 0) {
      return new Retries.JUnit4TestAdapterCacheDelegate(cache);
    }
    else {
      return cache;
    }
  }

  static TestResult maybeEnable(TestResult testResult) {
    if (RETRY_NUMBER > 0) {
      testResult = new RetryingTestResult(testResult);
    }
    return testResult;
  }

  private static <T extends Throwable> void retryTest(String description, ThrowableRunnable<T> test) {
    for (int i = 1; i <= RETRY_NUMBER; i++) {
      try {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println(description + " failed, retrying attempt #" + i + " of " + RETRY_NUMBER);
        test.run();
        break;
      }
      catch (Throwable ignored) {
      }
    }
  }

  private static void retryTest(Failure failure, RunNotifier notifier) {
    var testDescription = failure.getDescription();
    if (testDescription == Description.EMPTY ||
        testDescription == Description.TEST_MECHANISM ||
        !testDescription.isTest()) {
      return;
    }
    var testClass = testDescription.getTestClass();
    if (testClass == null) {
      throw new IllegalStateException("No test class for " + testDescription.getDisplayName());
    }
    if (TestCase.class.isAssignableFrom(testClass) &&
        "warning".equals(testDescription.getMethodName()) ||
        TestCaseLoader.isPerformanceTest(null, testClass.getSimpleName())) {
      return;
    }
    var runner = Request.classWithoutSuiteMethod(testClass).filterWith(testDescription).getRunner();
    var failureExposingListener = new FailureExposingListener();
    notifier.addListener(failureExposingListener);
    retryTest(testDescription.getDisplayName(), () -> {
      failureExposingListener.failure = null;
      runner.run(notifier);
      if (failureExposingListener.failure != null) {
        throw failureExposingListener.failure.getException();
      }
    });
  }

  private static void retryTest(Test test, TestResult testResult) {
    if (test instanceof TestCase) {
      if ("warning".equals(((TestCase)test).getName())) {
        return;
      }
      var failureExposingListener = new FailureExposingListener();
      testResult.addListener(failureExposingListener);
      retryTest(test.toString(), () -> {
        synchronized (testResult) {
          failureExposingListener.throwable = null;
        }
        test.run(testResult);
        synchronized (testResult) {
          if (failureExposingListener.throwable != null) {
            throw failureExposingListener.throwable;
          }
        }
      });
    }
    else if (!(test instanceof TestAll) &&
             !(test instanceof TestSuite) &&
             !(test instanceof TestDecorator) &&
             !(test instanceof JUnit4TestAdapter) &&
             !(test instanceof JUnit4TestCaseFacade)) {
      var msg = "Unable to retry test " + test.getClass().getCanonicalName();
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("##teamcity[message text='" + msg + "' status='ERROR']");
      throw new IllegalStateException(msg);
    }
  }

  private static class JUnit4TestAdapterCacheDelegate extends JUnit4TestAdapterCache {
    final JUnit4TestAdapterCache delegate;

    JUnit4TestAdapterCacheDelegate(JUnit4TestAdapterCache delegate) {
      this.delegate = delegate;
    }

    @Override
    public RunNotifier getNotifier(TestResult result, JUnit4TestAdapter adapter) {
      return new RetryingRunNotifier(delegate.getNotifier(result, adapter));
    }
  }

  private static class RetryingRunNotifier extends RunNotifier {
    final RunNotifier delegate;
    volatile Failure failure;

    RetryingRunNotifier(RunNotifier delegate) {
      this.delegate = delegate;
    }

    @Override
    public void fireTestStarted(Description description) throws StoppedByUserException {
      synchronized (this) {
        this.failure = null;
      }
      delegate.fireTestStarted(description);
    }

    @Override
    public void fireTestFailure(Failure failure) {
      delegate.fireTestFailure(failure);
      if (!(failure.getException() instanceof AssumptionViolatedException)) {
        synchronized (this) {
          this.failure = failure;
        }
      }
    }

    @Override
    public void fireTestFinished(Description description) {
      delegate.fireTestFinished(description);
      if (failure != null) {
        retryTest(failure, delegate);
      }
    }

    @Override
    public void addListener(RunListener listener) {
      delegate.addListener(listener);
    }

    @Override
    public void removeListener(RunListener listener) {
      delegate.removeListener(listener);
    }

    @Override
    public void fireTestRunStarted(Description description) {
      delegate.fireTestRunStarted(description);
    }

    @Override
    public void fireTestRunFinished(Result result) {
      delegate.fireTestRunFinished(result);
    }

    @Override
    public void fireTestAssumptionFailed(Failure failure) {
      delegate.fireTestAssumptionFailed(failure);
    }

    @Override
    public void fireTestIgnored(Description description) {
      delegate.fireTestIgnored(description);
    }

    @Override
    public void pleaseStop() {
      delegate.pleaseStop();
    }

    @Override
    public void addFirstListener(RunListener listener) {
      delegate.addFirstListener(listener);
    }
  }

  private static class RetryingTestResult extends TestResult {
    final TestResult delegate;
    volatile Test failedTest;

    RetryingTestResult(TestResult delegate) {
      this.delegate = delegate;
    }

    @Override
    public void runProtected(Test test, Protectable p) {
      // delegate should not be called, otherwise test failures aren't propagated from it
      super.runProtected(test, p);
    }

    @Override
    public void startTest(Test test) {
      synchronized (this) {
        failedTest = null;
      }
      delegate.startTest(test);
    }

    @Override
    public synchronized void addError(Test test, Throwable e) {
      delegate.addError(test, e);
      if (!(e instanceof AssumptionViolatedException)) {
        synchronized (this) {
          failedTest = test;
        }
      }
    }

    @Override
    public synchronized void addFailure(Test test, AssertionFailedError e) {
      delegate.addFailure(test, e);
      synchronized (this) {
        failedTest = test;
      }
    }

    @Override
    public void endTest(Test test) {
      delegate.endTest(test);
      if (failedTest != null) {
        retryTest(failedTest, delegate);
      }
    }

    @Override
    public synchronized void addListener(TestListener listener) {
      delegate.addListener(listener);
    }

    @Override
    public synchronized void removeListener(TestListener listener) {
      delegate.removeListener(listener);
    }

    @Override
    public synchronized int errorCount() {
      return delegate.errorCount();
    }

    @Override
    public synchronized Enumeration<TestFailure> errors() {
      return delegate.errors();
    }

    @Override
    public synchronized int failureCount() {
      return delegate.failureCount();
    }

    @Override
    public synchronized Enumeration<TestFailure> failures() {
      return delegate.failures();
    }

    @Override
    public synchronized int runCount() {
      return delegate.runCount();
    }

    @Override
    public synchronized boolean shouldStop() {
      return delegate.shouldStop();
    }

    @Override
    public synchronized void stop() {
      delegate.stop();
    }

    @Override
    public synchronized boolean wasSuccessful() {
      return delegate.wasSuccessful();
    }
  }

  private static class FailureExposingListener extends RunListener implements TestListener {
    Failure failure;
    Throwable throwable;

    @Override
    public void testFailure(Failure failure) throws Exception {
      this.failure = failure;
    }

    @Override
    public void addError(Test test, Throwable e) {
      throwable = e;
    }

    @Override
    public void addFailure(Test test, AssertionFailedError e) {
      throwable = e;
    }

    @Override
    public void endTest(Test test) { }

    @Override
    public void startTest(Test test) { }
  }
}