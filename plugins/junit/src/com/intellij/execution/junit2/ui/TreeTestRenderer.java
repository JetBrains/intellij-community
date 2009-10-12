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

package com.intellij.execution.junit2.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

class TreeTestRenderer extends ColoredTreeCellRenderer {
  private final TestConsoleProperties myProperties;

  public TreeTestRenderer(final TestConsoleProperties properties) {
    myProperties = properties;
  }

  public void customizeCellRenderer(
      final JTree tree,
      final Object value,
      final boolean selected,
      final boolean expanded,
      final boolean leaf,
      final int row,
      final boolean hasFocus
      ) {
    final TestProxy testProxy = TestProxyClient.from(value);
    if (testProxy != null) {
      TestRenderer.renderTest(testProxy, this);
      setIcon(TestRenderer.getIconFor(testProxy, myProperties.isPaused()));
    } else {
      append(ExecutionBundle.message("junit.runing.info.loading.tree.node.text"), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
