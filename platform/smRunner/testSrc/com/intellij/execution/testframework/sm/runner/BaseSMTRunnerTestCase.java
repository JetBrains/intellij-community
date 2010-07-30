/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;

/**
 * @author Roman Chernyatchik
 */
public abstract class BaseSMTRunnerTestCase extends LightPlatformTestCase {
  protected SMTestProxy mySuite;
  protected SMTestProxy mySimpleTest;

  protected BaseSMTRunnerTestCase() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySuite = createSuiteProxy();
    mySimpleTest = createTestProxy();
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

  protected RuntimeConfiguration createRunConfiguration() {
    return new MockRuntimeConfiguration(getProject());
  }

  protected TestConsoleProperties createConsoleProperties() {
    final RuntimeConfiguration runConfiguration = createRunConfiguration();

    final TestConsoleProperties consoleProperties = new SMTRunnerConsoleProperties(runConfiguration, "SMRunnerTests", DefaultDebugExecutor.getDebugExecutorInstance());
    TestConsoleProperties.HIDE_PASSED_TESTS.set(consoleProperties, false);
    
    return consoleProperties;
  }

  protected void doPassTest(final SMTestProxy test) {
    test.setStarted();
    test.setFinished();
  }

  protected void doFailTest(final SMTestProxy test) {
    test.setStarted();
    test.setTestFailed("", "", false);
    test.setFinished();
  }

  protected void doErrorTest(final SMTestProxy test) {
    test.setStarted();
    test.setTestFailed("", "", true);
    test.setFinished();
  }
}
