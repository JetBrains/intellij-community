/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.content;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

class BaseLabel extends JLabel {

  protected ToolWindowContentUi myUi;

  private Color myActiveFg;
  private Color myPassiveFg;
  private final boolean myBold;

  public BaseLabel(ToolWindowContentUi ui, boolean bold) {
    myUi = ui;
    setOpaque(false);
    setActiveFg(Color.white);
    setPassiveFg(Color.white);
    myBold = bold;
    updateFont();
  }

  public void updateUI() {
    super.updateUI();
    updateFont();
  }

  private void updateFont() {
    if (myBold) {
      setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    }
  }

  public void setActiveFg(final Color fg) {
    myActiveFg = fg;
  }

  public void setPassiveFg(final Color passiveFg) {
    myPassiveFg = passiveFg;
  }

  protected void paintComponent(final Graphics g) {
    setForeground(myUi.myWindow.isActive() ? myActiveFg : myPassiveFg);
    super.paintComponent(g);
  }

}
