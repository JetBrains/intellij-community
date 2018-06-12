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
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBValue;
import com.intellij.util.ui.JBValue.JBValueGroup;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class RectanglePainter {
  private static final JBValueGroup JBVG = new JBValueGroup();
  protected static final JBValue TEXT_PADDING_X = JBVG.value(5);
  public static final JBValue TOP_TEXT_PADDING = JBVG.value(2);
  public static final JBValue BOTTOM_TEXT_PADDING = JBVG.value(1);
  public static final JBValue LABEL_ARC = JBVG.value(6);

  private final boolean mySquare;

  public RectanglePainter(boolean square) {
    mySquare = square;
  }

  public static Font getFont() {
    return UIUtil.getLabelFont();
  }

  protected Font getLabelFont() {
    return getFont();
  }

  public void paint(@NotNull Graphics2D g2, @NotNull String text, int paddingX, int paddingY, @NotNull Color color) {
    GraphicsConfig config = GraphicsUtil.setupAAPainting(g2);
    g2.setFont(getLabelFont());
    g2.setStroke(new BasicStroke(1.5f));

    FontMetrics fontMetrics = g2.getFontMetrics();
    int width = fontMetrics.stringWidth(text) + 2 * TEXT_PADDING_X.get();
    int height = fontMetrics.getHeight() + TOP_TEXT_PADDING.get() + BOTTOM_TEXT_PADDING.get();

    g2.setColor(color);
    if (mySquare) {
      g2.fillRect(paddingX, paddingY, width, height);
    }
    else {
      g2.fill(new RoundRectangle2D.Double(paddingX, paddingY, width, height, LABEL_ARC.get(), LABEL_ARC.get()));
    }

    g2.setColor(JBColor.BLACK);
    int x = paddingX + TEXT_PADDING_X.get();
    int y = paddingY + SimpleColoredComponent.getTextBaseLine(fontMetrics, height);
    g2.drawString(text, x, y);

    config.restore();
  }

  public Dimension calculateSize(@NotNull String text, @NotNull FontMetrics metrics) {
    int width = metrics.stringWidth(text) + 2 * TEXT_PADDING_X.get();
    int height = metrics.getHeight() + TOP_TEXT_PADDING.get() + BOTTOM_TEXT_PADDING.get();
    return new Dimension(width, height);
  }
}
