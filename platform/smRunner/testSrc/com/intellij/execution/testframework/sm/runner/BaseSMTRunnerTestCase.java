// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.configurations.ModuleRunConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 */
public abstract class BaseSMTRunnerTestCase extends LightPlatformTestCase {
  protected SMTestProxy mySuite;
  protected SMTestProxy mySimpleTest;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySuite = createSuiteProxy();
    mySimpleTest = createTestProxy();
  }

  @Override
  protected void tearDown() throws Exception {
    if (mySuite != null) Disposer.dispose(mySuite);
    if (mySimpleTest != null) Disposer.dispose(mySimpleTest);
    super.tearDown();
  }

  protected SMTestProxy createTestProxy() {
    return createTestProxy("test");
  }

  protected SMTestProxy createTestProxy(final SMTestProxy parentSuite) {
    return createTestProxy("test", parentSuite);
  }

  protected SMTestProxy createTestProxy(final String name) {
    return createTestProxy(name, null);
  }

  protected SMTestProxy createTestProxy(final String name, final SMTestProxy parentSuite) {
    final SMTestProxy proxy = new SMTestProxy(name, false, "file://test.text");
    if (parentSuite != null) {
      parentSuite.addChild(proxy);
    }
    return proxy;
  }

  protected SMTestProxy createSuiteProxy(final String name) {
    return createSuiteProxy(name, null);
  }

  protected SMTestProxy createSuiteProxy(final String name, final SMTestProxy parentSuite) {
    final SMTestProxy suite = new SMTestProxy(name, true, null);
    if (parentSuite != null) {
      parentSuite.addChild(suite);
    }
    return suite;
  }

  protected SMTestProxy createSuiteProxy() {
    return createSuiteProxy("suite");
  }

  protected SMTestProxy createSuiteProxy(final SMTestProxy parentSuite) {
    return createSuiteProxy("suite", parentSuite);
  }

  protected ModuleRunConfiguration createRunConfiguration() {
    return new MockRuntimeConfiguration(getProject());
  }

  protected TestConsoleProperties createConsoleProperties() {
    final ModuleRunConfiguration runConfiguration = createRunConfiguration();

    final TestConsoleProperties consoleProperties = new SMTRunnerConsoleProperties(runConfiguration, "SMRunnerTests", DefaultDebugExecutor.getDebugExecutorInstance());
    TestConsoleProperties.HIDE_PASSED_TESTS.set(consoleProperties, false);
    
    return consoleProperties;
  }

  /**
   * @return Test tree using poorman's graphics
   */
  @NotNull
  public static String getFormattedTestTree(@NotNull final SMTestProxy proxy) {
    final StringBuilder builder = new StringBuilder("Test tree:\n");
    formatLevel(proxy, 0, builder);
    return builder.toString();
  }

  private static void formatLevel(@NotNull final SMTestProxy test, final int level, @NotNull final StringBuilder builder) {
    builder.append(StringUtil.repeat(".", level));
    builder.append(test.getName());
    if (test.wasTerminated()) {
      builder.append("[T]");
    }
    else if (test.isPassed()) {
      builder.append("(+)");
    }
    else if (test.isIgnored()) {
      builder.append("(~)");
    }
    else {
      builder.append("(-)");
    }
    builder.append('\n');
    for (SMTestProxy child : test.getChildren()) {
      formatLevel(child, level + 1, builder);
    }
  }

  /**
   * Searches for test by its name recursevly in test, passed as arumuent.
   *
   * @param testName test name to find
   * @param test     root test
   * @return test or null if not found
   */
  @Nullable
  public static AbstractTestProxy findTestByName(@NotNull final String testName, @NotNull final AbstractTestProxy test) {
    if (test.getName().equals(testName)) {
      return test;
    }
    for (final AbstractTestProxy testProxy : test.getChildren()) {
      final AbstractTestProxy result = findTestByName(testName, testProxy);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @NotNull
  public static String getTestOutput(@NotNull final AbstractTestProxy test) {
    final MockPrinter p = new MockPrinter();
    test.printOn(p);
    return p.getAllOut();
  }
}
