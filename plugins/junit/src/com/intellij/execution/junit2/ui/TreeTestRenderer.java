package com.intellij.execution.junit2.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

class TreeTestRenderer extends ColoredTreeCellRenderer {
  private TestConsoleProperties myProperties;

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
      setIcon(TestRenderer.getIconFor(testProxy, myProperties.getDebugSession()));
    } else {
      append(ExecutionBundle.message("junit.runing.info.loading.tree.node.text"), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}