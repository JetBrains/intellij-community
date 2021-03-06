/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.remoteServer.CloudBundle;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class ServersToolWindowMessagePanel implements RemoteServersDeploymentManager.MessagePanel {
  private JPanel myPanel;
  private JEditorPane myMessageArea;
  private String myCurrentText;

  ServersToolWindowMessagePanel() {
    myMessageArea.setBackground(UIUtil.getPanelBackground());
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
    myMessageArea.setText(CloudBundle.message("editor.pane.text.empty.text", UIUtil.getCssFontDeclaration(UIUtil.getLabelFont(), null, null, null),
                                     ColorUtil.toHex(UIUtil.getPanelBackground()), ColorUtil.toHex(UIUtil.getInactiveTextColor()), text));
    myCurrentText = text;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }
}
