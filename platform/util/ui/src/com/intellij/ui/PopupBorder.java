// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public interface PopupBorder extends Border {

  void setActive(boolean active);

  class Factory {
    private Factory() { }

    @NotNull
    public static PopupBorder createEmpty() {
      return new BaseBorder();
    }

    @NotNull
    public static PopupBorder create(boolean active, boolean windowWithShadow) {
      boolean visible = !(SystemInfo.isMac && windowWithShadow) || UIManager.getBoolean("Popup.paintBorder") == Boolean.TRUE;
      PopupBorder border = new BaseBorder(visible, JBUI.CurrentTheme.Popup.borderColor(true), JBUI.CurrentTheme.Popup.borderColor(false));
      border.setActive(active);
      return border;
    }

    public static PopupBorder createColored(Color color) {
      PopupBorder border = new BaseBorder(true, color, color);
      border.setActive(true);
      return border;
    }
  }

  class BaseBorder implements PopupBorder {
    private final boolean myVisible;
    private final Color myActiveColor;
    private final Color myPassiveColor;
    private boolean myActive;

    protected BaseBorder() {
      this(false, null, null);
    }

    protected BaseBorder(final boolean visible, final Color activeColor, final Color passiveColor) {
      myVisible = visible;
      myActiveColor = activeColor;
      myPassiveColor = passiveColor;
    }

    @Override
    public void setActive(final boolean active) {
      myActive = active;
    }

    @Override
    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      if (!myVisible) return;

      Color color = myActive ? myActiveColor : myPassiveColor;
      g.setColor(color);
      RectanglePainter.DRAW.paint((Graphics2D)g, x, y, width, height, null);
    }

    @Override
    public Insets getBorderInsets(final Component c) {
      return myVisible ? JBUI.insets(1) : JBUI.emptyInsets();
    }

    @Override
    public boolean isBorderOpaque() {
      return true;
    }
  }
}
