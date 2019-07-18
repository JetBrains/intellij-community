// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class ShowNotificationIconsDialogAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    new DialogWrapper(e.getProject()) {
      private JPanel myPanel;

      {
        init();
        setResizable(false);
      }

      @NotNull
      @Override
      protected Action[] createLeftSideActions() {
        return new Action[]{new AbstractAction("&Repaint icons") {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (myPanel != null) {
              myPanel.removeAll();
              fillPanel(myPanel);
              myPanel.revalidate();
            }
          }
        }};
      }

      @Override
      protected JComponent createCenterPanel() {
        myPanel = new JPanel();
        myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));
        fillPanel(myPanel);
        return myPanel;
      }

      private void fillPanel(JPanel panel) {
        panel.add(createIconsRow(NotificationType.INFORMATION, true));
        panel.add(createIconsRow(NotificationType.INFORMATION, false));
        panel.add(createIconsRow(NotificationType.WARNING, true));
        panel.add(createIconsRow(NotificationType.WARNING, false));
        panel.add(createIconsRow(NotificationType.ERROR, true));
        panel.add(createIconsRow(NotificationType.ERROR, false));
      }

      @NotNull
      private JPanel createIconsRow(@NotNull NotificationType notificationType, boolean forToolWindow) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        for (int i = 0; i <= 10; i++) {
          LayeredIcon icon = IdeNotificationArea.createIconWithNotificationCount(row, notificationType, i, forToolWindow);
          JBLabel label = new JBLabel(icon);
          label.setMaximumSize(new JBDimension(30, 20));
          label.setMinimumSize(new JBDimension(30, 20));
          row.add(label);
        }
        return row;
      }
    }.show();
  }
}
