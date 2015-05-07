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

import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.UITestUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman Chernyatchik
 */
public class ColumnDurationTest extends BaseColumnRenderingTest {
  
  public void testValueOf_NotRun() {
    assertEquals("<NOT RUN>", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_InProgress() {
    mySimpleTest.setStarted();
    assertEquals("<RUNNING>", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_TestFailure() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertEquals("<UNKNOWN>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(10000);
    assertEquals("10s", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_TestPassed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    assertEquals("<UNKNOWN>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(10000);
    assertEquals("10s", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_TestError() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);
    assertEquals("<UNKNOWN>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(10000);
    assertEquals("10s", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_TestTerminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    assertEquals("<TERMINATED>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(10000);
    assertEquals("TERMINATED: 10s", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_TestIgnored() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestIgnored("todo", null);
    mySimpleTest.setFinished();
    assertEquals("<UNKNOWN>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(10000);
    assertEquals("10s", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_Duration_Zero() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertEquals("<UNKNOWN>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(0);
    assertEquals("0ms", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_Duration_1() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertEquals("<UNKNOWN>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(1);
    assertEquals("1ms", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_Duration_99() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertEquals("<UNKNOWN>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(99);
    assertEquals("99ms", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_Duration_100() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertEquals("<UNKNOWN>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(100);
    assertEquals("100ms", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_Duration_999() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertEquals("<UNKNOWN>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(999);
    assertEquals("999ms", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_Duration_1000() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertEquals("<UNKNOWN>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(1000);
    assertEquals("1s", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_Duration_1001() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertEquals("<UNKNOWN>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(1001);
    assertEquals("1s 1ms", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_SuiteEmpty() {
    final SMTestProxy suite = createSuiteProxy();
    suite.setStarted();
    suite.setFinished();
    assertEquals("<NO TESTS>", myColumn.valueOf(suite));

    suite.setFinished();
    assertEquals("<NO TESTS>", myColumn.valueOf(suite));
  }

  public void testValueOf_SuiteNotRun() {
    final SMTestProxy suite = createSuiteProxy();
    assertEquals("<NOT RUN>", myColumn.valueOf(suite));

    final SMTestProxy test = createTestProxy("test", suite);
    assertEquals("<NOT RUN>", myColumn.valueOf(suite));

    test.setDuration(5);
    assertEquals("<NOT RUN>", myColumn.valueOf(suite));
  }

  public void testValueOf_SuiteFailed() {
    final SMTestProxy suite = createSuiteProxy();
    final SMTestProxy test = createTestProxy("test", suite);

    suite.setStarted();
    test.setTestFailed("", "", false);
    suite.setFinished();

    assertEquals("<UNKNOWN>", myColumn.valueOf(suite));

    test.setDuration(10000);
    assertEquals("10s", myColumn.valueOf(suite));
  }

  public void testValueOf_SuiteError() {
    final SMTestProxy suite = createSuiteProxy();
    final SMTestProxy test = createTestProxy("test", suite);

    suite.setStarted();
    test.setTestFailed("", "", true);
    suite.setFinished();

    assertEquals("<UNKNOWN>", myColumn.valueOf(suite));

    test.setDuration(10000);
    assertEquals("10s", myColumn.valueOf(suite));
  }

  public void testValueOf_SuitePassed() {
    final SMTestProxy suite = createSuiteProxy();
    final SMTestProxy test = createTestProxy("test", suite);

    suite.setStarted();
    test.setFinished();
    suite.setFinished();

    assertEquals("<UNKNOWN>", myColumn.valueOf(suite));

    test.setDuration(10000);
    assertEquals("10s", myColumn.valueOf(suite));
  }

  public void testValueOf_SuiteTerminated() {
    final SMTestProxy suite = createSuiteProxy();
    final SMTestProxy test = createTestProxy("test", suite);

    suite.setStarted();
    suite.setTerminated();

    assertEquals("<TERMINATED>", myColumn.valueOf(suite));

    test.setDuration(10000);
    assertEquals("TERMINATED: 10s", myColumn.valueOf(suite));
  }

  public void testValueOf_SuiteRunning() {
    final SMTestProxy suite = createSuiteProxy();
    final SMTestProxy test = createTestProxy("test", suite);

    suite.setStarted();
    test.setStarted();

    assertEquals("<RUNNING>", myColumn.valueOf(suite));

    test.setDuration(10000);
    assertEquals("RUNNING: 10s", myColumn.valueOf(suite));
  }

  public void testTotal_Test() {
    mySuite.addChild(mySimpleTest);

    doRender(mySimpleTest, 0);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals(myColumn.valueOf(mySimpleTest), myFragmentsContainer.getTextAt(0));

    myFragmentsContainer.clear();
    doRender(mySimpleTest, 1);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals(myColumn.valueOf(mySimpleTest), myFragmentsContainer.getTextAt(0));
  }

  public void testTotal_RegularSuite() {
    doRender(mySuite, 1);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals(myColumn.valueOf(mySuite), myFragmentsContainer.getTextAt(0));
  }

  public void testTotal_TotalSuite() {
    doRender(mySuite, 0);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals(myColumn.valueOf(mySuite), myFragmentsContainer.getTextAt(0));
  }
  
  @Override
  protected ColoredRenderer createRenderer(final SMTestProxy testProxy,
                                           final UITestUtil.FragmentsContainer fragmentsContainer) {
    return new MyRenderer(testProxy, fragmentsContainer);
  }

  @Override
  protected ColumnInfo<SMTestProxy, String> createColumn() {
    return new ColumnDuration();
  }

  private class MyRenderer extends ColumnDuration.DurationCellRenderer {
    private final UITestUtil.FragmentsContainer myFragmentsContainer;

    public MyRenderer(final SMTestProxy proxy,
                       final UITestUtil.FragmentsContainer fragmentsContainer) {
      super();
      myFragmentsContainer = fragmentsContainer;
    }

    @Override
    public void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes,
                       final boolean isMainText) {
      myFragmentsContainer.append(fragment, attributes);
    }
  }
}
