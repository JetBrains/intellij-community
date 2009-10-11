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
import com.intellij.execution.testframework.sm.UITestUtil;
import com.intellij.util.ui.ColumnInfo;

/**
 * @author Roman Chernyatchik
 */
public abstract class BaseColumnRenderingTest extends BaseSMTRunnerTestCase {
  protected ColumnInfo<SMTestProxy, String> myColumn;

  protected ColoredRenderer mySimpleTestRenderer;
  protected ColoredRenderer mySuiteRenderer;
  protected UITestUtil.FragmentsContainer myFragmentsContainer;

  protected abstract ColoredRenderer createRenderer(final SMTestProxy testProxy,
                                                             final UITestUtil.FragmentsContainer fragmentsContainer);
  protected abstract ColumnInfo<SMTestProxy, String> createColumn();

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myColumn = createColumn();    
    myFragmentsContainer = new UITestUtil.FragmentsContainer();

    mySimpleTestRenderer = createRenderer(mySimpleTest, myFragmentsContainer);
    mySuiteRenderer = createRenderer(mySuite, myFragmentsContainer);
  }

  protected void doRender(final SMTestProxy proxy) {
    if (proxy.isSuite()) {
      mySuiteRenderer.customizeCellRenderer(null, myColumn.valueOf(proxy), false, false, 0, 0);
    } else {
      mySimpleTestRenderer.customizeCellRenderer(null, myColumn.valueOf(proxy), false, false, 0, 0);
    }
  }

  protected void doRender(final SMTestProxy proxy, final int row) {
    if (proxy.isSuite()) {
      mySuiteRenderer.customizeCellRenderer(null, myColumn.valueOf(proxy), false, false, row, 0);
    } else {
      mySimpleTestRenderer.customizeCellRenderer(null, myColumn.valueOf(proxy), false, false, row, 0);
    }
  }

  protected void assertFragmentsSize(final int expectedSize) {
    assertEquals(expectedSize, myFragmentsContainer.getFragments().size());
  }
}
