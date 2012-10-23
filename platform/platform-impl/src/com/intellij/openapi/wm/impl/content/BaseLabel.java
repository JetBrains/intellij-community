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

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.EngravedTextGraphics;
import com.intellij.ui.Gray;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.WatermarkIcon;

import javax.swing.*;
import java.awt.*;

public class BaseLabel extends JLabel {

  protected static final int TAB_SHIFT = 1;
  private static final Color DEFAULT_ACTIVE_FORE = UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : Color.black;
  private static final Color DEFAULT_PASSIVE_FORE = UIUtil.isUnderDarcula()? UIUtil.getLabelDisabledForeground() : Gray._75;

  protected ToolWindowContentUi myUi;

  private Color myActiveFg;
  private Color myPassiveFg;
  private boolean myBold;

  public BaseLabel(ToolWindowContentUi ui, boolean bold) {
    myUi = ui;
    setOpaque(false);
    setActiveFg(DEFAULT_ACTIVE_FORE);
    setPassiveFg(DEFAULT_PASSIVE_FORE);
    myBold = bold;
    updateFont();
  }

  public void updateUI() {
    super.updateUI();
    updateFont();
  }

  private void updateFont() {
    Font baseFont = getLabelFont();
    if (myBold) {
      setFont(baseFont.deriveFont(Font.BOLD));
    }
    else {
      setFont(baseFont);
    }
  }

  public static Font getLabelFont() {
    Font f = UIUtil.getLabelFont();
    return f.deriveFont(f.getStyle(), Math.max(11, f.getSize() - 2));
  }

  public void setActiveFg(final Color fg) {
    myActiveFg = fg;
  }

  public void setPassiveFg(final Color passiveFg) {
    myPassiveFg = passiveFg;
  }

  protected void paintComponent(final Graphics g) {
    final Color fore = myUi.myWindow.isActive() ? myActiveFg : myPassiveFg;
    setForeground(fore);
    super.paintComponent(_getGraphics((Graphics2D)g));
  }
  
  protected Graphics _getGraphics(Graphics2D g) {
    if (!allowEngravement()) return g;
    Color foreground = getForeground();
    if (Color.BLACK.equals(foreground)) {
      return new EngravedTextGraphics(g);
    } 
    
    return g;
  }

  protected boolean allowEngravement() {
    return true;
  }

  protected Color getActiveFg(boolean selected) {
    return DEFAULT_ACTIVE_FORE;
  }

  protected Color getPassiveFg(boolean selected) {
    return DEFAULT_PASSIVE_FORE;
  }

  protected void updateTextAndIcon(Content content, boolean isSelected) {
    if (content == null) {
      setText(null);
      setIcon(null);
    } else {
      setText(content.getDisplayName());
      setActiveFg(getActiveFg(isSelected));
      setPassiveFg(getPassiveFg(isSelected));

      setToolTipText(content.getDescription());

      final boolean show = Boolean.TRUE.equals(content.getUserData(ToolWindow.SHOW_CONTENT_ICON));
      if (show) {
       if (isSelected) {
         setIcon(content.getIcon());
       } else {
         setIcon(content.getIcon() != null ? new WatermarkIcon(content.getIcon(), .5f) : null);
       }
      } else {
        setIcon(null);
      }

      myBold = false; //isSelected;
      updateFont();
    }
  }


  public Content getContent() {
    return null;
  }
}
