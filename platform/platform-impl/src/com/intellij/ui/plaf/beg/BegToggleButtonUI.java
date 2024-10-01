// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.plaf.beg;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalToggleButtonUI;
import java.awt.*;

@ApiStatus.Internal
public final class BegToggleButtonUI extends MetalToggleButtonUI{
  private static final BegToggleButtonUI begToggleButtonUI = new BegToggleButtonUI();

  public static ComponentUI createUI(JComponent c) {
    return begToggleButtonUI;
  }

  @Override
  protected void paintFocus(Graphics g, AbstractButton b, Rectangle viewRect, Rectangle textRect, Rectangle iconRect) {
    g.setColor(getFocusColor());
    UIUtil.drawDottedRectangle(g, viewRect.x, viewRect.y, viewRect.x + viewRect.width, viewRect.y + viewRect.height);
  }
}
