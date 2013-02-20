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
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * User: Vassiliy.Kudryashov
 */
public class TwoColorsIcon extends EmptyIcon {
  @NotNull private final Color myColor1;
  @NotNull private final Color myColor2;

  public TwoColorsIcon(int size, @NotNull Color color1, @NotNull Color color2) {
    super(size, size);
    myColor1 = color1;
    myColor2 = color2;
  }

  @Override
  public void paintIcon(final Component component, final Graphics g, final int x, final int y) {
    final int w = getIconWidth();
    final int h = getIconHeight();
    GraphicsUtil.setupAAPainting(g);
    g.setColor(myColor1);
    g.fillPolygon(new int[]{x, x + w, x}, new int[]{y, y, y + h}, 3);
    g.setColor(myColor2);
    g.fillPolygon(new int[]{x + w, x + w, x}, new int[]{y, y + h, y + h}, 3);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    TwoColorsIcon icon = (TwoColorsIcon)o;

    if (getIconWidth() != icon.getIconWidth()) return false;
    if (getIconHeight() != icon.getIconHeight()) return false;
    if (!myColor1.equals(icon.myColor1)) return false;
    if (!myColor2.equals(icon.myColor2)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myColor1.hashCode();
    result = 31 * result + myColor2.hashCode();
    return result;
  }
}
