/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.testframework.PoolOfTestIcons;
import com.intellij.execution.testframework.sm.SMTestsRunnerBundle;
import com.intellij.execution.testframework.sm.UITestUtil;
import com.intellij.execution.testframework.sm.runner.BaseSMTRunnerTestCase;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.icons.AllIcons;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Roman Chernyatchik
 */
public class TestsPresentationUtilTest extends BaseSMTRunnerTestCase {
  @NonNls private static final String FAKE_TEST_NAME = "my test";
  private TestsPresentationUtilTest.MyRenderer myRenderer;
  private UITestUtil.FragmentsContainer myFragContainer;
  private SMTestProxy.SMRootTestProxy mySMRootTestProxy;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myRenderer = new MyRenderer(false, new UITestUtil.FragmentsContainer());
    myFragContainer = myRenderer.getFragmentsContainer();
    mySMRootTestProxy = new SMTestProxy.SMRootTestProxy();
  }

  public void testProgressText() {
    assertEquals("Running: 10 of 1  Failed: 1  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 1, 10, 1, null, false));
    assertEquals("Running: 10 of 1  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 1, 10, 0, null, false));
    assertEquals("Running: 10 of 1  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 1, 10, 0, null, true));
    //here number format is platform-dependent
    assertEquals("Done: 10 of 1  (0ms)  ",
                 TestsPresentationUtil.getProgressStatus_Text(5, 5, 1, 10, 0, null, false));
  }

  public void testProgressText_UnsetTotal() {
    assertEquals("Running: 0 of <...>  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 0, 0, null, false));
    assertEquals("Running: 0 of 0  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 0, 0, null, true));
    assertEquals("Running: 1 of <...>  Failed: 1  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 1, 1, null, false));
    assertEquals("Running: 10 of <...>  Failed: 1  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 10, 1, null, false));
    //here number format is platform-dependent
    assertEquals("Done: 10 of <...>  Failed: 1  (5ms)  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 5, 0, 10, 1, null, false));
  }

  public void testProgressText_Category() {
    assertEquals("Running: 0 of <...>  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 0, 0, new HashSet<>(), false));

    final Set<String> category = new LinkedHashSet<>();

    category.clear();
    category.add("Scenarios");
    assertEquals("Running: Scenarios 0 of <...>  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 0, 0, category, false));

    category.clear();
    category.add("Scenarios");
    category.add(TestsPresentationUtil.DEFAULT_TESTS_CATEGORY);
    assertEquals("Running: Scenarios, tests 0 of <...>  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 0, 0, category, false));

    category.clear();
    category.add("Cucumbers");
    category.add("Tomatoes");
    category.add(TestsPresentationUtil.DEFAULT_TESTS_CATEGORY);
    assertEquals("Running: Cucumbers, tomatoes, tests 0 of <...>  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 0, 0, category, false));
    assertEquals("Running: Cucumbers, tomatoes, tests 0 of 0  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 0, 0, category, true));

    category.clear();
    category.add(TestsPresentationUtil.DEFAULT_TESTS_CATEGORY);
    assertEquals("Running: 0 of <...>  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 0, 0, category, false));

  }

  public void testFormatTestProxyTest_NewTest() {
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.NOT_RAN, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_NewTestPaused() {
    //paused
    final MyRenderer pausedRenderer = new MyRenderer(true, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatTestProxy(mySimpleTest, pausedRenderer);

    assertEquals(PoolOfTestIcons.NOT_RAN, pausedRenderer.getIcon());
    assertEquals(1, myFragContainer.getFragments().size());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_Started() {
    //paused
    mySimpleTest.setStarted();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertIsAnimatorProgressIcon(myRenderer.getIcon());
    assertEquals(1, myFragContainer.getFragments().size());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_StartedAndPaused() {
    //paused
    final MyRenderer pausedRenderer = new MyRenderer(true, myFragContainer = new UITestUtil.FragmentsContainer());

    mySimpleTest.setStarted();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, pausedRenderer);

    assertEquals(AllIcons.RunConfigurations.TestPaused, pausedRenderer.getIcon());
    assertEquals(1, myFragContainer.getFragments().size());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_StartedAndPaused_WithErrors() {
    //paused
    final MyRenderer pausedRenderer = new MyRenderer(true, myFragContainer = new UITestUtil.FragmentsContainer());

    mySimpleTest.setStarted();
    mySimpleTest.addError("msg", "stacktrace", true);

    TestsPresentationUtil.formatTestProxy(mySimpleTest, pausedRenderer);

    assertEquals(SMPoolOfTestIcons.PAUSED_E_ICON, pausedRenderer.getIcon());
  }

  public void testFormatTestProxyTest_Passed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.PASSED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_Failed() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.FAILED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

    mySimpleTest.setFinished();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);
    assertEquals(PoolOfTestIcons.FAILED_ICON, myRenderer.getIcon());
  }

  public void testFormatTestProxyTest_Failed_WithErrors() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    mySimpleTest.addError("msg", "stacktrace", true);
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(SMPoolOfTestIcons.FAILED_E_ICON, myRenderer.getIcon());
  }

  public void testFormatTestProxyTest_Error() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.ERROR_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

    mySimpleTest.setFinished();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);
    assertEquals(PoolOfTestIcons.ERROR_ICON, myRenderer.getIcon());
  }

  public void testFormatTestProxyTest_Error_WithErrors() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);
    mySimpleTest.addError("msg", "stacktrace", true);
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.ERROR_ICON, myRenderer.getIcon());
  }

  public void testFormatTestProxyTest_Ignored() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestIgnored("", null);
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.IGNORED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

    mySimpleTest.setFinished();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);
    assertEquals(PoolOfTestIcons.IGNORED_ICON, myRenderer.getIcon());
  }

  public void testFormatTestProxyTest_Ignored_WithErrors() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestIgnored("", null);
    mySimpleTest.addError("msg", "stacktrace", true);
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(SMPoolOfTestIcons.IGNORED_E_ICON, myRenderer.getIcon());
  }

  public void testFormatTestProxyTest_Terminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

    mySimpleTest.setFinished();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);
    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
  }

  public void testFormatTestProxyTest_TerminatedWithErrors() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    mySimpleTest.addError("msg", "stacktrace", true);
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(SMPoolOfTestIcons.TERMINATED_E_ICON, myRenderer.getIcon());
  }

  public void testFormatTestProxyTest_WithCriticalErrors() {
    mySimpleTest.setStarted();
    mySimpleTest.addError("msg", "stacktrace", true);
    mySimpleTest.setFinished();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(SMPoolOfTestIcons.PASSED_E_ICON, myRenderer.getIcon());
  }

  public void testFormatTestProxyTest_WithErrors_LegacyApi() {
    mySimpleTest.setStarted();
    mySimpleTest.addError("msg", "stacktrace", true);
    mySimpleTest.setFinished();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(SMPoolOfTestIcons.PASSED_E_ICON, myRenderer.getIcon());
  }

  public void testFormatTestProxyTest_WithNoncriticalErrors() {
    mySimpleTest.setStarted();
    mySimpleTest.addError("msg", "stacktrace", false);
    mySimpleTest.setFinished();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.PASSED_ICON, myRenderer.getIcon());
  }

  public void testFormatRootNodeWithChildren_Started() {
    mySMRootTestProxy.setTestsReporterAttached();
    mySMRootTestProxy.setStarted();

    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, myRenderer);

    assertIsAnimatorProgressIcon(myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Running tests...", myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatRootNodeWithChildren_Failed() {
    final MyRenderer renderer1 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());

    mySMRootTestProxy.setTestsReporterAttached();
    mySMRootTestProxy.addChild(mySimpleTest);
    mySMRootTestProxy.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    mySimpleTest.setFinished();
    mySMRootTestProxy.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, renderer1);

    assertEquals(PoolOfTestIcons.FAILED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results", renderer1.getFragmentsContainer().getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getFragmentsContainer().getAttribsAt(0));

    final MyRenderer renderer2 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, renderer2);
    mySMRootTestProxy.setFinished();
    assertEquals(PoolOfTestIcons.FAILED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results", renderer1.getFragmentsContainer().getTextAt(0));
  }

  public void testFormatRootNodeWithChildren_Failed_WithErrors() {
    final MyRenderer renderer1 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());

    mySMRootTestProxy.setTestsReporterAttached();
    mySMRootTestProxy.addChild(mySimpleTest);
    mySMRootTestProxy.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    mySimpleTest.addError("msg", "stacktrace", true);
    mySimpleTest.setFinished();
    mySMRootTestProxy.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, renderer1);

    assertEquals(SMPoolOfTestIcons.FAILED_E_ICON, renderer1.getIcon());

    final MyRenderer renderer2 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, renderer2);
    mySMRootTestProxy.setFinished();
    assertEquals(SMPoolOfTestIcons.FAILED_E_ICON, renderer1.getIcon());
  }

  public void testFormatRootNodeWithChildren_Error() {
    final MyRenderer renderer1 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());

    mySMRootTestProxy.setTestsReporterAttached();
    mySMRootTestProxy.addChild(mySimpleTest);
    mySMRootTestProxy.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);
    mySimpleTest.addError("msg", "stacktrace", true);
    mySimpleTest.setFinished();
    mySMRootTestProxy.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, renderer1);

    assertEquals(PoolOfTestIcons.ERROR_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results", renderer1.getFragmentsContainer().getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getFragmentsContainer().getAttribsAt(0));

    final MyRenderer renderer2 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, renderer2);
    mySMRootTestProxy.setFinished();
    assertEquals(PoolOfTestIcons.ERROR_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results", renderer1.getFragmentsContainer().getTextAt(0));
  }

  public void testFormatRootNodeWithChildren_Ignored() {
    final MyRenderer renderer1 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());

    mySMRootTestProxy.setTestsReporterAttached();
    mySMRootTestProxy.addChild(mySimpleTest);
    mySMRootTestProxy.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setTestIgnored("", null);
    mySimpleTest.setFinished();
    mySMRootTestProxy.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, renderer1);

    assertEquals(PoolOfTestIcons.IGNORED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results", renderer1.getFragmentsContainer().getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getFragmentsContainer().getAttribsAt(0));

    final MyRenderer renderer2 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, renderer2);
    mySMRootTestProxy.setFinished();
    assertEquals(PoolOfTestIcons.IGNORED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results", renderer1.getFragmentsContainer().getTextAt(0));
  }

  public void testFormatRootNodeWithChildren_Ignored_WithErrors() {
    final MyRenderer renderer1 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());

    mySMRootTestProxy.setTestsReporterAttached();
    mySMRootTestProxy.addChild(mySimpleTest);
    mySMRootTestProxy.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setTestIgnored("", null);
    mySimpleTest.addError("msg", "stacktrace", true);
    mySimpleTest.setFinished();
    mySMRootTestProxy.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, renderer1);

    assertEquals(SMPoolOfTestIcons.IGNORED_E_ICON, renderer1.getIcon());

    final MyRenderer renderer2 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, renderer2);
    mySMRootTestProxy.setFinished();
    assertEquals(SMPoolOfTestIcons.IGNORED_E_ICON, renderer1.getIcon());
  }

  public void testFormatRootNodeWithChildren_Passed() {
    mySMRootTestProxy.setTestsReporterAttached();
    mySMRootTestProxy.addChild(mySimpleTest);
    mySMRootTestProxy.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    mySMRootTestProxy.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, myRenderer);

    assertEquals(PoolOfTestIcons.PASSED_ICON, myRenderer.getIcon());
    assertOneElement(myRenderer.getFragmentsContainer().getFragments());
    assertEquals("Test Results", myRenderer.getFragmentsContainer().getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myRenderer.getFragmentsContainer().getAttribsAt(0));
  }

  public void testFormatRootNodeWithChildren_Passed_WithErrors() {
    mySMRootTestProxy.setTestsReporterAttached();
    mySMRootTestProxy.addChild(mySimpleTest);
    mySMRootTestProxy.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.addError("msg", "stacktrace", true);
    mySimpleTest.setFinished();
    mySMRootTestProxy.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, myRenderer);

    assertEquals(SMPoolOfTestIcons.PASSED_E_ICON, myRenderer.getIcon());
  }

  public void testFormatRootNodeWithChildren_Terminated() {
    mySMRootTestProxy.setTestsReporterAttached();
    mySMRootTestProxy.addChild(mySimpleTest);
    mySMRootTestProxy.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    mySMRootTestProxy.setTerminated();
    // terminated
    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, myRenderer);

    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Terminated", myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatRootNodeWithChildren_Terminated_WithErrors() {
    mySMRootTestProxy.setTestsReporterAttached();
    mySMRootTestProxy.addChild(mySimpleTest);
    mySMRootTestProxy.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.addError("msg", "stacktrace", true);
    mySimpleTest.setFinished();
    mySMRootTestProxy.setTerminated();
    // terminated
    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, myRenderer);

    assertEquals(SMPoolOfTestIcons.TERMINATED_E_ICON, myRenderer.getIcon());
  }

  public void testFormatRootNodeWithChildren_TerminatedAndFinished() {
    mySMRootTestProxy.setTestsReporterAttached();
    mySMRootTestProxy.addChild(mySimpleTest);
    mySMRootTestProxy.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    mySMRootTestProxy.setTerminated();
    mySMRootTestProxy.setFinished();

    // terminated and finished
    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, myRenderer);
    mySMRootTestProxy.setFinished();
    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Terminated", myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatRootNodeWithChildren_Passed_StartShutdownErrors() {
    final MyRenderer renderer1 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());

    mySMRootTestProxy.setTestsReporterAttached();
    mySMRootTestProxy.addChild(mySimpleTest);
    mySMRootTestProxy.setStarted();
    mySMRootTestProxy.addError("msg1", "stacktrace1", true);
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    mySMRootTestProxy.addError("msg2", "stacktrace2", true);
    mySMRootTestProxy.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySMRootTestProxy, renderer1);
    assertEquals(SMPoolOfTestIcons.PASSED_E_ICON, renderer1.getIcon());

    final MyRenderer renderer2 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatTestProxy(mySimpleTest, renderer2);
    assertEquals(PoolOfTestIcons.PASSED_ICON, renderer2.getIcon());
  }

  public void testFormatRootNodeWithoutChildren() {
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySMRootTestProxy, myRenderer);

    assertEquals(PoolOfTestIcons.NOT_RAN, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("No Test Results", myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.ERROR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_Started() {
    mySMRootTestProxy.setTestsReporterAttached();
    mySMRootTestProxy.setStarted();
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySMRootTestProxy, myRenderer);

    assertIsAnimatorProgressIcon(myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Instantiating tests...", myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_ReporterRegistered() {
    mySMRootTestProxy.setTestsReporterAttached();
    mySMRootTestProxy.setStarted();
    mySMRootTestProxy.setFinished();
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySMRootTestProxy, myRenderer);

    assertEquals(PoolOfTestIcons.NOT_RAN, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("No tests were found", myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.ERROR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_ReporterNotRegistered() {
    mySMRootTestProxy.setStarted();
    mySMRootTestProxy.setFinished();
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySMRootTestProxy, myRenderer);

    assertEquals(PoolOfTestIcons.NOT_RAN, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals(SMTestsRunnerBundle.message("sm.test.runner.ui.tests.tree.presentation.labels.test.reporter.not.attached"), myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.ERROR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_Terminated() {
    mySMRootTestProxy.setTestsReporterAttached();
    mySMRootTestProxy.setStarted();
    mySMRootTestProxy.setTerminated();
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySMRootTestProxy, myRenderer);

    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Terminated", myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_PY_2434() {
    mySMRootTestProxy.setTestsReporterAttached();
    // See [PY-2434] Unittest: Do not show "No test were found" notification before completing test suite
    mySMRootTestProxy.addChild(mySimpleTest);
    mySMRootTestProxy.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("msg", "stacktrace", false);
    mySimpleTest.setFinished();
    mySMRootTestProxy.setFinished();
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySMRootTestProxy, myRenderer);

    assertEquals(PoolOfTestIcons.FAILED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Test Results", myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }


  public void testGetPresentableName() {
    //Test unit examples
    assertProxyPresentation("testFirst", "MyRubyTest1", "MyRubyTest1.testFirst");
    assertProxyPresentation("MyRubyTest1.testFirst", "/some/path/on/my/comp", "MyRubyTest1.testFirst");

    //Spec example
    assertProxyPresentation("should be beautiful", "World", "should be beautiful");
    assertProxyPresentation("<no name>", "World", "");

    //Common example
    assertProxyPresentation("some phrase", "Begin of", "Begin of some phrase");

    //Name with extra white spaces
    assertProxyPresentation("several variants: 1. Do first 2. Do 2nd 3. Do 3d", "I have ", "several variants: 1. Do first\n     2. Do 2nd\n     3. Do 3d");
    assertProxyPresentation("several variants: 1. Do first 2. Do 2nd 3. Do 3d", "I have ", "I have several variants: 1. Do first\n     2. Do 2nd\n     3. Do 3d");
    assertProxyPresentation("several variants: 1. Do first 2. Do 2nd", "I have", "several variants: 1. Do first\t2. Do 2nd");
    assertProxyPresentation("several variants: 1. Do first", "I have", "several variants: 1. Do           first");

    //Bound examples
    assertEquals("suite without parent",
                 TestsPresentationUtil.getPresentableName(createSuiteProxy("suite without parent")));
    assertEquals("test without parent",
                 TestsPresentationUtil.getPresentableName(createTestProxy("test without parent")));
    assertEquals("with spaces",
                 TestsPresentationUtil.getPresentableName(createSuiteProxy("    with spaces  ")));

  }

  public void testGetTestStatusPresentation_NotRun() {
    assertEquals("Not run", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
  }

  public void testGetTestStatusPresentation_Progress() {
    mySimpleTest.setStarted();
    assertEquals("Running...", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
  }

  public void testGetTestStatusPresentation_Passed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    assertEquals("Passed", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
  }

  public void testGetTestStatusPresentation_Failed() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertEquals("Assertion failed", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
    mySimpleTest.setFinished();
    assertEquals("Assertion failed", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
  }

  public void testGetTestStatusPresentation_TestError() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);
    assertEquals("Error", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
    mySimpleTest.setFinished();
    assertEquals("Error", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
  }

  public void testGetTestStatusPresentation_TestIgnored() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestIgnored("", null);
    assertEquals("Ignored", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
    mySimpleTest.setFinished();
    assertEquals("Ignored", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
  }

  public void testGetTestStatusPresentation_Terminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    assertEquals("Terminated", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
  }

  private void assertProxyPresentation(final String expectedPresentation, final String parentName,
                                       final String childName) {
    assertEquals(expectedPresentation,
                 TestsPresentationUtil.getPresentableName(createChildSuiteOfParentSuite(parentName, childName)));
    assertEquals(expectedPresentation,
                 TestsPresentationUtil.getPresentableName(createChildTestOfSuite(parentName, childName)));
  }

  protected SMTestProxy createChildSuiteOfParentSuite(final String parentName, final String childName) {
    final SMTestProxy parentSuite = createSuiteProxy(parentName);
    final SMTestProxy childSuite = createTestProxy(childName);
    parentSuite.addChild(childSuite);

    return childSuite;
  }

  protected SMTestProxy createChildTestOfSuite(final String suiteName, final String childName) {
    final SMTestProxy suiteProxy = createSuiteProxy(suiteName);
    final SMTestProxy test = createTestProxy(childName);
    suiteProxy.addChild(test);
    return test;
  }

  @Override
  protected SMTestProxy createTestProxy() {
    return createTestProxy(FAKE_TEST_NAME);
  }

  private static void assertIsAnimatorProgressIcon(final Icon icon) {
    for (Icon frame : TestsProgressAnimator.FRAMES) {
      if (icon == frame) {
        return;
      }
    }

    fail("Icon isn't an Animator progress frame");
  }

  private class MyRenderer extends TestTreeRenderer {
    private final UITestUtil.FragmentsContainer myFragmentsContainer;

    public MyRenderer(final boolean isPaused,
                      final UITestUtil.FragmentsContainer fragmentsContainer) {
      super(new SMTRunnerConsoleProperties(createRunConfiguration(), "SMRunnerTests", DefaultDebugExecutor.getDebugExecutorInstance()) {
        @Override
        public boolean isPaused() {
          return isPaused;
        }
      });
      myFragmentsContainer = fragmentsContainer;
    }

    @Override
    public void append(@NotNull @Nls final String fragment,
                       @NotNull final SimpleTextAttributes attributes,
                       final boolean isMainText) {
      myFragmentsContainer.append(fragment, attributes);
    }

    public UITestUtil.FragmentsContainer getFragmentsContainer() {
      return myFragmentsContainer;
    }
  }
}
