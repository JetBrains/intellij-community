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

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTRunnerNodeDescriptor;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author: Roman Chernyatchik
 */
public class TestTreeRenderer extends ColoredTreeCellRenderer {
  @NonNls private static final String SPACE_STRING = " ";

  private final TestConsoleProperties myConsoleProperties;
  private SMRootTestProxyFormatter myAdditionalRootFormatter;

  public TestTreeRenderer(final TestConsoleProperties consoleProperties) {
    myConsoleProperties = consoleProperties;
  }

  public void customizeCellRenderer(final JTree tree,
                                    final Object value,
                                    final boolean selected,
                                    final boolean expanded,
                                    final boolean leaf,
                                    final int row,
                                    final boolean hasFocus) {
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
    final Object userObj = node.getUserObject();
    if (userObj instanceof SMTRunnerNodeDescriptor) {
      final SMTRunnerNodeDescriptor desc = (SMTRunnerNodeDescriptor)userObj;
      final SMTestProxy testProxy = desc.getElement();

      if (testProxy instanceof SMTestProxy.SMRootTestProxy) {
        SMTestProxy.SMRootTestProxy rootTestProxy = (SMTestProxy.SMRootTestProxy) testProxy;
        if (rootTestProxy.isLeaf()) {
          TestsPresentationUtil.formatRootNodeWithoutChildren(rootTestProxy, this);
        } else {
          TestsPresentationUtil.formatRootNodeWithChildren(rootTestProxy, this);
        }
        if (myAdditionalRootFormatter != null) {
          myAdditionalRootFormatter.format(rootTestProxy, this);
        }
      } else {
        TestsPresentationUtil.formatTestProxy(testProxy, this);
      }
      //Done
      return;
    }

    //strange node
    final String text = node.toString();
    //no icon
    append(text != null ? text : SPACE_STRING, SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  public TestConsoleProperties getConsoleProperties() {
    return myConsoleProperties;
  }

  public void setAdditionalRootFormatter(@NotNull SMRootTestProxyFormatter formatter) {
    myAdditionalRootFormatter = formatter;
  }

  public void removeAdditionalRootFormatter() {
    myAdditionalRootFormatter = null;
  }
}
