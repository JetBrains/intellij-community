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
public class ColumnTestTest extends BaseColumnRenderingTest {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myColumn = new ColumnTest();
  }

  public void testValueOf_Test() {
    assertEquals("test", myColumn.valueOf(createTestProxy("test")));

    final SMTestProxy test = createTestProxy("test of suite", mySuite);
    assertEquals("test of suite", myColumn.valueOf(test));
  }

  public void testValueOf_TestNameCollapsing() {
    assertEquals("test", myColumn.valueOf(createTestProxy("test")));

    final SMTestProxy suiteProxy = createSuiteProxy("MySuite");
    assertEquals("test of suite", myColumn.valueOf(createTestProxy("MySuite.test of suite", suiteProxy)));
    assertEquals("test of suite", myColumn.valueOf(createTestProxy("MySuite test of suite", suiteProxy)));
    assertEquals("Not MySuite test of suite", myColumn.valueOf(createTestProxy("Not MySuite test of suite", suiteProxy)));
  }

  public void testValueOf_Suite() {
    final SMTestProxy suite = createSuiteProxy("my suite", mySuite);
    createTestProxy("test", suite);
    assertEquals("my suite", myColumn.valueOf(suite));
  }

  public void testValueOf_SuiteNameCollapsing() {
    final SMTestProxy suiteProxy = createSuiteProxy("MySuite");
    assertEquals("child suite", myColumn.valueOf(createSuiteProxy("MySuite.child suite", suiteProxy)));
    assertEquals("child suite", myColumn.valueOf(createSuiteProxy("MySuite child suite", suiteProxy)));
    assertEquals("Not MySuite child suite", myColumn.valueOf(createSuiteProxy("Not MySuite child suite", suiteProxy)));
  }

  public void testTotal_Test() {
    mySuite.addChild(mySimpleTest);

    doRender(mySimpleTest, 0);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals(mySimpleTest.getPresentableName(), myFragmentsContainer.getTextAt(0));

    myFragmentsContainer.clear();
    doRender(mySimpleTest, 1);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals(mySimpleTest.getPresentableName(), myFragmentsContainer.getTextAt(0));
  }

  public void testTotal_RegularSuite() {
    doRender(mySuite, 1);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals(mySuite.getPresentableName(), myFragmentsContainer.getTextAt(0));
  }

  public void testTotal_TotalNotRootSuite() {
    // pre condition
    assertEquals("suite", mySuite.getName());

    final SMTestProxy newRootSuite = createSuiteProxy("root_suite");
    mySuite.setParent(newRootSuite);
    doRender(mySuite, 0);
    assertFragmentsSize(1);
  }

  public void testTotal_TotalRootSuite() {
    doRender(mySuite, 0);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
  }

  @Override
  protected ColoredRenderer createRenderer(final SMTestProxy proxy,
                                           final UITestUtil.FragmentsContainer fragmentsContainer) {
    return new MyRenderer(proxy, fragmentsContainer);
  }

  @Override
  protected ColumnInfo<SMTestProxy, String> createColumn() {
    return new ColumnTest();
  }

  private class MyRenderer extends ColumnTest.TestsCellRenderer {
    private final UITestUtil.FragmentsContainer myFragmentsContainer;

    public MyRenderer(final SMTestProxy proxy,
                      final UITestUtil.FragmentsContainer fragmentsContainer) {
      super();
      myFragmentsContainer = fragmentsContainer;
    }

    @Override
    public void append(@NotNull final String fragment,
                       @NotNull final SimpleTextAttributes attributes,
                       final boolean isMainText) {
      myFragmentsContainer.append(fragment, attributes);
    }
  }
}
