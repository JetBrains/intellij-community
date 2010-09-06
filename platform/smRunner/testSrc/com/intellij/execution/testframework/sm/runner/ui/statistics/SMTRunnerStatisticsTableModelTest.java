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
package com.intellij.execution.testframework.sm.runner.ui.statistics;

import com.intellij.execution.testframework.sm.runner.BaseSMTRunnerTestCase;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;

import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class SMTRunnerStatisticsTableModelTest extends BaseSMTRunnerTestCase {
  private StatisticsTableModel myStatisticsTableModel;
  private SMTestProxy myRootSuite;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myStatisticsTableModel = new StatisticsTableModel();

    myRootSuite = createSuiteProxy("root");
  }

  public void testOnSelected_Null() {
    myStatisticsTableModel.updateModelOnProxySelected(null);

    assertEmpty(getItems());
  }

  public void testOnSelected_Test() {
    final SMTestProxy test1 = createTestProxy("test1", myRootSuite);
    final SMTestProxy test2 = createTestProxy("test2", myRootSuite);
    myStatisticsTableModel.updateModelOnProxySelected(test1);

    assertSameElements(getItems(), myRootSuite, test1, test2);
  }

  public void testOnSelected_Suite() {
    final SMTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    final SMTestProxy test1 = createTestProxy("test1", suite1);
    final SMTestProxy test2 = createTestProxy("test2", suite1);

    final SMTestProxy suite2 = createSuiteProxy("suite2", myRootSuite);

    myStatisticsTableModel.updateModelOnProxySelected(suite1);
    assertSameElements(getItems(), suite1, test1, test2);

    myStatisticsTableModel.updateModelOnProxySelected(suite2);
    assertSameElements(getItems(), suite2);

    myStatisticsTableModel.updateModelOnProxySelected(myRootSuite);
    assertSameElements(getItems(), myRootSuite, suite1, suite2);
  }
