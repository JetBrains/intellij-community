// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.testframework.sm.runner.events.TestDurationStrategy;
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.intellij.execution.testframework.ui.AbstractTestTreeBuilderBase;
import com.intellij.execution.testframework.ui.BaseTestProxyNodeDescriptor;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IdempotenceChecker;
import com.intellij.util.containers.ContainerUtil;
import org.easymock.EasyMock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude;

/**
 * @author Roman Chernyatchik
 */
public class SMTestProxyTest extends BaseSMTRunnerTestCase {

  public void testTestInstance() {
    mySimpleTest = createTestProxy("newTest");

    assertEquals("newTest", mySimpleTest.getName());
    assertEquals("newTest", mySimpleTest.toString());

    assertEmpty(mySimpleTest.getChildren());
    assertTrue(mySimpleTest.isLeaf());
    assertNull(mySimpleTest.getParent());

    assertFalse(mySimpleTest.wasLaunched());
    assertFalse(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());
  }

  public void testGetName() {
    mySimpleTest = createTestProxy("newTest");
    assertEquals("newTest", mySimpleTest.getName());

    mySuite = createSuiteProxy("newSuite");
    assertEquals("newSuite", mySuite.getName());

    mySuite.setParent(mySimpleTest);
    assertEquals("newTest", mySimpleTest.getName());
  }

  public void testGetName_trim() {
    mySimpleTest = createTestProxy(" newTest ");
    assertEquals(" newTest ", mySimpleTest.getName());
  }

  public void testSuiteInstance() {
    mySuite = createSuiteProxy("newSuite");

    assertEquals("newSuite", mySuite.getName());
    assertEquals("newSuite", mySuite.toString());

    assertEmpty(mySuite.getChildren());
    assertTrue(mySuite.isLeaf());
    assertNull(mySuite.getParent());

    assertFalse(mySuite.wasLaunched());
    assertFalse(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());

    mySuite.addChild(mySimpleTest);
    assertEquals("newSuite", mySuite.getName());
    assertEquals("newSuite", mySuite.toString());
    assertSameElements(mySuite.getChildren(), mySimpleTest);
    assertFalse(mySuite.isLeaf());
  }

  public void testAppendedChildToTestShouldMakeItSuite() {
    mySuite = createTestProxy("unroll spock test");
    assertFalse(mySuite.isSuite());
    assertFalse(mySuite.isDefect());
    mySuite.setSuiteStarted();
    assertTrue(mySuite.isSuite());
    assertFalse(mySuite.isDefect());
    mySuite.addChild(mySimpleTest);
    mySimpleTest.setTestFailed("failed message", null, false);
    assertTrue(mySimpleTest.isDefect());
    assertTrue(mySuite.isDefect());
  }

  public void testIsRoot() {
    final SMTestProxy rootTest = createTestProxy("root");
    assertTrue(rootTest.getParent() == null);

    rootTest.addChild(mySimpleTest);

    assertFalse(mySimpleTest.getParent() == null);
  }

  public void testTestStarted() {
    mySimpleTest.setStarted();

    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isInProgress());

