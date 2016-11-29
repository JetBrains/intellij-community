/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui.border;

import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.border.Border;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class CustomLineBorder implements Border {
  private final Color myColor;
  private final Insets myInsets;

  public CustomLineBorder(@Nullable Color color, @NotNull Insets insets) {
    myColor = color;
    myInsets = insets;
  }

  public CustomLineBorder(@Nullable Color color, int top, int left, int bottom, int right) {
    this(color, JBUI.insets(top, left, bottom, right));
  }

  public CustomLineBorder(@NotNull Insets insets) {
    this(null, insets);
  }

  public CustomLineBorder(int top, int left, int bottom, int right) {
    this(JBUI.insets(top, left, bottom, right));
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
    final Color oldColor = g.getColor();
    g.setColor(getColor());

    if (myInsets.left > 0) g.fillRect(x, y, myInsets.left, h);
    if (myInsets.bottom > 0) g.fillRect(x, y + h - myInsets.bottom, w, myInsets.bottom);
    if (myInsets.right> 0) g.fillRect(x + w - myInsets.right, y, myInsets.right, h);
    if (myInsets.top > 0) g.fillRect(x, y, w, myInsets.top);

    g.setColor(oldColor);
  }

  protected Color getColor() {
    return myColor == null ? UIUtil.getBorderColor() : myColor;
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return (Insets)myInsets.clone();
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }
}
