// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.configurations.ModuleRunConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformTestCase;

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
    final SMTestProxy proxy = new SMTestProxy(name, false, null);
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
}
