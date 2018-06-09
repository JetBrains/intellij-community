// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.find.FindBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Bas Leijdekkers
 */
public class ScopePanel extends JPanel {

  private final JPanel myScopeDetailsPanel = new JPanel(new CardLayout());

  public ScopePanel() {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    myScopeDetailsPanel.setBorder(JBUI.Borders.emptyBottom(UIUtil.isUnderDefaultMacTheme() ? 0 : 3));
    final DefaultActionGroup scopeActionGroup = new DefaultActionGroup(
      new ScopeToggleAction(FindBundle.message("find.popup.scope.project")),
      new ScopeToggleAction(FindBundle.message("find.popup.scope.module")),
      new ScopeToggleAction(FindBundle.message("find.popup.scope.directory")),
      new ScopeToggleAction(FindBundle.message("find.popup.scope.scope"))
    );
    ActionToolbarImpl toolbar =
      (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar("ScopePanel", scopeActionGroup, true);
    toolbar.setForceMinimumSize(true);
    toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    //toolbar.setMinimumButtonSize(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    add(toolbar);
    add(myScopeDetailsPanel);
  }

  class ScopeToggleAction extends ToggleAction {

    public ScopeToggleAction(@Nullable String text) {
      super(text, null, EmptyIcon.ICON_0);
      //getTemplatePresentation().setHoveredIcon(EmptyIcon.ICON_0);
      getTemplatePresentation().setDisabledIcon(EmptyIcon.ICON_0);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return false;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {

    }

    @Override
    public boolean displayTextInToolbar() {
      return true;
    }
  }
}
