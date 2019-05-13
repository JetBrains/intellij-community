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

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.BaseSMTRunnerTestCase;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Roman Chernyatchik
 */
public class SMTRunnerUIActionsHandlerTest extends BaseSMTRunnerTestCase {
  private MockTestResultsViewer myResultsViewer;
  private TestConsoleProperties myProperties;
  private SMTRunnerUIActionsHandler myUIActionsHandler;
  private AbstractTestProxy mySelectedTestProxy;
  private SMTestRunnerResultsForm myResultsForm;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myProperties = createConsoleProperties();
    myResultsViewer = new MockTestResultsViewer(myProperties, mySuite) {
      @Override
      public void selectAndNotify(@Nullable final AbstractTestProxy proxy) {
        super.selectAndNotify(proxy);
        mySelectedTestProxy = proxy;
      }
    };

    myUIActionsHandler = new SMTRunnerUIActionsHandler(myProperties);

    TestConsoleProperties.HIDE_PASSED_TESTS.set(myProperties, false);
    TestConsoleProperties.OPEN_FAILURE_LINE.set(myProperties, false);
    TestConsoleProperties.SCROLL_TO_SOURCE.set(myProperties, false);
    TestConsoleProperties.SELECT_FIRST_DEFECT.set(myProperties, false);
    TestConsoleProperties.TRACK_RUNNING_TEST.set(myProperties, false);

