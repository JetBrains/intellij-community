/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui.plaf.beg;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalToggleButtonUI;
import java.awt.*;

/**
 * User: Vassiliy.Kudryashov
 */
public class BegToggleButtonUI extends MetalToggleButtonUI{
  private final static BegToggleButtonUI begToggleButtonUI = new BegToggleButtonUI();

  public static ComponentUI createUI(JComponent c) {
    return begToggleButtonUI;
  }

  @Override
  protected void paintFocus(Graphics g, AbstractButton b, Rectangle viewRect, Rectangle textRect, Rectangle iconRect) {
    g.setColor(getFocusColor());
    UIUtil.drawDottedRectangle(g, viewRect.x, viewRect.y, viewRect.x + viewRect.width, viewRect.y + viewRect.height);
  }
}
