// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;


final class EditorTablessBorder extends SideBorder {

  private final Project myProject;
  private final JPanel myHeaderPanel;

  EditorTablessBorder(Project project, JPanel headerPanel) {
    super(JBColor.border(), ALL);
    myProject = project;
    myHeaderPanel = headerPanel;
  }

  @Override
  public void paintBorder(@NotNull Component c, @NotNull Graphics g, int x, int y, int width, int height) {
    if (c instanceof JComponent) {
      Insets insets = ((JComponent)c).getInsets();
      if (insets.left > 0) {
        super.paintBorder(c, g, x, y, width, height);
      }
      else {
        g.setColor(UIUtil.getPanelBackground());
        g.fillRect(x, y, width, 1);
        g.setColor(Gray._50.withAlpha(90));
        g.fillRect(x, y, width, 1);
      }
    }
  }

  @Override
  public @NotNull Insets getBorderInsets(Component c) {
    Container splitters = SwingUtilities.getAncestorOfClass(EditorsSplitters.class, c);
    boolean thereIsSomethingAbove = !SystemInfo.isMac ||
                                    UISettings.getInstance().getShowMainToolbar() ||
                                    UISettings.getInstance().getShowNavigationBar() ||
                                    toolWindowIsNotEmpty();
    //noinspection ConstantConditions
    Component header = myHeaderPanel == null ? null : ArrayUtil.getFirstElement(myHeaderPanel.getComponents());
    boolean paintTop = thereIsSomethingAbove && header == null && UISettings.getInstance().getEditorTabPlacement() != SwingConstants.TOP;
    return splitters == null ? super.getBorderInsets(c) : JBUI.insetsTop(paintTop ? 1 : 0);
  }

  private boolean toolWindowIsNotEmpty() {
    if (myProject == null) {
      return false;
    }
    return !ToolWindowManagerEx.getInstanceEx(myProject).getIdsOn(ToolWindowAnchor.TOP).isEmpty();
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }
}
