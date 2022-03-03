// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tests;

import junit.framework.Test;
import junit.framework.TestCase;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.runner.Description;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TeamCity note: <a href="https://www.jetbrains.com/help/teamcity/2021.1/build-failure-conditions.html#test-retry">test retry support</a> should be enabled for previously failed tests to be muted
 */
public final class Retries {
  public static final int NUMBER = Optional.ofNullable(System.getProperty("intellij.build.test.retries.number"))
    .map(Integer::parseInt)
    .orElse(0);
  private static final int FAILED_TESTS_STOP_THRESHOLD = Optional.ofNullable(System.getProperty("intellij.build.test.retries.failedTestsStopThreshold"))
    .map(Integer::parseInt)
    .orElse(Integer.MAX_VALUE);
  private static final Set<Method> SUCCESSFUL_TEST_METHODS = new HashSet<>();
  private static volatile int SUCCESSFUL_RETRIES;
  private static volatile int FAILED_RETRIES;

  static {
    if (NUMBER > 0) {
      Runtime.getRuntime().addShutdownHook(new Thread("Successful Retries Statistics Report") {
        @Override
        public void run() {
          reportStatistics();
        }
      });
    }
  }

  private Retries() { }

  private static void reportStatistics() {
    if (SUCCESSFUL_RETRIES > 0) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("##teamcity[buildStatisticValue key='Successful test retries' value='" +
                         SUCCESSFUL_RETRIES + "']");
    }
  }

  private static synchronized void testFinished(Method test, boolean isSuccessful) {
    assert NUMBER > 0;
    if (isSuccessful) {
      SUCCESSFUL_RETRIES++;
      SUCCESSFUL_TEST_METHODS.add(test);
    }
    else {
      FAILED_RETRIES++;
    }
  }

  private static Optional<Method> getMethodFromClass(Class<?> clazz, String methodName) {
    @SuppressWarnings("SSBasedInspection")
    var methods = Arrays.stream(clazz.getDeclaredMethods())
      .filter(method -> methodName.equals(method.getName()))
      .collect(Collectors.toList());
    if (methods.size() == 1) {
      return Optional.of(methods.get(0));
    }
    else {
      return Optional.empty();
    }
  }

  public static void testFinished(Test test, boolean isSuccessful) {
    assert test instanceof TestCase;
    getMethodFromClass(test.getClass(), ((TestCase)test).getName())
      .ifPresent(it -> testFinished(it, isSuccessful));
  }

  public static void testFinished(Description testDescription, boolean isSuccessful) {
    getMethodFromClass(testDescription.getTestClass(), testDescription.getMethodName())
      .ifPresent(it -> testFinished(it, isSuccessful));
  }

  public static boolean shouldStop() {
    var failedTests = FAILED_RETRIES / NUMBER;
    return failedTests > FAILED_TESTS_STOP_THRESHOLD;
  }

  public static synchronized boolean getAndClearSuccessfulStatus(TestIdentifier testIdentifier) {
    assert NUMBER > 0;
    assert testIdentifier.isTest();
    var testMethod = testIdentifier.getSource().orElse(null);
    if (testMethod instanceof MethodSource) {
      return SUCCESSFUL_TEST_METHODS.remove(((MethodSource)testMethod).getJavaMethod());
    }
    else {
      return false;
    }
  }
}
