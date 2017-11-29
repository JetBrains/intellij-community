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
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.psi.search.GlobalSearchScope;
import org.easymock.EasyMock;

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
    final MockPrinter printer = new MockPrinter(true);
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

    final MockPrinter printer = new MockPrinter(true) {
      @Override
      public void printHyperlink(String text, HyperlinkInfo info) {
        print(text, ConsoleViewContentType.SYSTEM_OUTPUT);
      }
    };
    mySimpleTest.printOn(printer);
    assertEquals("", printer.getStdOut());
    assertEquals("\n" +
                 "a\n" +
                 "Expected :expected1\n" +
                 "Actual   :actual1\n" +
                 " <Click to see difference>\n" +
                 "\n" +
                 "stacktrace\n" +
                 "\n" +
                 "b\n" +
                 "Expected :expected2\n" +
                 "Actual   :actual2\n" +
                 " <Click to see difference>\n" +
                 "\n" +
                 "stacktrace\n" +
                 "\n" +
                 "c\n" +
                 "stacktrace\n", printer.getAllOut());
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
    assertNull(mySuite.getLocation(getProject(), GlobalSearchScope.allScope(getProject())));

    mySuite.addChild(mySimpleTest);

    assertNull(mySuite.getLocation(getProject(), GlobalSearchScope.allScope(getProject())));
    assertNull(mySimpleTest.getLocation(getProject(), GlobalSearchScope.allScope(getProject())));
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
    assertEquals(7L, duration.longValue());

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

  protected static void assertWeightsOrder(final Magnitude previous, final Magnitude next) {
    assertTrue(previous.getSortWeight() < next.getSortWeight());
  }
}
