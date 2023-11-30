// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.plaf.beg;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalRadioButtonUI;
import java.awt.*;

public final class BegRadioButtonUI extends MetalRadioButtonUI {
  private static final BegRadioButtonUI begRadioButtonUI = new BegRadioButtonUI();

  public static ComponentUI createUI(JComponent c) {
    return begRadioButtonUI;
  }

  @Override
  protected void paintFocus(Graphics g, Rectangle t, Dimension d) {
    g.setColor(getFocusColor());
    UIUtil.drawDottedRectangle(g, t.x - 2, t.y - 1, t.x + t.width + 1, t.y + t.height);
  }
}