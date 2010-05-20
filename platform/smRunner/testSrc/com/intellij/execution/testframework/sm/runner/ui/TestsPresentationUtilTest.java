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
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.PoolOfTestIcons;
import com.intellij.execution.testframework.sm.UITestUtil;
import com.intellij.execution.testframework.sm.runner.BaseSMTRunnerTestCase;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
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

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myRenderer = new MyRenderer(false, new UITestUtil.FragmentsContainer());
    myFragContainer = myRenderer.getFragmentsContainer();
  }

  public void testProgressText() {
    assertEquals("Running: 10 of 1  Failed: 1  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 1, 10, 1, null));
    assertEquals("Running: 10 of 1  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 1, 10, 0, null));
    //here number format is platform-dependent
    assertEquals("Done: 10 of 1  (0 s)  ",
                 TestsPresentationUtil.getProgressStatus_Text(5, 5, 1, 10, 0, null));
  }

  public void testProgressText_UnsetTotal() {
    assertEquals("Running: 0 of <...>  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 0, 0, null));
    assertEquals("Running: 1 of <...>  Failed: 1  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 1, 1, null));
    assertEquals("Running: 10 of <...>  Failed: 1  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 10, 1, null));
    //here number format is platform-dependent
    assertEquals("Done: 10 of <...>  Failed: 1  (5 ms)  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 5, 0, 10, 1, null));
  }

  public void testProgressText_Category() {
    assertEquals("Running: 0 of <...>  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 0, 0, new HashSet<String>()));

    final Set<String> category = new LinkedHashSet<String>();

    category.clear();
    category.add("Scenarios");
    assertEquals("Running: Scenarios 0 of <...>  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 0, 0, category));

    category.clear();
    category.add("Scenarios");
    category.add(TestsPresentationUtil.DEFAULT_TESTS_CATEGORY);
    assertEquals("Running: Scenarios, tests 0 of <...>  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 0, 0, category));

    category.clear();
    category.add("Cucumbers");
    category.add("Tomatos");
    category.add(TestsPresentationUtil.DEFAULT_TESTS_CATEGORY);
    assertEquals("Running: Cucumbers, tomatos, tests 0 of <...>  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 0, 0, category));

    category.clear();
    category.add(TestsPresentationUtil.DEFAULT_TESTS_CATEGORY);
    assertEquals("Running: 0 of <...>  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 0, 0, 0, category));

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

    assertEquals(TestsProgressAnimator.PAUSED_ICON, pausedRenderer.getIcon());
    assertEquals(1, myFragContainer.getFragments().size());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_StartedAndPaused_WithErrors() {
    //paused
    final MyRenderer pausedRenderer = new MyRenderer(true, myFragContainer = new UITestUtil.FragmentsContainer());

    mySimpleTest.setStarted();
    mySimpleTest.addError("msg", "stacktrace");

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
    mySimpleTest.addError("msg", "stacktrace");
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
    mySimpleTest.addError("msg", "stacktrace");
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
    mySimpleTest.addError("msg", "stacktrace");
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

  public void testFormatTestProxyTest_WithErrors() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    mySimpleTest.addError("msg", "stacktrace");
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(SMPoolOfTestIcons.TERMINATED_E_ICON, myRenderer.getIcon());
  }

  public void testFormatRootNodeWithChildren_Started() {
    mySimpleTest.setStarted();

    TestsPresentationUtil.formatRootNodeWithChildren(mySimpleTest, myRenderer);

    assertIsAnimatorProgressIcon(myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Running tests...", myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatRootNodeWithChildren_Failed() {
    final MyRenderer renderer1 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());

    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    mySimpleTest.setFinished();
    mySuite.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer1);

    assertEquals(PoolOfTestIcons.FAILED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results:", renderer1.getFragmentsContainer().getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getFragmentsContainer().getAttribsAt(0));

    final MyRenderer renderer2 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer2);
    mySuite.setFinished();
    assertEquals(PoolOfTestIcons.FAILED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results:", renderer1.getFragmentsContainer().getTextAt(0));
  }

  public void testFormatRootNodeWithChildren_Failed_WithErrors() {
    final MyRenderer renderer1 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());

    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    mySimpleTest.addError("msg", "stacktrace");
    mySimpleTest.setFinished();
    mySuite.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer1);

    assertEquals(SMPoolOfTestIcons.FAILED_E_ICON, renderer1.getIcon());

    final MyRenderer renderer2 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer2);
    mySuite.setFinished();
    assertEquals(SMPoolOfTestIcons.FAILED_E_ICON, renderer1.getIcon());
  }

  public void testFormatRootNodeWithChildren_Error() {
    final MyRenderer renderer1 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());

    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);
    mySimpleTest.addError("msg", "stacktrace");
    mySimpleTest.setFinished();
    mySuite.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer1);

    assertEquals(PoolOfTestIcons.ERROR_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results:", renderer1.getFragmentsContainer().getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getFragmentsContainer().getAttribsAt(0));

    final MyRenderer renderer2 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer2);
    mySuite.setFinished();
    assertEquals(PoolOfTestIcons.ERROR_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results:", renderer1.getFragmentsContainer().getTextAt(0));
  }

  public void testFormatRootNodeWithChildren_Ignored() {
    final MyRenderer renderer1 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());

    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setTestIgnored("", null);
    mySimpleTest.setFinished();
    mySuite.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer1);

    assertEquals(PoolOfTestIcons.IGNORED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results:", renderer1.getFragmentsContainer().getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getFragmentsContainer().getAttribsAt(0));

    final MyRenderer renderer2 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer2);
    mySuite.setFinished();
    assertEquals(PoolOfTestIcons.IGNORED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results:", renderer1.getFragmentsContainer().getTextAt(0));
  }

  public void testFormatRootNodeWithChildren_Ignored_WithErrors() {
    final MyRenderer renderer1 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());

    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setTestIgnored("", null);
    mySimpleTest.addError("msg", "stacktrace");
    mySimpleTest.setFinished();
    mySuite.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer1);

    assertEquals(SMPoolOfTestIcons.IGNORED_E_ICON, renderer1.getIcon());

    final MyRenderer renderer2 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer2);
    mySuite.setFinished();
    assertEquals(SMPoolOfTestIcons.IGNORED_E_ICON, renderer1.getIcon());
  }

  public void testFormatRootNodeWithChildren_Passed() {
    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    mySuite.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, myRenderer);

    assertEquals(PoolOfTestIcons.PASSED_ICON, myRenderer.getIcon());
    assertOneElement(myRenderer.getFragmentsContainer().getFragments());
    assertEquals("Test Results:", myRenderer.getFragmentsContainer().getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myRenderer.getFragmentsContainer().getAttribsAt(0));
  }

  public void testFormatRootNodeWithChildren_Passed_WithErrors() {
    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.addError("msg", "stacktrace");
    mySimpleTest.setFinished();
    mySuite.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, myRenderer);

    assertEquals(SMPoolOfTestIcons.PASSED_E_ICON, myRenderer.getIcon());
  }

  public void testFormatRootNodeWithChildren_Terminated() {
    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    mySuite.setTerminated();
    // terminated
    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, myRenderer);

    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Terminated", myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatRootNodeWithChildren_Terminated_WithErrors() {
    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.addError("msg", "stacktrace");
    mySimpleTest.setFinished();
    mySuite.setTerminated();
    // terminated
    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, myRenderer);

    assertEquals(SMPoolOfTestIcons.TERMINATED_E_ICON, myRenderer.getIcon());
  }

  public void testFormatRootNodeWithChildren_TerminatedAndFinished() {
    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    mySuite.setTerminated();
    mySuite.setFinished();

    // terminated and finished
    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, myRenderer);
    mySuite.setFinished();
    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Terminated", myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatRootNodeWithChildren_Passed_StartShutdownErrors() {
    final MyRenderer renderer1 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());

    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySuite.addError("msg1", "stacktrace1");
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    mySuite.addError("msg2", "stacktrace2");
    mySuite.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer1);
    assertEquals(SMPoolOfTestIcons.PASSED_E_ICON, renderer1.getIcon());

    final MyRenderer renderer2 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatRootNodeWithChildren(mySimpleTest, renderer2);
    assertEquals(SMPoolOfTestIcons.PASSED_ICON, renderer2.getIcon());
  }

  public void testFormatRootNodeWithoutChildren() {
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.NOT_RAN, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("No Test Results", myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.ERROR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_Started() {
    mySimpleTest.setStarted();
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySimpleTest, myRenderer);

    assertIsAnimatorProgressIcon(myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Instantiating tests...", myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_Passed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.PASSED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("All Tests Passed", myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_Terminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Terminated", myFragContainer.getTextAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

  }

  public void testGetPresentableName() {
    //Test unit examples
    assertProxyPresentation("testFirst", "MyRubyTest1", "MyRubyTest1.testFirst");
    assertProxyPresentation("MyRubyTest1.testFirst", "/some/path/on/my/comp", "MyRubyTest1.testFirst");

    //Spec example
    assertProxyPresentation("should be beautifull", "World", "should be beautifull");

    //Common example
    assertProxyPresentation("some phrase", "Begin of", "Begin of some phrase");


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

  protected SMTestProxy createTestProxy() {
    return createTestProxy(FAKE_TEST_NAME);
  }

  private void assertIsAnimatorProgressIcon(final Icon icon) {
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
      super(new SMTRunnerConsoleProperties(createRunConfiguration(), "SMRunnerTests") {
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