    assertFalse(mySimpleTest.isDefect());
  }

  public void testTestStarted_InSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    assertTrue(mySuite.wasLaunched());
    assertTrue(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());
    assertFalse(mySimpleTest.wasLaunched());
    assertFalse(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());

    mySimpleTest.setStarted();

    assertTrue(mySuite.wasLaunched());
    assertTrue(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());
  }

  public void testTestFinished() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();

    assertTrue(mySimpleTest.wasLaunched());

    assertFalse(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());
    assertFalse(mySuite.wasTerminated());
  }

  public void testTestFinished_InSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    mySimpleTest.setStarted();
    mySimpleTest.setFinished();

    assertTrue(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());
    assertFalse(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());

    mySuite.setFinished();

    assertFalse(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());
  }

  public void testTestFinished_InSuite_WrongOrder() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    mySimpleTest.setStarted();
    mySuite.setFinished();

    assertFalse(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());

    assertTrue(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());
  }

  public void testTestFailed() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isDefect());
    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.FAILED_INDEX);

    mySimpleTest.setFinished();

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isDefect());

    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.FAILED_INDEX);
  }

  public void testTestFailedTwice() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("msg 1", "stack trace 1", false);

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isDefect());
    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.FAILED_INDEX);
    final MockPrinter printer = new MockPrinter();
    mySimpleTest.printOn(printer);
    assertEquals("", printer.getStdOut());
    assertEquals("\nmsg 1\nstack trace 1\n", printer.getStdErr());
    assertEquals("", printer.getStdSys());

    mySimpleTest.setTestFailed("msg 2", "stack trace 2", false);

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isDefect());
    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.FAILED_INDEX);
    printer.resetIfNecessary();
    mySimpleTest.printOn(printer);
    assertEquals("", printer.getStdOut());
    assertEquals("\nmsg 1\nstack trace 1\n\nmsg 2\nstack trace 2\n", printer.getStdErr());
    assertEquals("", printer.getStdSys());

    mySimpleTest.setFinished();

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isDefect());
    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.FAILED_INDEX);
  }

  public void testMultipleAssertions() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestComparisonFailed("a", "stacktrace", "actual1", "expected1");
    mySimpleTest.setTestComparisonFailed("b", "stacktrace", "actual2", "expected2");
    mySimpleTest.setTestFailed("c", "stacktrace", false);
    mySimpleTest.setFinished();

    final MockPrinter printer = new MockPrinter();
    printer.setShowHyperLink(true);
    mySimpleTest.printOn(printer);
    assertEquals("", printer.getStdOut());
    assertEquals("""

                   a
                   Expected :expected1
                   Actual   :actual1
                   <Click to see difference>

                   stacktrace

                   b
                   Expected :expected2
                   Actual   :actual2
                   <Click to see difference>

                   stacktrace

                   c
                   stacktrace
                   """, printer.getAllOut());
  }

  public void testTestFailed_ComparisonAssertion() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestComparisonFailed("", "", "", "");

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isDefect());
    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.FAILED_INDEX);

    mySimpleTest.setFinished();

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isDefect());
    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.FAILED_INDEX);
  }

  public void testTestFailed_InSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());
    assertTrue(mySimpleTest.isDefect());

    mySimpleTest.setFinished();

    assertTrue(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    mySuite.setFinished();

    assertFalse(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.FAILED_INDEX);
    assertTrue(mySuite.getMagnitudeInfo() == Magnitude.FAILED_INDEX);
  }

  public void testTestIgnored() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestIgnored("", null);

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isDefect());
    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.IGNORED_INDEX);

    mySimpleTest.setFinished();

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isDefect());

    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.IGNORED_INDEX);
  }

  public void testTestIgnored_InSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    mySimpleTest.setStarted();
    mySimpleTest.setTestIgnored("", null);

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());
    assertTrue(mySimpleTest.isDefect());

    mySimpleTest.setFinished();

    assertTrue(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    mySuite.setFinished();

    assertFalse(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.IGNORED_INDEX);
    assertTrue(mySuite.getMagnitudeInfo() == Magnitude.IGNORED_INDEX);
  }

  public void testTestError() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isDefect());
    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.ERROR_INDEX);

    mySimpleTest.setFinished();

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isDefect());

    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.ERROR_INDEX);
  }

  public void testTestError_InSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);

    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());
    assertTrue(mySimpleTest.isDefect());

    mySimpleTest.setFinished();

    assertTrue(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    mySuite.setFinished();

    assertFalse(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.ERROR_INDEX);
    assertTrue(mySuite.getMagnitudeInfo() == Magnitude.ERROR_INDEX);
  }

  public void testSuiteFailed_WithPendingAndFailed() {
    final SMTestProxy testPending = createTestProxy("pending");

    mySuite.setStarted();

    // failed test
    mySuite.addChild(mySimpleTest);
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    mySimpleTest.setFinished();

    // pending test
    mySuite.addChild(testPending);
    testPending.setStarted();
    testPending.setTestIgnored("todo", null);

    assertFalse(testPending.isInProgress());
    assertTrue(mySuite.isInProgress());
    assertTrue(testPending.isDefect());
    assertTrue(mySuite.isDefect());

    testPending.setFinished();

    // check that suite is failed
    assertTrue(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    mySuite.setFinished();

    assertFalse(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.FAILED_INDEX);
    assertTrue(testPending.getMagnitudeInfo() == Magnitude.IGNORED_INDEX);
    assertTrue(mySuite.getMagnitudeInfo() == Magnitude.FAILED_INDEX);
  }

  public void testSuitePending_WithPendingAndPassed() {
    final SMTestProxy testPending = createTestProxy("pending");

    mySuite.setStarted();

    // passed test
    mySuite.addChild(mySimpleTest);
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();

    // pending test
    mySuite.addChild(testPending);
    testPending.setStarted();
    testPending.setTestIgnored("todo", null);

    assertTrue(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    testPending.setFinished();

    // check that suite is failed
    assertTrue(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    mySuite.setFinished();

    assertFalse(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    assertTrue(mySimpleTest.getMagnitudeInfo() == Magnitude.PASSED_INDEX);
    assertTrue(testPending.getMagnitudeInfo() == Magnitude.IGNORED_INDEX);
    assertTrue(mySuite.getMagnitudeInfo() == Magnitude.IGNORED_INDEX);
  }

  public void testSuiteTerminated() {
    mySuite.setStarted();
    mySuite.setTerminated();

    assertFalse(mySuite.isInProgress());

    assertTrue(mySuite.wasLaunched());
    assertTrue(mySuite.isDefect());
    assertTrue(mySuite.wasTerminated());

    mySuite.setFinished();
    assertTrue(mySuite.wasTerminated());
  }

  public void testSuiteTerminated_WithNotRunChild() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    mySuite.setTerminated();

    assertTrue(mySuite.wasTerminated());
    assertTrue(mySimpleTest.wasTerminated());
  }

  public void testSuiteTerminated_WithChildInProgress() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);
    mySimpleTest.setStarted();

    mySuite.setTerminated();

    assertTrue(mySuite.wasTerminated());
    assertTrue(mySimpleTest.wasTerminated());
  }

  public void testSuiteTerminated_WithChildInFinalState() {
    final SMTestProxy testPassed = createTestProxy("passed");
    final SMTestProxy testFailed = createTestProxy("failed");
    final SMTestProxy testInProgress = createTestProxy("inProgress");

    mySuite.setStarted();

    mySuite.addChild(testPassed);
    testPassed.setStarted();
    testPassed.setFinished();

    mySuite.addChild(testFailed);
    testFailed.setStarted();
    testFailed.setTestFailed("", "", false);
    testFailed.setFinished();

    mySuite.addChild(testInProgress);
    testInProgress.setStarted();

    // Suite terminated
    mySuite.setTerminated();

    assertTrue(mySuite.wasTerminated());
    assertFalse(testPassed.wasTerminated());
    assertFalse(testFailed.wasTerminated());
    assertTrue(testInProgress.wasTerminated());
  }

  public void testTestTerminated() {
    mySimpleTest.setTerminated();

    assertTrue(mySimpleTest.isDefect());
    assertTrue(mySimpleTest.wasTerminated());
    assertTrue(mySimpleTest.wasLaunched());

    assertFalse(mySimpleTest.isInProgress());

    mySimpleTest.setFinished();
    assertTrue(mySimpleTest.wasTerminated());
  }

  public void testMagnitude() {
    assertEquals(Magnitude.NOT_RUN_INDEX.getValue(), mySuite.getMagnitude());

    final SMTestProxy passedTest = createTestProxy("passed");
    final SMTestProxy failedTest = createTestProxy("failed");
    mySuite.addChild(passedTest);
    mySuite.addChild(failedTest);

    assertEquals(Magnitude.NOT_RUN_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(Magnitude.NOT_RUN_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(Magnitude.NOT_RUN_INDEX.getValue(), failedTest.getMagnitude());

    mySuite.setStarted();
    assertEquals(Magnitude.RUNNING_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(Magnitude.NOT_RUN_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(Magnitude.NOT_RUN_INDEX.getValue(), failedTest.getMagnitude());

    passedTest.setStarted();
    assertEquals(Magnitude.RUNNING_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(Magnitude.RUNNING_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(Magnitude.NOT_RUN_INDEX.getValue(), failedTest.getMagnitude());

    passedTest.setFinished();
    assertEquals(Magnitude.RUNNING_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(Magnitude.PASSED_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(Magnitude.NOT_RUN_INDEX.getValue(), failedTest.getMagnitude());

    failedTest.setStarted();
    assertEquals(Magnitude.RUNNING_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(Magnitude.PASSED_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(Magnitude.RUNNING_INDEX.getValue(), failedTest.getMagnitude());

    failedTest.setTestFailed("", "", false);
    assertEquals(Magnitude.RUNNING_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(Magnitude.PASSED_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(Magnitude.FAILED_INDEX.getValue(), failedTest.getMagnitude());

    mySuite.setFinished();
    assertEquals(Magnitude.FAILED_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(Magnitude.PASSED_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(Magnitude.FAILED_INDEX.getValue(), failedTest.getMagnitude());
  }

  public void testMagnitude_Error() {
    assertEquals(Magnitude.NOT_RUN_INDEX.getValue(), mySuite.getMagnitude());

    final SMTestProxy passedTest = createTestProxy("passed");
    final SMTestProxy failedTest = createTestProxy("failed");
    final SMTestProxy errorTest = createTestProxy("error");
    mySuite.addChild(passedTest);
    mySuite.addChild(failedTest);
    mySuite.addChild(errorTest);

    mySuite.setStarted();
    passedTest.setStarted();
    passedTest.setFinished();
    failedTest.setStarted();
    failedTest.setTestFailed("", "", false);
    failedTest.setFinished();
    errorTest.setStarted();
    errorTest.setTestFailed("", "", true);
    errorTest.setFinished();

    assertEquals(Magnitude.RUNNING_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(Magnitude.PASSED_INDEX.getValue(), passedTest.getMagnitude());
    assertEquals(Magnitude.FAILED_INDEX.getValue(), failedTest.getMagnitude());
    assertEquals(Magnitude.ERROR_INDEX.getValue(), errorTest.getMagnitude());
  }

  public void testMagnitude_Terminated() {
    assertEquals(Magnitude.NOT_RUN_INDEX.getValue(), mySuite.getMagnitude());

    final SMTestProxy testProxy = createTestProxy("failed");
    mySuite.addChild(testProxy);

    assertEquals(Magnitude.NOT_RUN_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(Magnitude.NOT_RUN_INDEX.getValue(), testProxy.getMagnitude());

    mySuite.setStarted();
    mySuite.setTerminated();
    assertEquals(Magnitude.TERMINATED_INDEX.getValue(), mySuite.getMagnitude());
    assertEquals(Magnitude.TERMINATED_INDEX.getValue(), testProxy.getMagnitude());
  }

  public void testMagnitude_suiteWithoutTests() {
    final SMTestProxy noTests = createSuiteProxy("emptySuite");
    noTests.setStarted();
    noTests.setFinished();
    assertEquals(Magnitude.COMPLETE_INDEX.getValue(), noTests.getMagnitude());
  }

  public void testMagnitude_PassedSuite() {
    final SMTestProxy passedSuite = createSuiteProxy("passedSuite");
    final SMTestProxy passedSuiteTest = createTestProxy("test");
    passedSuite.setStarted();
    passedSuite.addChild(passedSuiteTest);
    passedSuiteTest.setStarted();
    passedSuiteTest.setFinished();
    passedSuite.setFinished();
    assertEquals(Magnitude.PASSED_INDEX.getValue(), passedSuite.getMagnitude());
  }

  public void testLocation() {
    Project project = getProject();
    GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    assertNull(mySuite.getLocation(project, allScope));

    mySuite.addChild(mySimpleTest);

    assertNull(mySuite.getLocation(project, allScope));
    assertNull(mySimpleTest.getLocation(project, allScope));
    PsiFile testFile = createFile("test.txt", MockTestLocator.TEST_LOCATION_TEXT);
    Location<PsiFile> testFileLocation = PsiLocation.fromPsiElement(testFile);
    MockTestLocator locator = new MockTestLocator(testFileLocation);
    mySimpleTest.setLocator(locator);
    assertEquals(testFileLocation, mySimpleTest.getLocation(project, allScope));
    assertEquals(List.of(allScope), locator.myCalledSearchScopes);

    assertEquals(testFileLocation, mySimpleTest.getLocation(project, allScope));
    assertEquals(List.of(allScope), locator.myCalledSearchScopes);

    GlobalSearchScope notAllScope = GlobalSearchScope.notScope(allScope);
    assertEquals(testFileLocation, mySimpleTest.getLocation(project, notAllScope));
    assertEquals(List.of(allScope, notAllScope), locator.myCalledSearchScopes);

    WriteAction.run(() -> {
      PsiDocumentManager.getInstance(project).getDocument(testFile).setText("");
      PsiDocumentManager.getInstance(project).commitAllDocuments(); // to rebuild PSI and invalidate cache
    });

    assertNull(null, mySimpleTest.getLocation(project, allScope));
    assertEquals(List.of(allScope, notAllScope, allScope), locator.myCalledSearchScopes);

    assertNull(null, mySimpleTest.getLocation(project, allScope));
    assertEquals(List.of(allScope, notAllScope, allScope), locator.myCalledSearchScopes);

    assertNull(null, mySimpleTest.getLocation(project, allScope));
    assertEquals(List.of(allScope, notAllScope, allScope), locator.myCalledSearchScopes);
  }

  public void testNavigatable() {
    TestConsoleProperties properties = EasyMock.createMock(TestConsoleProperties.class);

    assertNull(mySuite.getDescriptor(null, properties));

    mySuite.addChild(mySimpleTest);
    assertNull(mySuite.getDescriptor(null, properties));
    assertNull(mySimpleTest.getDescriptor(null, properties));
  }

  public void testShouldRun_Test() {
    assertTrue(mySimpleTest.shouldRun());
  }

  public void testShouldRun_Suite() {
    assertTrue(mySuite.shouldRun());

    mySuite.addChild(mySimpleTest);
    assertTrue(mySuite.shouldRun());

    mySimpleTest.setStarted();
    assertTrue(mySuite.shouldRun());
  }

  public void testShouldRun_StartedTest() {
    mySimpleTest.setStarted();
    assertTrue(mySimpleTest.shouldRun());
  }

  public void testShouldRun_StartedSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);
    assertTrue(mySuite.shouldRun());

    mySimpleTest.setStarted();
    assertTrue(mySuite.shouldRun());
  }

  public void testShouldRun_FailedTest() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertTrue(mySimpleTest.shouldRun());
  }

  public void testShouldRun_FailedSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);

    assertTrue(mySuite.shouldRun());
  }

  public void testShouldRun_ErrorSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);

    assertTrue(mySuite.shouldRun());
  }

  public void testShouldRun_PassedTest() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    assertTrue(mySimpleTest.shouldRun());
  }

  public void testShouldRun_PassedSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();

    assertTrue(mySuite.shouldRun());
  }

  public void testFilter() {
    //noinspection unchecked
    assertEmpty(mySuite.getChildren(Filter.NO_FILTER));
    assertEmpty(mySuite.getChildren(null));

    mySuite.addChild(mySimpleTest);

    //noinspection unchecked
    assertEquals(1, mySuite.getChildren(Filter.NO_FILTER).size());
    assertEquals(1, mySuite.getChildren(null).size());
  }

  public void testGetAllTests() {
    assertOneElement(mySuite.getAllTests());

    final SMTestProxy suite1 = createTestProxy("newTest");
    mySuite.addChild(suite1);
    final SMTestProxy test11 = createTestProxy("newTest");
    suite1.addChild(test11);

    final SMTestProxy suite2 = createTestProxy("newTest");
    suite1.addChild(suite2);
    final SMTestProxy test21 = createTestProxy("newTest");
    suite2.addChild(test21);
    final SMTestProxy test22 = createTestProxy("newTest");
    suite2.addChild(test22);

    assertEquals(6, mySuite.getAllTests().size());
    assertEquals(5, suite1.getAllTests().size());
    assertEquals(3, suite2.getAllTests().size());
    assertOneElement(test11.getAllTests());
    assertOneElement(test21.getAllTests());
  }

  public void testIsSuite() {
    assertFalse(mySimpleTest.isSuite());

    mySimpleTest.setStarted();
    assertFalse(mySimpleTest.isSuite());

    final SMTestProxy suite = mySuite;
    assertTrue(suite.isSuite());

    suite.setStarted();
    assertTrue(suite.isSuite());
  }

  public void testDuration_ForTest() {
    assertNull(mySimpleTest.getDuration());

    mySimpleTest.setDuration(0);
    Long duration = mySimpleTest.getDuration();
    assertNotNull(duration);
    assertEquals(0, duration.longValue());

    mySimpleTest.setDuration(10);
    duration = mySimpleTest.getDuration();
    assertNotNull(duration);
    assertEquals(10, duration.longValue());

    mySimpleTest.setDuration(5);
    duration = mySimpleTest.getDuration();
    assertNotNull(duration);
    assertEquals(5, duration.longValue());

    mySimpleTest.setDuration(-2);
    assertNull(mySimpleTest.getDuration());
  }

  public void testDuration_ForSuiteEmpty() {
    final SMTestProxy suite = createSuiteProxy("root");
    assertNull(suite.getDuration());
  }

  public void testSetDuration_Suite() {
    mySuite.setDuration(5);
    assertNull(mySuite.getDuration());

    final SMTestProxy test = createTestProxy("test", mySuite);
    test.setDuration(2);
    mySuite.setDuration(5);
    final Long duration = mySuite.getDuration();
    assertNotNull(duration);
    assertEquals(2L, duration.longValue());
  }

  public void testSetDuration_Suite_ManualStrategy() {
    SMTestProxy.SMRootTestProxy root = new SMTestProxy.SMRootTestProxy();
    root.setDurationStrategy(TestDurationStrategy.MANUAL);
    SMTestProxy suite = createSuiteProxy("suite");
    root.addChild(suite);

    suite.setDuration(500);
    Long duration = suite.getDuration();
    assertNotNull(duration);
    assertEquals(500L, duration.longValue());
  }

  public void testSetDuration_Suite_AutomaticStrategy() {
    SMTestProxy.SMRootTestProxy root = new SMTestProxy.SMRootTestProxy();
    root.setDurationStrategy(TestDurationStrategy.AUTOMATIC);
    SMTestProxy suite = createSuiteProxy("suite");
    root.addChild(suite);

    suite.setDuration(500);
    Long duration = suite.getDuration();
    assertNull(duration);
  }

  public void testDuration_ForSuiteWithTests() {
    final SMTestProxy suite = createSuiteProxy("root");
    final SMTestProxy test1 = createTestProxy("test1", suite);
    final SMTestProxy test2 = createTestProxy("test2", suite);

    assertNull(suite.getDuration());

    test1.setDuration(5);
    Long duration = suite.getDuration();
    assertNotNull(duration);
    assertEquals(5L, duration.longValue());

    test2.setDuration(6);
    duration = suite.getDuration();
    assertNotNull(duration);
    assertEquals(11L, duration.longValue());
  }

  public void testDuration_OnFinished() {
    final SMTestProxy suite = createSuiteProxy("root");
    final SMTestProxy test = createTestProxy("test1", suite);

    assertNull(suite.getDuration());

    test.setDuration(5);
    Long duration = suite.getDuration();
    assertNotNull(duration);
    assertEquals(5L, duration.longValue());

    test.setDuration(7);
    duration = suite.getDuration();
    assertNotNull(duration);
    assertEquals(7L, duration.longValue());

    suite.setFinished();
    duration = suite.getDuration();
    assertNotNull(duration);
    assertEquals(7L, duration.longValue());

    test.setDuration(8);
    duration = suite.getDuration();
    assertNotNull(duration);
    assertEquals(8L, duration.longValue());
  }

  public void testDuration_OnTerminated() {
    final SMTestProxy suite = createSuiteProxy("root");
    final SMTestProxy test = createTestProxy("test1", suite);

    assertNull(suite.getDuration());

    test.setDuration(5);
    Long duration = suite.getDuration();
    assertNotNull(duration);
    assertEquals(5L, duration.longValue());

    test.setDuration(7);
    duration = suite.getDuration();
    assertNotNull(duration);
    assertEquals(7L, duration.longValue());


    suite.setTerminated();
    duration = suite.getDuration();
    assertNotNull(duration);
    assertEquals(0L, duration.longValue());

    test.setDuration(8);
    duration = suite.getDuration();
    assertNotNull(duration);
    assertEquals(8L, duration.longValue());
  }

  public void testDuration_ForSuiteWithSuites() {
    final SMTestProxy root = createSuiteProxy("root");
    final SMTestProxy suite1 = createSuiteProxy("suite1", root);
    final SMTestProxy suite2 = createSuiteProxy("suite2", root);

    final SMTestProxy test11 = createTestProxy("test11", suite1);
    final SMTestProxy test12 = createTestProxy("test12", suite1);
    final SMTestProxy test21 = createTestProxy("test21", suite2);

    test11.setDuration(5);
    Long duration = root.getDuration();
    assertNotNull(duration);
    assertEquals(5L, duration.longValue());

    test12.setDuration(6);
    duration = root.getDuration();
    assertNotNull(duration);
    assertEquals(11, duration.longValue());

    test21.setDuration(9);
    duration = root.getDuration();
    assertNotNull(duration);
    assertEquals(20, duration.longValue());
  }

  public void testMagnitudeWeight() {
    assertWeightsOrder(Magnitude.NOT_RUN_INDEX, Magnitude.SKIPPED_INDEX);
    assertWeightsOrder(Magnitude.SKIPPED_INDEX, Magnitude.IGNORED_INDEX);
    assertWeightsOrder(Magnitude.IGNORED_INDEX, Magnitude.COMPLETE_INDEX);
    assertEquals(Magnitude.COMPLETE_INDEX.getSortWeight(), Magnitude.PASSED_INDEX.getSortWeight());
    assertWeightsOrder(Magnitude.PASSED_INDEX, Magnitude.FAILED_INDEX);
    assertWeightsOrder(Magnitude.FAILED_INDEX, Magnitude.ERROR_INDEX);
    assertWeightsOrder(Magnitude.ERROR_INDEX, Magnitude.TERMINATED_INDEX);
    assertWeightsOrder(Magnitude.TERMINATED_INDEX, Magnitude.RUNNING_INDEX);
  }

  public void testEmptySuite_isntDefect() {
    mySuite.setStarted();
    mySuite.setFinished();

    assertEmpty(mySuite.getChildren());
    assertFalse(mySuite.isDefect());
    assertEquals(Magnitude.COMPLETE_INDEX, mySuite.getMagnitudeInfo());
  }

  public void testIsEmpty_EmptySuiteNotRun() {
    final SMTestProxy root = createSuiteProxy("root");

    assertEquals(Magnitude.NOT_RUN_INDEX, root.getMagnitudeInfo());
    assertTrue(root.isEmptySuite());
  }

  public void testIsEmpty_SuiteNotRun() {
    final SMTestProxy root = createSuiteProxy("root");

    final SMTestProxy suite1 = createSuiteProxy("suite1", root);
    createTestProxy("test11", suite1);

    assertEquals(Magnitude.NOT_RUN_INDEX, root.getMagnitudeInfo());
    assertFalse(root.isEmptySuite());
  }

  public void testIsEmpty_EmptySuiteInProgress() {
    final SMTestProxy root = createSuiteProxy("root");
    root.setStarted();

    assertEquals(Magnitude.RUNNING_INDEX, root.getMagnitudeInfo());
    assertTrue(root.isEmptySuite());
  }

  public void testIsEmpty_EmptySuiteFinished() {
    final SMTestProxy root = createSuiteProxy("root");
    root.setFinished();

    assertEquals(Magnitude.COMPLETE_INDEX, root.getMagnitudeInfo());
    assertTrue(root.isEmptySuite());
  }

  public void testIsEmpty_EmptySuiteWithSubSuite_NotRun() {
    final SMTestProxy root = createSuiteProxy("root");

    createSuiteProxy("suite1", root);

    assertEquals(Magnitude.NOT_RUN_INDEX, root.getMagnitudeInfo());
    assertTrue(root.isEmptySuite());
  }

  public void testIsEmpty_EmptySuiteWithSubSuite_InProgress() {
    final SMTestProxy root = createSuiteProxy("root");
    root.setStarted();

    final SMTestProxy suite1 = createSuiteProxy("suite1", root);
    suite1.setStarted();

    assertEquals(Magnitude.RUNNING_INDEX, root.getMagnitudeInfo());
    assertTrue(root.isEmptySuite());
  }

  public void testIsEmpty_EmptySuiteWithSubSuite_Finished() {
    final SMTestProxy root = createSuiteProxy("root");
    root.setStarted();

    final SMTestProxy suite1 = createSuiteProxy("suite1", root);
    suite1.setStarted();
    suite1.setFinished();
    root.setFinished();

    assertEquals(Magnitude.COMPLETE_INDEX, root.getMagnitudeInfo());
    assertTrue(root.isEmptySuite());
  }


  public void testIsEmpty_SuiteWithSubSuite_InProgress() {
    final SMTestProxy root = createSuiteProxy("root");
    root.setStarted();

    final SMTestProxy suite1 = createSuiteProxy("suite1", root);
    suite1.setStarted();

    final SMTestProxy test11 = createTestProxy("test11", suite1);
    test11.setStarted();

    assertEquals(Magnitude.RUNNING_INDEX, root.getMagnitudeInfo());
    assertFalse(root.isEmptySuite());
  }

  public void testIsEmpty_Caching() {
    final SMTestProxy root = createSuiteProxy("root");
    root.setStarted();

    assertTrue(root.isEmptySuite());

    final SMTestProxy suite1 = createSuiteProxy("suite1", root);
    suite1.setStarted();

    assertTrue(suite1.isEmptySuite());

    final SMTestProxy test11 = createTestProxy("test11", suite1);
    test11.setStarted();
    test11.setFinished();

    assertFalse(suite1.isEmptySuite());

    suite1.setFinished();
    root.setFinished();

    assertEquals(Magnitude.PASSED_INDEX, root.getMagnitudeInfo());
    assertFalse(root.isEmptySuite());
  }

  public void testDisplayTimeAfterTermination() {
    SMTestProxy root = createSuiteProxy("root");
    SMTestProxy firstSubSuite = createSuiteProxy("firstSubSuite", root);
    SMTestProxy secondSubSuite = createSuiteProxy("secondSubSuite", root);
    SMTestProxy firstSubSuiteChild = createTestProxy("firstSubSuiteChild", firstSubSuite);
    SMTestProxy secondSubSuiteChild = createTestProxy("secondSubSuiteChild", secondSubSuite);

    root.setStarted();
    firstSubSuite.setStarted();
    firstSubSuiteChild.setStarted();
    secondSubSuite.setStarted();
    secondSubSuiteChild.setStarted();
    root.setTerminated();
    firstSubSuiteChild.setDuration(10L);
    secondSubSuiteChild.setDuration(5L);

    assertDisplayTimeEqualsToSumOfChildren(firstSubSuite);
    assertDisplayTimeEqualsToSumOfChildren(secondSubSuite);
    assertDisplayTimeEqualsToSumOfChildren(root);
  }

  public void testDisplayTimeShowsZeroForNonStartedTestsAfterTermination() {
    SMTestProxy root = createSuiteProxy("root");
    SMTestProxy firstChild = createTestProxy("firstChild", root);
    SMTestProxy secondChild = createTestProxy("secondChild", root);

    root.setStarted();
    firstChild.setStarted();
    root.setTerminated();
    firstChild.setDuration(5L);

    assertNotNull(secondChild.getDuration());
    assertEquals(0L, secondChild.getDuration().longValue());
    assertDisplayTimeEqualsToSumOfChildren(root);
  }

  public void testDisplayTimeAfterTerminationHasNoEffectForPassedAndFailedTests() {
    SMTestProxy root = createSuiteProxy("root");
    SMTestProxy passedChild = createTestProxy("passedChild", root);
    SMTestProxy failedChild = createTestProxy("failedChild", root);
    SMTestProxy ignoredChild = createTestProxy("ignoredChild", root);
    SMTestProxy runningChild = createTestProxy("runningChild", root);

    root.setStarted();

    passedChild.setStarted();
    passedChild.setFinished();
    passedChild.setDuration(10L);
    Long passedChildDuration = passedChild.getDuration();

    failedChild.setStarted();
    failedChild.setTestFailed("message", "stacktrace", true);
    failedChild.setDuration(5L);
    Long failedChildDuration = failedChild.getDuration();

    ignoredChild.setStarted();
    ignoredChild.setTestIgnored("message", "stacktrace");
    Long ignoredChildDuration = ignoredChild.getDuration();

    runningChild.setStarted();
    root.setTerminated();

    assertEquals(passedChildDuration, passedChild.getDuration());
    assertEquals(failedChildDuration, failedChild.getDuration());
    assertNull(ignoredChildDuration);
    assertEquals(ignoredChildDuration, ignoredChild.getDuration());
    assertDisplayTimeEqualsToSumOfChildren(root);
  }

  public void testDisplayOwnTime() {
    SMTestProxy root = createSuiteProxy("root");
    SMTestProxy child1 = createTestProxy("child1", root);
    SMTestProxy child2 = createTestProxy("child12", root);

    root.setStarted();

    child1.setStarted();
    child1.setFinished();
    child1.setDuration(10L);

    child2.setStarted();
    child2.setFinished();
    child2.setDuration(10L);


    root.setFinished();
    root.setDuration(60_000_000_000L);

    assertDisplayTimeEqualsToSumOfChildren(root);
    assertTrue(root.getEndTimeMillis() - root.getStartTimeMillis() != root.getDuration());
  }

  public void testDisplayOwnTimeTerminated() {
    SMTestProxy root = createSuiteProxy("root");
    SMTestProxy child1 = createTestProxy("child1", root);
    SMTestProxy child2 = createTestProxy("child12", root);

    root.setStarted();

    child1.setStarted();

    root.setTerminated();

    assertNotNull(root.getEndTimeMillis());
    assertNotNull(child1.getEndTimeMillis());
    assertNotNull(child2.getEndTimeMillis());
  }

  public void testTerminatedWithNonFinished() {
    SMTestProxy root = createSuiteProxy("root");
    SMTestProxy suite1 = createSuiteProxy("suite1", root);
    SMTestProxy suite2 = createSuiteProxy("suite2", root);

    root.setStarted();

    suite1.setStarted();
    suite1.setFinished();

    SMTestProxy test1 = createTestProxy("test1", suite1);
    SMTestProxy test2 = createTestProxy("test2", suite2);

    test1.setStarted();
    suite2.setStarted();
    test2.setStarted();

    root.setTerminated();

    assertNotNull(root.getEndTimeMillis());
    assertNotNull(suite1.getEndTimeMillis());
    assertNotNull(suite2.getEndTimeMillis());
    assertNotNull(test1.getEndTimeMillis());
    assertNotNull(test2.getEndTimeMillis());

    assertTrue(root.wasTerminated());
    assertTrue(suite1.isPassed());
    assertTrue(suite2.wasTerminated());
    assertTrue(test1.wasTerminated());
    assertTrue(test2.wasTerminated());
  }


  public void testSortByDuration_usesCustomizedDuration() {
    // dynamicTests1: getCustomizedDuration()=2200, getDuration() sum=200
    SMTestProxy suite1 = new SMTestProxy("dynamicTests1", true, null) {
      @Override
      public @NotNull Long getCustomizedDuration(@NotNull TestConsoleProperties props) {
        return 2200L;
      }
    };
    createTestProxy("Test 1", suite1).setDuration(100L);
    createTestProxy("Test 2", suite1).setDuration(100L);

    // dynamicTests2: getCustomizedDuration()=1000, getDuration() sum=1000
    SMTestProxy suite2 = new SMTestProxy("dynamicTests2", true, null) {
      @Override
      public @NotNull Long getCustomizedDuration(@NotNull TestConsoleProperties props) {
        return 1000L;
      }
    };
    createTestProxy("Test 1", suite2).setDuration(500L);
    createTestProxy("Test 2", suite2).setDuration(500L);

    // Sanity: raw getDuration() would incorrectly sort suite2 first (pre-fix behaviour)
    assertEquals(200L, (long)suite1.getDuration());
    assertEquals(1000L, (long)suite2.getDuration());

    TestConsoleProperties properties = createConsoleProperties();
    TestConsoleProperties.SORT_BY_DURATION.set(properties, true);

    TestFrameworkRunningModel model = new TestFrameworkRunningModel() {
      @Override public TestConsoleProperties getProperties() { return properties; }
      @Override public void setFilter(@NotNull Filter<?> filter) {}
      @Override public boolean isRunning() { return false; }
      @Override public TestTreeView getTreeView() { return null; }
      @Override public AbstractTestTreeBuilderBase<?> getTreeBuilder() { return null; }
      @Override public boolean hasTestSuites() { return false; }
      @Override public AbstractTestProxy getRoot() { return null; }
      @Override public void selectAndNotify(AbstractTestProxy proxy) {}
      @Override public void dispose() {}
    };

    SMTestProxy root = createSuiteProxy("root");
    root.addChild(suite1);
    root.addChild(suite2);
    NodeDescriptor<?> parentDesc = new BaseTestProxyNodeDescriptor<>(getProject(), root, null);
    BaseTestProxyNodeDescriptor<SMTestProxy> desc1 = new BaseTestProxyNodeDescriptor<>(getProject(), suite1, parentDesc);
    BaseTestProxyNodeDescriptor<SMTestProxy> desc2 = new BaseTestProxyNodeDescriptor<>(getProject(), suite2, parentDesc);

    Comparator<NodeDescriptor<?>> comparator = model.createComparator();

    assertTrue("suite1 (wall=2200 ms) must sort before suite2 (wall=1000 ms)",
               comparator.compare(desc1, desc2) < 0);
  }

  private static void assertDisplayTimeEqualsToSumOfChildren(@NotNull SMTestProxy node) {
    List<? extends SMTestProxy> children = node.collectChildren(new Filter<>() {
      @Override
      public boolean shouldAccept(SMTestProxy test) {
        return test.isLeaf();
      }
    });
    assertTrue(ContainerUtil.all(children, child -> !child.wasTerminated() || child.getDuration() != null));
    Long totalTime = ContainerUtil.reduce(ContainerUtil.map(children, child -> child.getDuration() == null ? 0 : child.getDuration()), 0L,
                                          (a, b) -> a + b);
    assertNotNull(node.getDuration());
    assertEquals(node.getDuration(), totalTime);
  }

  protected static void assertWeightsOrder(final Magnitude previous, final Magnitude next) {
    assertTrue(previous.getSortWeight() < next.getSortWeight());
  }

  // ── Declaration-order sorting tests (regression for KTIJ-34747) ──────────

  /**
   * Normal case: both tests have resolvable PSI locations → sorted by text offset.
   */
  public void testSortByDeclarationOrder_sortedByTextOffset() {
    TestConsoleProperties properties = createConsoleProperties();
    TestConsoleProperties.SORT_BY_DURATION.set(properties, false);
    TestConsoleProperties.SORT_ALPHABETICALLY.set(properties, false);
    TestConsoleProperties.SORT_BY_DECLARATION_ORDER.set(properties, true);
    TestConsoleProperties.SUITES_ALWAYS_ON_TOP.set(properties, false);

    TestFrameworkRunningModel model = createModelFor(properties);

    SMTestProxy root  = createSuiteProxy("root");
    SMTestProxy test1 = proxyAtOffset("test1", root, 10);
    SMTestProxy test2 = proxyAtOffset("test2", root, 20);

    NodeDescriptor<?> parentDesc = new BaseTestProxyNodeDescriptor<>(getProject(), root, null);
    BaseTestProxyNodeDescriptor<SMTestProxy> desc1 = new BaseTestProxyNodeDescriptor<>(getProject(), test1, parentDesc);
    BaseTestProxyNodeDescriptor<SMTestProxy> desc2 = new BaseTestProxyNodeDescriptor<>(getProject(), test2, parentDesc);

    Comparator<NodeDescriptor<?>> comparator = model.createComparator();
    assertTrue("test1 (offset=10) must sort before test2 (offset=20)", comparator.compare(desc1, desc2) < 0);
    assertTrue("test2 (offset=20) must sort after test1 (offset=10)",  comparator.compare(desc2, desc1) > 0);
  }

  /**
   * Regression test for KTIJ-34747: non-JVM KMP targets whose {@code getLocation()} returns null
   * must not crash the comparator — they should be placed at the end instead.
   */
  public void testSortByDeclarationOrder_nullLocationGoesToEnd() {
    TestConsoleProperties properties = createConsoleProperties();
    TestConsoleProperties.SORT_BY_DURATION.set(properties, false);
    TestConsoleProperties.SORT_ALPHABETICALLY.set(properties, false);
    TestConsoleProperties.SORT_BY_DECLARATION_ORDER.set(properties, true);
    TestConsoleProperties.SUITES_ALWAYS_ON_TOP.set(properties, false);

    TestFrameworkRunningModel model = createModelFor(properties);

    SMTestProxy root    = createSuiteProxy("root");
    SMTestProxy jvmTest = proxyAtOffset("jvm", root, 10);  // location is non-null
    SMTestProxy jsTest  = createTestProxy("js",  root);    // no locator → getLocation() returns null

    NodeDescriptor<?> parentDesc = new BaseTestProxyNodeDescriptor<>(getProject(), root, null);
    BaseTestProxyNodeDescriptor<SMTestProxy> jvmDesc = new BaseTestProxyNodeDescriptor<>(getProject(), jvmTest, parentDesc);
    BaseTestProxyNodeDescriptor<SMTestProxy> jsDesc  = new BaseTestProxyNodeDescriptor<>(getProject(), jsTest,  parentDesc);

    Comparator<NodeDescriptor<?>> comparator = model.createComparator();
    assertTrue("null-location node must go after valid-location node",  comparator.compare(jsDesc,  jvmDesc) > 0);
    assertTrue("valid-location node must go before null-location node", comparator.compare(jvmDesc, jsDesc)  < 0);
  }

  /**
   * Two nodes both with null locations must be considered equal (no crash, both visible).
   */
  public void testSortByDeclarationOrder_bothNullLocationAreEqual() {
    TestConsoleProperties properties = createConsoleProperties();
    TestConsoleProperties.SORT_BY_DURATION.set(properties, false);
    TestConsoleProperties.SORT_ALPHABETICALLY.set(properties, false);
    TestConsoleProperties.SORT_BY_DECLARATION_ORDER.set(properties, true);
    TestConsoleProperties.SUITES_ALWAYS_ON_TOP.set(properties, false);

    TestFrameworkRunningModel model = createModelFor(properties);

    SMTestProxy root = createSuiteProxy("root");
    SMTestProxy js   = createTestProxy("js",   root);
    SMTestProxy wasm = createTestProxy("wasm", root);
    // Neither overrides getLocation → returns null for both

    NodeDescriptor<?> parentDesc = new BaseTestProxyNodeDescriptor<>(getProject(), root, null);
    BaseTestProxyNodeDescriptor<SMTestProxy> jsDesc   = new BaseTestProxyNodeDescriptor<>(getProject(), js,   parentDesc);
    BaseTestProxyNodeDescriptor<SMTestProxy> wasmDesc = new BaseTestProxyNodeDescriptor<>(getProject(), wasm, parentDesc);

    Comparator<NodeDescriptor<?>> comparator = model.createComparator();
    assertEquals("two null-location nodes must be equal", 0, comparator.compare(jsDesc, wasmDesc));
  }

  /**
   * Nodes under different parents must not be reordered (comparator returns 0).
   */
  public void testSortByDeclarationOrder_differentParentsNotReordered() {
    TestConsoleProperties properties = createConsoleProperties();
    TestConsoleProperties.SORT_BY_DURATION.set(properties, false);
    TestConsoleProperties.SORT_ALPHABETICALLY.set(properties, false);
    TestConsoleProperties.SORT_BY_DECLARATION_ORDER.set(properties, true);
    TestConsoleProperties.SUITES_ALWAYS_ON_TOP.set(properties, false);

    TestFrameworkRunningModel model = createModelFor(properties);

    SMTestProxy root   = createSuiteProxy("root");
    SMTestProxy suite1 = createSuiteProxy("suite1", root);
    SMTestProxy suite2 = createSuiteProxy("suite2", root);
    SMTestProxy testA  = proxyAtOffset("testA", suite1, 100);
    SMTestProxy testB  = proxyAtOffset("testB", suite2, 5);

    NodeDescriptor<?> parentDesc1 = new BaseTestProxyNodeDescriptor<>(getProject(), suite1, null);
    NodeDescriptor<?> parentDesc2 = new BaseTestProxyNodeDescriptor<>(getProject(), suite2, null);
    BaseTestProxyNodeDescriptor<SMTestProxy> descA = new BaseTestProxyNodeDescriptor<>(getProject(), testA, parentDesc1);
    BaseTestProxyNodeDescriptor<SMTestProxy> descB = new BaseTestProxyNodeDescriptor<>(getProject(), testB, parentDesc2);

    Comparator<NodeDescriptor<?>> comparator = model.createComparator();
    assertEquals("nodes under different parents must not be reordered", 0, comparator.compare(descA, descB));
  }

  private TestFrameworkRunningModel createModelFor(TestConsoleProperties properties) {
    return new TestFrameworkRunningModel() {
      @Override public TestConsoleProperties getProperties() { return properties; }
      @Override public void setFilter(@NotNull Filter<?> filter) {}
      @Override public boolean isRunning() { return false; }
      @Override public TestTreeView getTreeView() { return null; }
      @Override public AbstractTestTreeBuilderBase<?> getTreeBuilder() { return null; }
      @Override public boolean hasTestSuites() { return false; }
      @Override public AbstractTestProxy getRoot() { return null; }
      @Override public void selectAndNotify(AbstractTestProxy proxy) {}
      @Override public void dispose() {}
    };
  }

  /**
   * Creates a test proxy that overrides {@code getLocation()} to return a {@link PsiLocation}
   * backed by a {@link FakePsiElement} at the given {@code textOffset}.
   * This bypasses the {@code SMTestLocator} / caching machinery in {@link SMTestProxy#getLocation}.
   */
  private SMTestProxy proxyAtOffset(String name, SMTestProxy parent, int textOffset) {
    Project project = getProject();
    PsiElement psi = new FakePsiElement() {
      @Override public int getTextOffset() { return textOffset; }
      @Override public PsiElement getParent() { return null; }
      @Override public String getName() { return name + "@" + textOffset; }
      @Override public boolean isValid() { return true; }
    };
    SMTestProxy proxy = new SMTestProxy(name, false, null) {
      @Override
      public @Nullable Location getLocation(@NotNull Project p, @NotNull GlobalSearchScope scope) {
        return new Location<PsiElement>() {
          @Override public @NotNull PsiElement getPsiElement() { return psi; }
          @Override public @NotNull Project getProject() { return project; }
          @Override public @Nullable com.intellij.openapi.module.Module getModule() { return null; }
          @Override public @NotNull <T extends PsiElement> java.util.Iterator<Location<T>> getAncestors(Class<T> c, boolean strict) {
            return java.util.Collections.emptyIterator();
          }
          @Override public @NotNull PsiLocation<PsiElement> toPsiLocation() {
            return new PsiLocation<>(project, null, psi);
          }
        };
      }
    };
    parent.addChild(proxy);
    return proxy;
  }

  private static class MockTestLocator implements SMTestLocator {

    public static final String TEST_LOCATION_TEXT = "<test location>";

    private final Location myLocation;
    private final List<GlobalSearchScope> myCalledSearchScopes = new ArrayList<>();

    MockTestLocator(@NotNull Location location) {
      myLocation = location;
    }

    @NotNull
    @Override
    public List<Location> getLocation(@NotNull String protocol,
                                      @NotNull String path,
                                      @NotNull Project project,
                                      @NotNull GlobalSearchScope scope) {
      if (!IdempotenceChecker.isCurrentThreadInsideRandomCheck()) {
        myCalledSearchScopes.add(scope);
      }
      if (myLocation.getPsiElement().getText().contains(TEST_LOCATION_TEXT)) {
        return Collections.singletonList(myLocation);
      }
      return Collections.emptyList();
    }
  }
}
