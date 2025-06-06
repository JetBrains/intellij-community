// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.remoteServer.CloudBundle;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class ServersToolWindowMessagePanel implements RemoteServersDeploymentManager.MessagePanel {
  private JPanel myPanel;
  private JEditorPane myMessageArea;
  private String myCurrentText;

  ServersToolWindowMessagePanel() {
    myPanel.setBackground(JBUI.CurrentTheme.ToolWindow.background());
    myMessageArea.setBackground(myPanel.getBackground());
    myMessageArea.setBorder(JBUI.Borders.empty());
    if (myMessageArea.getCaret() != null) {
      myMessageArea.setCaretPosition(0);
    }
    myMessageArea.setEditable(false);
  }

  @Override
  public void setEmptyText(@NotNull String text) {
    if (text.equals(myCurrentText)) {
      return;
    }
    myMessageArea.setText(CloudBundle.message("editor.pane.text.empty.text", UIUtil.getCssFontDeclaration(StartupUiUtil.getLabelFont(), null, null, null),
                                              ColorUtil.toHex(myPanel.getBackground()), ColorUtil.toHex(
        NamedColorUtil.getInactiveTextColor()), text));
    myCurrentText = text;
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myPanel;
  }
}