/*
  public void testSort_ColumnTest() {
    final SMTestProxy firstSuite = createSuiteProxy("K_suite1", myRootSuite);
    final SMTestProxy lastSuite = createSuiteProxy("L_suite1", myRootSuite);
    final SMTestProxy firstTest = createTestProxy("A_test", myRootSuite);
    final SMTestProxy lastTest = createTestProxy("Z_test", myRootSuite);

    myStatisticsTableModel.updateModelOnProxySelected(myRootSuite);
    assertOrderedEquals(getItems(), myRootSuite, firstTest, firstSuite, lastSuite, lastTest);

    //sort with another sort type
    myStatisticsTableModel.sortByColumn(2, SortableColumnModel.SORT_ASCENDING);
    //resort
    myStatisticsTableModel.sortByColumn(0, SortableColumnModel.SORT_ASCENDING);
    assertOrderedEquals(getItems(), myRootSuite, firstTest, firstSuite, lastSuite, lastTest);
    //reverse
    myStatisticsTableModel.sortByColumn(0, SortableColumnModel.SORT_DESCENDING);
    assertOrderedEquals(getItems(), myRootSuite, lastTest, lastSuite, firstSuite, firstTest);
    //direct
    myStatisticsTableModel.sortByColumn(0, SortableColumnModel.SORT_ASCENDING);
    assertOrderedEquals(getItems(), myRootSuite, firstTest, firstSuite, lastSuite, lastTest);
  }

  public void testSort_DurationTest() {
    final SMTestProxy firstSuite = createSuiteProxy("A_suite1", myRootSuite);
    final SMTestProxy firstSuite_Test = createTestProxy("test", firstSuite);
    firstSuite_Test.setDuration(10);

    final SMTestProxy lastSuite = createSuiteProxy("L_suite1", myRootSuite);
    final SMTestProxy lastSuite_Test = createTestProxy("test", lastSuite);
    lastSuite_Test.setDuration(90);

    final SMTestProxy firstTest = createTestProxy("K_test", myRootSuite);
    firstTest.setDuration(1);
    final SMTestProxy lastTest = createTestProxy("Z_test", myRootSuite);
    lastTest.setDuration(100);

    myStatisticsTableModel.updateModelOnProxySelected(myRootSuite);
    //assertOrderedEquals(getItems(), myRootSuite, firstTest, firstSuite, lastSuite, lastTest);

    //sort with another sort type
    myStatisticsTableModel.sortByColumn(0, SortableColumnModel.SORT_ASCENDING);
    //resort
    myStatisticsTableModel.sortByColumn(1, SortableColumnModel.SORT_ASCENDING);
    assertOrderedEquals(getItems(), myRootSuite, firstTest, firstSuite, lastSuite, lastTest);
    //reverse
    myStatisticsTableModel.sortByColumn(1, SortableColumnModel.SORT_DESCENDING);
    assertOrderedEquals(getItems(), myRootSuite, lastTest, lastSuite, firstSuite, firstTest);
    //direct
    myStatisticsTableModel.sortByColumn(1, SortableColumnModel.SORT_ASCENDING);
    assertOrderedEquals(getItems(), myRootSuite, firstTest, firstSuite, lastSuite, lastTest);
  }
*/
  public void testGotoParentSuite_ResultsRoot() {
    // create test sturcure
    final SMTestProxy rootSuite = createSuiteProxy("rootSuite");

    final SMTestProxy suite3 = createSuiteProxy("A_suite3", rootSuite);
    final SMTestProxy failedTest31 = createTestProxy("failedTest31", suite3);
    final SMTestProxy errorTest31 = createTestProxy("errorTest31", suite3);
    doFailTest(failedTest31);
    doErrorTest(errorTest31);

    final SMTestProxy suite1 = createSuiteProxy("B_suite1", rootSuite);
    final SMTestProxy passedTest11 = createTestProxy("passedTest11", suite1);
    final SMTestProxy passedTest12 = createTestProxy("passedTest12", suite1);
    doPassTest(passedTest11);
    doPassTest(passedTest12);

    final SMTestProxy suite2 = createSuiteProxy("C_suite1", rootSuite);
    final SMTestProxy passedTest21 = createTestProxy("passedTest21", suite2);
    final SMTestProxy errorTest21 = createTestProxy("errorTest21", suite2);
    doPassTest(passedTest21);
    doErrorTest(errorTest21);

    final SMTestProxy suite4 = createSuiteProxy("D_suite4", rootSuite);
    final SMTestProxy failedTest41 = createTestProxy("failedTest41", suite4);
    final SMTestProxy errorTest41 = createTestProxy("errorTest41", suite4);
    final SMTestProxy errorTest42 = createTestProxy("errorTest42", suite4);
    doFailTest(failedTest41);
    doErrorTest(errorTest41);
    doErrorTest(errorTest42);

    final SMTestProxy passedTest1 = createTestProxy("passedTest1", rootSuite);
    final SMTestProxy failedTest1 = createTestProxy("failedTest1", rootSuite);
    final SMTestProxy errorTest1 = createTestProxy("errotTest1", rootSuite);
    doPassTest(passedTest1);
    doFailTest(failedTest1);
    doErrorTest(errorTest1);

    myStatisticsTableModel.updateModelOnProxySelected(rootSuite);

    //sort with another sort type
    //myStatisticsTableModel.sortByColumn(0, SortableColumnModel.SORT_ASCENDING);
    //resort
    //myStatisticsTableModel.sortByColumn(2, SortableColumnModel.SORT_DESCENDING);
    assertOrderedEquals(getItems(),
                        rootSuite, suite4, suite3, suite2, suite1, errorTest1, failedTest1, passedTest1);
    //reverse
    //myStatisticsTableModel.sortByColumn(2, SortableColumnModel.SORT_ASCENDING);
    assertOrderedEquals(getItems(),
                        rootSuite, passedTest1, failedTest1, errorTest1, suite1, suite2, suite3, suite4);
    //direct
    //myStatisticsTableModel.sortByColumn(2, SortableColumnModel.SORT_DESCENDING);
    assertOrderedEquals(getItems(),
                        rootSuite, suite4, suite3, suite2, suite1, errorTest1, failedTest1, passedTest1);
  }

  private List<SMTestProxy> getItems() {
    return myStatisticsTableModel.getItems();
  }
}
