package com.intellij.execution.junit2.ui;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.ui.actions.ViewAssertEqualsDiffAction;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestTreeView;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;

public class JUnitTestTreeView extends TestTreeView {

  protected TreeTestRenderer getRenderer(TestConsoleProperties properties) {
    return new TreeTestRenderer(properties);
  }

  protected TestProxy getSelectedTest(@NotNull final TreePath selectionPath) {
    return TestProxyClient.from(selectionPath.getLastPathComponent());
  }

  public String convertValueToText(final Object value,
                                   final boolean selected,
                                   final boolean expanded,
                                   final boolean leaf,
                                   final int row,
                                   final boolean hasFocus) {
    return Formatters.printTest(TestProxyClient.from(value));
  }

  protected void installHandlers() {
    super.installHandlers();
    ViewAssertEqualsDiffAction.registerShortcut(this);
  }
}
