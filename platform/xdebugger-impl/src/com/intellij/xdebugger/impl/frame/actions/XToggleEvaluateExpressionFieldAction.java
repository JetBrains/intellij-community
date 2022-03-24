// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.frame.XWatchesViewImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Toggles new evaluate expression field visibility in the watches panel.
 *
 * <p>Use {@link #markAsEvaluateExpressionField(JComponent)} to mark
 * a component as a field that should be shown/hidden when action is called.</p>
 *
 * <p>The initial visibility is set by {@link #markAsEvaluateExpressionField(JComponent)}
 * Use {@link #isEvaluateExpressionFieldEnabled()} to get current state of the option.</p>
 */
public class XToggleEvaluateExpressionFieldAction extends DumbAwareToggleAction {

  private static final Key<String> EVALUATE_EXPRESSION_FIELD = Key.create("Evaluate Expression Field");
  private static final String PROP_KEY = "XToggleEvaluateFieldAction.EvaluateExpressionField.enabled";
  private static final boolean IS_ENABLED_BY_DEFAULT = true;

  public static boolean isEvaluateExpressionFieldEnabled() {
    return PropertiesComponent.getInstance().getBoolean(PROP_KEY, IS_ENABLED_BY_DEFAULT);
  }

  /**
   * Mark a component as evaluate expression field and set visibility according to the current option state.
   * @param component evaluate expression field
   */
  public static void markAsEvaluateExpressionField(@NotNull JComponent component) {
    ComponentUtil.putClientProperty(component, EVALUATE_EXPRESSION_FIELD, "EvaluateExpressionField");
    component.setVisible(isEvaluateExpressionFieldEnabled());
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return isEvaluateExpressionFieldEnabled();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    PropertiesComponent.getInstance().setValue(PROP_KEY, state, IS_ENABLED_BY_DEFAULT);
    Project project = e.getProject();
    if (project != null) {
      var window = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DEBUG);
      if (window != null) {
        for (Content content : window.getContentManager().getContents()) {
          var context = DataManager.getInstance().getDataContext(content.getComponent());
          findAllFieldsAndUpdateState(context, state);
        }
      }
    }
    // fallback in case of Services tool window
    findAllFieldsAndUpdateState(e.getDataContext(), state);
  }

  private static void findAllFieldsAndUpdateState(DataContext context, boolean state) {
    XWatchesView view = context.getData(XWatchesView.DATA_KEY);
    if (view instanceof XWatchesViewImpl) {
      JPanel panel = ((XWatchesViewImpl)view).getPanel();
      UIUtil.uiTraverser(panel)
        .filter(c -> c instanceof JComponent && ComponentUtil.getClientProperty((JComponent)c, EVALUATE_EXPRESSION_FIELD) != null)
        .forEach(c -> c.setVisible(state));
    }
  }
}
