package com.intellij.execution.junit2.ui.actions;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.states.ComparisonFailureState;
import com.intellij.execution.junit2.states.TestState;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

public class ViewAssertEqualsDiffAction extends AnAction {
  @NonNls public static final String ACTION_ID = "openAssertEqualsDiff";
  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final AbstractTestProxy testProxy = TestProxy.fromDataContext(dataContext);
    final ComparisonFailureState state = (ComparisonFailureState)((TestProxy)testProxy).getState();
    state.openDiff(DataKeys.PROJECT.getData(dataContext));
  }

  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final boolean enabled;
    final DataContext dataContext = e.getDataContext();
    if (dataContext.getData(DataConstants.PROJECT) == null) enabled = false;
    else {
      final AbstractTestProxy test = TestProxy.fromDataContext(dataContext);
      if (test instanceof TestProxy) {
        final TestState state = ((TestProxy)test).getState();
        enabled = state instanceof ComparisonFailureState;
      } else {
        enabled = false;
      }
    }
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }

  public static void registerShortcut(final JComponent component) {
    ActionManager.getInstance().getAction(ACTION_ID).registerCustomShortcutSet(CommonShortcuts.ALT_ENTER, component);
  }
}
