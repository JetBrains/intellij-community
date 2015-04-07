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

package com.intellij.execution.junit2.ui.model;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestTreeViewStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import org.jetbrains.annotations.NotNull;

class TestTreeStructure extends TestTreeViewStructure<TestProxy> {
  private final TestProxy myRootTest;
  private final JUnitConsoleProperties myProperties;
  private SpecialNode mySpecialNode;

  public TestTreeStructure(final TestProxy rootTest, final JUnitConsoleProperties properties) {
    myRootTest = rootTest;
    myProperties = properties;
  }

  public void setSpecialNode(final SpecialNode specialNode) { mySpecialNode = specialNode; }

  public Object getRootElement() {
    return myRootTest;
  }

  public Object[] getChildElements(final Object element) {
    final AbstractTestProxy[] children = ((TestProxy)element).selectChildren(getFilter());
    if (element == myRootTest) {
      if (children.length == 0 && myRootTest.getState().isPassed()) {
        mySpecialNode.setVisible(true);
        return mySpecialNode.asArray();
      }
      else {
        mySpecialNode.setVisible(false);
      }
    }

    return children;
  }

  public Object getParentElement(final Object element) {
    final TestProxy testProxy = (TestProxy)element;
    return testProxy.getParent();
  }

  @NotNull
  public TestProxyDescriptor createDescriptor(final Object element, final NodeDescriptor parentDescriptor) {
    final TestProxy testProxy = (TestProxy)element;
    return new TestProxyDescriptor(myProperties.getProject(), parentDescriptor, testProxy);
  }

  public void commit() {
  }

  public boolean hasSomethingToCommit() {
    return false;
  }
}
