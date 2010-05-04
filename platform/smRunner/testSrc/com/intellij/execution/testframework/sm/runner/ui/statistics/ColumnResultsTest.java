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
import com.intellij.execution.testframework.sm.runner.ui.TestsPresentationUtil;
import com.intellij.execution.testframework.sm.UITestUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman Chernyatchik
 */
public class ColumnResultsTest extends BaseColumnRenderingTest {

  public void testPresentation_TestNotRun() {
    doRender(mySimpleTest);

    assertFragmentsSize(1);
    assertEquals(1, myFragmentsContainer.getFragments().size());
    assertEquals(SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Not run", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_TestInProgress() {
    mySimpleTest.setStarted();

    doRender(mySimpleTest);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Running...", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_TestFailure() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);

    doRender(mySimpleTest);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Assertion failed", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_TestPassed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();

    doRender(mySimpleTest);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Passed", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_TestError() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);

    doRender(mySimpleTest);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Error", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_TestTerminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();

    doRender(mySimpleTest);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.TERMINATED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Terminated", myFragmentsContainer.getTextAt(0));
  }

  public void testValueOf_Test() {
    assertEquals(ColumnResults.UNDEFINED, myColumn.valueOf(mySimpleTest));

    mySimpleTest.setStarted();
    assertEquals(ColumnResults.UNDEFINED, myColumn.valueOf(mySimpleTest));

    mySimpleTest.setFinished();
    assertEquals(ColumnResults.UNDEFINED, myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_Suite() {
    assertEquals(ColumnResults.UNDEFINED, myColumn.valueOf(mySuite));

    mySuite.setStarted();
    assertEquals(ColumnResults.UNDEFINED, myColumn.valueOf(mySuite));

    createTestProxy(mySuite);
    assertEquals(ColumnResults.UNDEFINED, myColumn.valueOf(mySuite));

    mySuite.setFinished();
    assertEquals(ColumnResults.UNDEFINED, myColumn.valueOf(mySuite));
  }

  public void testPresentation_SuiteNotRun() {
    doRender(mySuite);

    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("<NO TESTS>", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_SuiteEmpty() {
    doRender(mySuite);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("<NO TESTS>", myFragmentsContainer.getTextAt(0));

    myFragmentsContainer.clear();
    mySuite.setStarted();
    doRender(mySuite);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("<NO TESTS>", myFragmentsContainer.getTextAt(0));

    myFragmentsContainer.clear();
    mySuite.setFinished();
    doRender(mySuite);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("<NO TESTS>", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_SuiteTestProgress() {
    mySuite.setStarted();
    final SMTestProxy test1 = createTestProxy(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());

    test1.setStarted();
    assertEmpty(myFragmentsContainer.getFragments());
  }

  public void testPresentation_SuiteTestPassed() {
    mySuite.setStarted();
    final SMTestProxy test1 = createTestProxy(mySuite);

    doRender(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());

    test1.setStarted();
    test1.setFinished();

    doRender(mySuite);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("P:1", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_SuiteTestFailed() {
    mySuite.setStarted();
    final SMTestProxy test1 = createTestProxy(mySuite);

    doRender(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());

    test1.setStarted();
    test1.setTestFailed("", "", false);

    doRender(mySuite);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("F:1 ", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_SuiteTestError() {
    mySuite.setStarted();
    final SMTestProxy test1 = createTestProxy(mySuite);

    doRender(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());

    test1.setStarted();
    test1.setTestFailed("", "", true);

    doRender(mySuite);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("E:1 ", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_SuiteTerminated() {
    mySuite.setStarted();
    final SMTestProxy test1 = createTestProxy(mySuite);
    doRender(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());

    test1.setStarted();
    mySuite.setTerminated();

    doRender(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());
  }

  public void testPresentation_SuiteTerminated_WithResults() {
    mySuite.setStarted();
    final SMTestProxy passedTest = createTestProxy(mySuite);
    final SMTestProxy failedTest = createTestProxy(mySuite);
    final SMTestProxy errorTest = createTestProxy(mySuite);
    final SMTestProxy inProgressTest = createTestProxy(mySuite);

    doRender(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());

    passedTest.setStarted();
    passedTest.setFinished();

    failedTest.setStarted();
    failedTest.setTestFailed("", "", false);
    failedTest.setFinished();

    errorTest.setStarted();
    errorTest.setTestFailed("", "", true);
    errorTest.setFinished();

    inProgressTest.setStarted();

    mySuite.setTerminated();

    doRender(mySuite);
    assertFragmentsSize(3);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("F:1 ", myFragmentsContainer.getTextAt(0));
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(1));
    assertEquals("E:1 ", myFragmentsContainer.getTextAt(1));
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(2));
    assertEquals("P:1", myFragmentsContainer.getTextAt(2));
  }

  public void testPresentation_SuiteStarted_DifferentResults() {
    mySuite.setStarted();
    final SMTestProxy passedTest1 = createTestProxy(mySuite);
    final SMTestProxy passedTest2 = createTestProxy(mySuite);
    final SMTestProxy passedTest3 = createTestProxy(mySuite);
    final SMTestProxy failedTest = createTestProxy(mySuite);
    final SMTestProxy errorTest1 = createTestProxy(mySuite);
    final SMTestProxy errorTest2 = createTestProxy(mySuite);
    final SMTestProxy inProgressTest = createTestProxy(mySuite);

    doRender(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());

    passedTest1.setStarted();
    passedTest1.setFinished();
    passedTest2.setStarted();
    passedTest2.setFinished();
    passedTest3.setStarted();
    passedTest3.setFinished();

    failedTest.setStarted();
    failedTest.setTestFailed("", "", false);
    failedTest.setFinished();

    errorTest1.setStarted();
    errorTest1.setTestFailed("", "", true);
    errorTest1.setFinished();
    errorTest2.setStarted();
    errorTest2.setTestFailed("", "", true);
    errorTest2.setFinished();

    inProgressTest.setStarted();

    doRender(mySuite);
    assertFragmentsSize(3);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("F:1 ", myFragmentsContainer.getTextAt(0));
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(1));
    assertEquals("E:2 ", myFragmentsContainer.getTextAt(1));
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(2));
    assertEquals("P:3", myFragmentsContainer.getTextAt(2));
  }

  public void testPresentation_SuitePassed() {
    mySuite.setStarted();
    final SMTestProxy passedTest = createTestProxy(mySuite);
    final SMTestProxy failedTest = createTestProxy(mySuite);

    passedTest.setStarted();
    passedTest.setFinished();

    mySuite.setFinished();

    doRender(mySuite);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("P:1", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_SuiteFailed() {
    mySuite.setStarted();
    final SMTestProxy passedTest = createTestProxy(mySuite);
    final SMTestProxy failedTest = createTestProxy(mySuite);

    passedTest.setStarted();
    passedTest.setFinished();

    failedTest.setStarted();
    failedTest.setTestFailed("", "", false);
    failedTest.setFinished();

    mySuite.setFinished();

    doRender(mySuite);
    assertFragmentsSize(2);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("F:1 ", myFragmentsContainer.getTextAt(0));
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(1));
    assertEquals("P:1", myFragmentsContainer.getTextAt(1));
  }

  public void testPresentation_SuiteError() {
    mySuite.setStarted();
    final SMTestProxy passedTest = createTestProxy(mySuite);
    final SMTestProxy failedTest = createTestProxy(mySuite);

    passedTest.setStarted();
    passedTest.setFinished();

    failedTest.setStarted();
    failedTest.setTestFailed("", "", true);
    failedTest.setFinished();

    mySuite.setFinished();

    doRender(mySuite);
    assertFragmentsSize(2);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("E:1 ", myFragmentsContainer.getTextAt(0));
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(1));
    assertEquals("P:1", myFragmentsContainer.getTextAt(1));
  }

  protected ColoredRenderer createRenderer(final SMTestProxy testProxy,
                                           final UITestUtil.FragmentsContainer fragmentsContainer) {
    return new MyRenderer(testProxy, fragmentsContainer);
  }

  protected ColumnInfo<SMTestProxy, String> createColumn() {
    return new ColumnResults();
  }


  private class MyRenderer extends ColumnResults.ResultsCellRenderer {
    private final UITestUtil.FragmentsContainer myFragmentsContainer;

    private MyRenderer(final SMTestProxy proxy,
                       final UITestUtil.FragmentsContainer fragmentsContainer) {
      super(proxy);
      myFragmentsContainer = fragmentsContainer;
    }

    @Override
    public void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes,
                       final boolean isMainText) {
      myFragmentsContainer.append(fragment, attributes);
    }
  }
}
