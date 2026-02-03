// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.icons.AllIcons;
import com.intellij.ui.components.ActionLink;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

final class ConfigurableEditorBanner extends SimpleBanner {
  private final JLabel myProjectIcon = new JLabel();
  // breadcrumbs for dialog, label for editor-based settings (non-modal)
  public final JComponent myHeaderText;

  ConfigurableEditorBanner(Action action, JComponent jLabel) {
    myHeaderText = jLabel;
    myProjectIcon.setMinimumSize(new Dimension(0, 0));
    myProjectIcon.setBorder(JBUI.Borders.empty(8, 4, 8, 8));
    myProjectIcon.setIcon(AllIcons.General.ProjectConfigurable);
    myProjectIcon.setForeground(UIUtil.getContextHelpForeground());
    showProject(false);
    myLeftPanel.removeAll();
    myLeftPanel.add(myHeaderText);
    myLeftPanel.add(myProjectIcon);
    myLeftPanel.add(new ActionLink(action)/*, BorderLayout.EAST*/);
    myLeftPanel.add(myProgress);
  }

  void setProjectText(@Nullable @Nls String projectText) {
    boolean visible = projectText != null;
    showProject(visible);
    if (visible) {
      myProjectIcon.setToolTipText(projectText);
    }
  }

  void showProject(boolean hasProject) {
    myProjectIcon.setVisible(hasProject);
  }

  @Override
  void setLeftComponent(Component component) {
    super.setLeftComponent(component);
    myHeaderText.setVisible(component == null);
  }

  @Override
  Component getBaselineTemplate() {
    return myHeaderText;
  }
}