    myResultsForm = new SMTestRunnerResultsForm(new JLabel(),
                                                myProperties) {
      @Override
      public void selectAndNotify(AbstractTestProxy testProxy) {
        super.selectAndNotify(testProxy);
        mySelectedTestProxy = testProxy;
      }
    };
    Disposer.register(myResultsForm, myProperties);
    myResultsForm.initUI();

  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(myResultsViewer);
    Disposer.dispose(myResultsForm);
    super.tearDown();
  }

  public void testSelectFirstDeffect_Failed() {
    TestConsoleProperties.SELECT_FIRST_DEFECT.set(myProperties, true);
    mySuite.setStarted();

    final SMTestProxy testsSuite = createSuiteProxy("my suite", mySuite);
    testsSuite.setStarted();

    // passed test
    final SMTestProxy testPassed1 = createTestProxy("testPassed1", testsSuite);
    testPassed1.setStarted();
    
    //failed test
    final SMTestProxy testFailed1 = createTestProxy("testFailed1", testsSuite);
    testFailed1.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testFailed1);
    assertNull(mySelectedTestProxy);

    testFailed1.setTestFailed("", "", false);
    //myUIActionsHandler.onTestFinished(testFailed1);
    assertNull(mySelectedTestProxy);

   // passed test numer 2
    mySelectedTestProxy = null;
    final SMTestProxy testPassed2 = createTestProxy("testPassed2", testsSuite);
    testPassed2.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testPassed2);
    assertNull(mySelectedTestProxy);

    testPassed2.setFinished();
    //myUIActionsHandler.onTestFinished(testPassed2);
    assertNull(mySelectedTestProxy);


    //failed test 2
    final SMTestProxy testFailed2 = createTestProxy("testFailed1", testsSuite);
    testFailed2.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testFailed2);
    assertNull(mySelectedTestProxy);

    testFailed2.setTestFailed("", "", false);
    //myUIActionsHandler.onTestFinished(testFailed2);
    assertNull(mySelectedTestProxy);

    // finish suite
    testsSuite.setFinished();
    assertNull(mySelectedTestProxy);

    //testing finished
    mySuite.setFinished();
    assertNull(mySelectedTestProxy);

    myUIActionsHandler.onTestingFinished(myResultsViewer);
    assertEquals(testFailed1, mySelectedTestProxy);
  }

  public void testSelectFirstDeffect_Error() {
    TestConsoleProperties.SELECT_FIRST_DEFECT.set(myProperties, true);
    mySuite.setStarted();

    final SMTestProxy testsSuite = createSuiteProxy("my suite", mySuite);
    testsSuite.setStarted();

    // passed test
    final SMTestProxy testPassed1 = createTestProxy("testPassed1", testsSuite);
    testPassed1.setStarted();

    //failed test
    final SMTestProxy testError = createTestProxy("testError", testsSuite);
    testError.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testError);
    assertNull(mySelectedTestProxy);

    testError.setTestFailed("", "", true);
    //myUIActionsHandler.onTestFinished(testFailed1);
    assertNull(mySelectedTestProxy);

   // passed test numer 2
    mySelectedTestProxy = null;
    final SMTestProxy testPassed2 = createTestProxy("testPassed2", testsSuite);
    testPassed2.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testPassed2);
    assertNull(mySelectedTestProxy);

    testPassed2.setFinished();
    //myUIActionsHandler.onTestFinished(testPassed2);
    assertNull(mySelectedTestProxy);


    //failed test
    final SMTestProxy testFailed2 = createTestProxy("testFailed1", testsSuite);
    testFailed2.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testFailed2);
    assertNull(mySelectedTestProxy);

    testFailed2.setTestFailed("", "", false);
    //myUIActionsHandler.onTestFinished(testFailed2);
    assertNull(mySelectedTestProxy);

    // finish suite
    testsSuite.setFinished();
    assertNull(mySelectedTestProxy);

    //testing finished
    mySuite.setFinished();
    assertNull(mySelectedTestProxy);

    myUIActionsHandler.onTestingFinished(myResultsViewer);
    assertEquals(testError, mySelectedTestProxy);
  }


  public void testSelectFirstDefect_Priority_Error() {
    // Priority: error -> failure
    TestConsoleProperties.SELECT_FIRST_DEFECT.set(myProperties, true);
    mySuite.setStarted();

    final SMTestProxy testsSuite = createSuiteProxy("my suite", mySuite);
    testsSuite.setStarted();

    // pending test
    final SMTestProxy testPending = createTestProxy("testPending", testsSuite);
    testPending.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testPending);
    testPending.setTestIgnored("", "");

    //failed test
    final SMTestProxy testFailed = createTestProxy("testFailed", testsSuite);
    testFailed.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testFailed);
    testFailed.setTestFailed("", "", false);

    //error test
    final SMTestProxy testError = createTestProxy("testError", testsSuite);
    testError.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testError);
    testError.setTestFailed("", "", true);

    // Second error test just to check that first failed will be selected
    final SMTestProxy testError2 = createTestProxy("testError2", testsSuite);
    testError2.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testError2);
    testError2.setTestFailed("", "", true);

    // finish suite
    testsSuite.setFinished();
    assertNull(mySelectedTestProxy);

    //testing finished
    mySuite.setFinished();
    assertNull(mySelectedTestProxy);

    myUIActionsHandler.onTestingFinished(myResultsViewer);
    assertEquals(testError, mySelectedTestProxy);
  }

  public void testSelectFirstDefect_Priority_Failure() {
    // Priority: error -> failure
    TestConsoleProperties.SELECT_FIRST_DEFECT.set(myProperties, true);
    mySuite.setStarted();

    final SMTestProxy testsSuite = createSuiteProxy("my suite", mySuite);
    testsSuite.setStarted();

    // pending test
    final SMTestProxy testPending = createTestProxy("testPending", testsSuite);
    testPending.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testPending);
    testPending.setTestIgnored("", "");

    //failed test
    final SMTestProxy testFailed = createTestProxy("testFailed", testsSuite);
    testFailed.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testFailed);
    testFailed.setTestFailed("", "", false);

    // Second failed test just to check that first failed will be selected
    final SMTestProxy testFailed2 = createTestProxy("testFailed2", testsSuite);
    testFailed2.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testFailed2);
    testFailed2.setTestFailed("", "", false);

    // finish suite
    testsSuite.setFinished();
    assertNull(mySelectedTestProxy);

    //testing finished
    mySuite.setFinished();
    assertNull(mySelectedTestProxy);

    myUIActionsHandler.onTestingFinished(myResultsViewer);
    assertEquals(testFailed, mySelectedTestProxy);
  }

  public void testSelectFirstDefect_Priority_Pending() {
    // Priority: error -> failure
    TestConsoleProperties.SELECT_FIRST_DEFECT.set(myProperties, true);
    mySuite.setStarted();

    final SMTestProxy testsSuite = createSuiteProxy("my suite", mySuite);
    testsSuite.setStarted();

    // pending test
    final SMTestProxy testPending = createTestProxy("testPending", testsSuite);
    testPending.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testPending);
    testPending.setTestIgnored("", "");

    // finish suite
    testsSuite.setFinished();
    assertNull(mySelectedTestProxy);

    //testing finished
    mySuite.setFinished();
    assertNull(mySelectedTestProxy);

    myUIActionsHandler.onTestingFinished(myResultsViewer);
    // pending tests shouldn't be considered as errors/failures
    assertNull(mySelectedTestProxy);
  }

  public void testTrackRunningTest() {
    TestConsoleProperties.TRACK_RUNNING_TEST.set(myProperties, true);
    mySuite.setStarted();

    final SMTestProxy testsSuite = createSuiteProxy("my suite", mySuite);
    testsSuite.setStarted();
    assertNull(mySelectedTestProxy);

    // passed test
    final SMTestProxy testPassed1 = createTestProxy("testPassed1", testsSuite);
    testPassed1.setStarted();
    myResultsForm.onTestStarted(testPassed1);
    assertEquals(testPassed1, mySelectedTestProxy);

    testPassed1.setFinished();
    //myUIActionsHandler.onTestFinished(testPassed1);
    assertEquals(testPassed1, mySelectedTestProxy);

    //failed test
    final SMTestProxy testFailed1 = createTestProxy("testFailed1", testsSuite);
    testFailed1.setStarted();
    myResultsForm.onTestStarted(testFailed1);
    assertEquals(testFailed1, mySelectedTestProxy);

    testFailed1.setTestFailed("", "", false);
    //myUIActionsHandler.onTestFinished(testFailed1);
    assertEquals(testFailed1, mySelectedTestProxy);

    //error test
    final SMTestProxy testError = createTestProxy("testError", testsSuite);
    testError.setStarted();
    myResultsForm.onTestStarted(testError);
    assertEquals(testError, mySelectedTestProxy);

    testError.setTestFailed("", "", true);
    //myUIActionsHandler.onTestFinished(testError);
    assertEquals(testError, mySelectedTestProxy);

    //terminated test
    final SMTestProxy testTerminated = createTestProxy("testTerimated", testsSuite);
    testTerminated.setStarted();
    myResultsForm.onTestStarted(testTerminated);
    assertEquals(testTerminated, mySelectedTestProxy);

    testTerminated.setTerminated();
    //myUIActionsHandler.onTestFinished(testError);
    assertEquals(testTerminated, mySelectedTestProxy);

   // passed test numer 2
    mySelectedTestProxy = null;
    final SMTestProxy testPassed2 = createTestProxy("testPassed2", testsSuite);
    testPassed2.setStarted();
    myResultsForm.onTestStarted(testPassed2);
    assertEquals(testPassed2, mySelectedTestProxy);

    testPassed2.setFinished();
    //myUIActionsHandler.onTestFinished(testPassed2);
    assertEquals(testPassed2, mySelectedTestProxy);


    //failed test 2
    final SMTestProxy testFailed2 = createTestProxy("testFailed2", testsSuite);
    testFailed2.setStarted();
    myResultsForm.onTestStarted(testFailed2);
    assertEquals(testFailed2, mySelectedTestProxy);
    final SMTestProxy lastSelectedTest = testFailed2;

    testFailed2.setTestFailed("", "", false);
    //myUIActionsHandler.onTestFinished(testFailed2);
    assertEquals(lastSelectedTest, mySelectedTestProxy);

    // finish suite
    testsSuite.setFinished();
    assertEquals(lastSelectedTest, mySelectedTestProxy);

    // root suite finished
    mySuite.setFinished();
    assertEquals(lastSelectedTest, mySelectedTestProxy);

    //testing finished
    //myResultsForm.onTestingFinished(myResultsViewer.getTestsRootNode());
    myUIActionsHandler.onTestingFinished(myResultsViewer);
    assertEquals(lastSelectedTest, mySelectedTestProxy);
  }
}
