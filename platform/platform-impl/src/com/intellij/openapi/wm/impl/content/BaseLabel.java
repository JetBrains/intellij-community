/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.EngravedTextGraphics;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.WatermarkIcon;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import java.awt.*;

public class BaseLabel extends JLabel {
  protected ToolWindowContentUi myUi;

  private Color myActiveFg;
  private Color myPassiveFg;
  private boolean myBold;

  public BaseLabel(ToolWindowContentUi ui, boolean bold) {
    myUi = ui;
    setOpaque(false);
    myBold = bold;
  }

  @Override
  public void updateUI() {
    setActiveFg(JBColor.foreground());
    setPassiveFg(new JBColor(Gray._75, UIUtil.getLabelDisabledForeground()));
    super.updateUI();
  }

  @Override
  public Font getFont() {
    Font f = UIUtil.getLabelFont();
    f = f.deriveFont(f.getStyle(), Math.max(11, f.getSize() - 2));
    if (myBold) {
      f = f.deriveFont(Font.BOLD);
    }

    return f;
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
    putClientProperty(SwingUtilities2.AA_TEXT_PROPERTY_KEY, AntialiasingType.getAAHintForSwingComponent());
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
    return myActiveFg;
  }

  protected Color getPassiveFg(boolean selected) {
    return myPassiveFg;
  }

  protected void updateTextAndIcon(Content content, boolean isSelected) {
    if (content == null) {
      setText(null);
      setIcon(null);
    }
    else {
      setText(content.getDisplayName());
      setActiveFg(getActiveFg(isSelected));
      setPassiveFg(getPassiveFg(isSelected));

      setToolTipText(content.getDescription());

      final boolean show = Boolean.TRUE.equals(content.getUserData(ToolWindow.SHOW_CONTENT_ICON)) 
                           || content.getComponent() instanceof Iconable;
      Icon icon = content.getIcon();
      if (content.getComponent() instanceof Iconable) { // handling tabbed content after 'split group' action
        icon = ((Iconable)content.getComponent()).getIcon(Iconable.ICON_FLAG_VISIBILITY);
      }
      if (show) {
        if (isSelected) {
          setIcon(icon);
        }
        else {
          setIcon(icon != null ? new WatermarkIcon(icon, .5f) : null);
        }
      }
      else {
        setIcon(null);
      }

      myBold = false; //isSelected;
    }
  }

  public Content getContent() {
    return null;
  }
}
