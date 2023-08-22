// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

import static javax.swing.SwingConstants.TOP;

final class JTabbedPaneDemoAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {

    DialogWrapper dialog = new DialogWrapper(e.getProject(), false, DialogWrapper.IdeModalityType.MODELESS) {

      @Override
      protected @NotNull JComponent createCenterPanel() {
        JTabbedPane tabbedPane = new JTabbedPane(TOP, JTabbedPane.SCROLL_TAB_LAYOUT) {
          @Override
          public Dimension getPreferredSize() {
            return new JBDimension(600, 400);
          }
        };
        {
          for (int i = 1; i < 22; i++) {
            JLabel label = new JLabel("Label #" + i, SwingConstants.CENTER);
            label.setBorder(JBUI.Borders.customLine(new Color(255, 255, 255, 128), 3));
            Icon icon = (i == 19) ? AllIcons.FileTypes.Any_type : null;
            tabbedPane.addTab("Tab #" + i, icon, label);
          }
        }
        return tabbedPane;
      }

      @Override
      protected @NotNull Action[] createActions() {
        return ArrayUtil.append(super.createActions(), new AbstractAction("Next Tab Placement") {
          @Override
          public void actionPerformed(ActionEvent e) {
            JTabbedPane tabbedPane = UIUtil.findComponentOfType(getRootPane(), JTabbedPane.class);
            if (tabbedPane == null) return;
            int placement = tabbedPane.getTabPlacement();
            placement++;
            if (placement > SwingConstants.RIGHT) placement = TOP;
            tabbedPane.setTabPlacement(placement);
          }
        });
      }

      @Override
      protected void createDefaultActions() {
        super.createDefaultActions();

        init();
      }
    };
    dialog.setTitle(getTemplateText());
    dialog.setSize(400, 400);
    dialog.show();
  }
}
