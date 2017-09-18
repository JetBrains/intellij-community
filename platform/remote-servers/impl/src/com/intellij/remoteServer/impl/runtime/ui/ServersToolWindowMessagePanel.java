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

import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class ServersToolWindowMessagePanel implements ServersToolWindowContent.MessagePanel {
  private JPanel myPanel;
  private JEditorPane myMessageArea;

  public ServersToolWindowMessagePanel() {
    myMessageArea.setBackground(UIUtil.getPanelBackground());
    myMessageArea.setBorder(JBUI.Borders.empty());
    myMessageArea.setCaretPosition(0);
    myMessageArea.setEditable(false);
  }

  @Override
  public void setEmptyText(@NotNull String text) {
    myMessageArea.setText("<html><head>" +
                          UIUtil.getCssFontDeclaration(UIUtil.getLabelFont(), null, null, null) +
                          "<style>body {" +
                          "text-align: center; white-space: normal;" +
                          "background: #" + ColorUtil.toHex(UIUtil.getPanelBackground()) + ";" +
                          "color: #" + ColorUtil.toHex(UIUtil.getInactiveTextColor()) + ";" +
                          "}</style>" +
                          "</head><body>" + text + "</body></html>");
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }
}
