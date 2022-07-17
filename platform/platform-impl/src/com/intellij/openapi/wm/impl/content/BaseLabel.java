// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.WatermarkIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

@DirtyUI
public class BaseLabel extends JLabel {
  protected ToolWindowContentUi myUi;

  private Color myActiveFg;
  private Color myPassiveFg;
  private Color myTabColor;
  private boolean myBold;

  public BaseLabel(@NotNull ToolWindowContentUi ui, boolean bold) {
    myUi = ui;
    setOpaque(false);
    myBold = bold;
    addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        repaint();
      }
      @Override
      public void focusLost(FocusEvent e) {
        repaint();
      }
    });
    GraphicsUtil.setAntialiasingType(this, AntialiasingType.getAAHintForSwingComponent());

    if (ExperimentalUI.isNewUI()) {
      setBorder(JBUI.Borders.empty(JBUI.CurrentTheme.ToolWindow.headerLabelLeftRightInsets()));
    }
  }

  @Override
  public void updateUI() {
    setActiveFg(JBColor.foreground());
    setPassiveFg(JBColor.foreground());
    super.updateUI();
  }

  @Override
  public Font getFont() {
    Font font = getLabelFont();
    if (myBold) {
      font = font.deriveFont(Font.BOLD);
    }

    return font;
  }

  public static Font getLabelFont() {
    Font font = JBUI.CurrentTheme.ToolWindow.headerFont();
    return font.deriveFont(font.getSize() + JBUI.CurrentTheme.ToolWindow.overrideHeaderFontSizeOffset());
  }

  public void setActiveFg(final Color fg) {
    myActiveFg = fg;
  }

  public void setPassiveFg(final Color passiveFg) {
    myPassiveFg = passiveFg;
  }

  @Override
  protected void paintComponent(final Graphics g) {
    final Color fore = myUi.window.isActive() ? myActiveFg : myPassiveFg;
    setForeground(fore);
    super.paintComponent(_getGraphics((Graphics2D)g));

    if (isFocusOwner()) {
      UIUtil.drawLabelDottedRectangle(this, g);
    }
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

  protected void updateTextAndIcon(Content content, boolean isSelected, boolean isBold) {
    if (content == null) {
      setText(null);
      setIcon(null);
      myTabColor = null;
    }
    else {
      setText(content.getDisplayName());
      setActiveFg(getActiveFg(isSelected));
      setPassiveFg(getPassiveFg(isSelected));
      myTabColor = content.getTabColor();

      setToolTipText(content.getDescription());

      final boolean show = Boolean.TRUE.equals(content.getUserData(ToolWindow.SHOW_CONTENT_ICON));
      if (show) {
        ComponentOrientation componentOrientation = content.getUserData(Content.TAB_LABEL_ORIENTATION_KEY);
        if(componentOrientation != null) {
          setComponentOrientation(componentOrientation);
        }
        Icon icon = OffsetIcon.getOriginalIcon(content.getIcon());
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

      myBold = isBold;
    }
  }

  public @Nullable Color getTabColor() {
    return myTabColor;
  }

  @Nullable
  public Content getContent() {
    return null;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleBaseLabel();
    }
    return accessibleContext;
  }

  protected class AccessibleBaseLabel extends AccessibleJLabel {
  }
}
