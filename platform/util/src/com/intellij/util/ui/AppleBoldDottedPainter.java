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
package com.intellij.util.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws a 'apple-like' bold dotted line. Instances are cached for performance reasons.
 * <p/>
 * Each dot has this transparency core:
 * | 20%  | 50%  | 20% | 0% |
 * | 70%  | 70%  | 70% | 0% |
 * | 50%  | 100% | 50% | 0% |
 * <p/>
 * This class is not thread-safe, it's supposed to be used in EDT only.
 */
public class AppleBoldDottedPainter {
  private static final int HEIGHT = 3;
  private static final int WIDTH = 4;

  private static final Map<Color, AppleBoldDottedPainter> myPainters = new HashMap<Color, AppleBoldDottedPainter>();
  private static final int PATTERN_WIDTH = 4000;

  private final BufferedImage myImage;

  @SuppressWarnings("PointlessArithmeticExpression")
  private AppleBoldDottedPainter(Color color) {
    myImage = UIUtil.createImage(PATTERN_WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = myImage.createGraphics();
    try {
      g.setColor(color);
      for (int i = 0; i < PATTERN_WIDTH / WIDTH + 1; i++) {
        int offset = i * WIDTH;

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, .2f));
        g.drawLine(offset + 0, 0, offset + 0, 0);
        g.drawLine(offset + 2, 0, offset + 2, 0);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, 0.7f));
        g.drawLine(offset + 0, 1, offset + 2, 1);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, 1.0f));
        g.drawLine(offset + 1, 2, offset + 1, 2);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, .5f));
        g.drawLine(offset + 1, 0, offset + 1, 0);
        g.drawLine(offset + 0, 2, offset + 0, 2);
        g.drawLine(offset + 2, 2, offset + 2, 2);
      }
    }
    finally {
      g.dispose();
    }
  }

  public void paint(Graphics2D g, int xStart, int xEnd, int y) {
    Shape oldClip = g.getClip();

    final int startPosCorrection = xStart % WIDTH == WIDTH - 1 ? 1 : 0;
    final int dotX0 = (xStart / WIDTH + startPosCorrection) * WIDTH; // draw lines in common phase
    final int width = ((xEnd - dotX0 - 1) / WIDTH + 1) * WIDTH; // always paint whole dot

    final Rectangle rectangle = new Rectangle(dotX0, y, width, HEIGHT);
    final Rectangle lineClip = oldClip != null ? oldClip.getBounds().intersection(rectangle) : rectangle;
    if (lineClip.isEmpty()) return;

    Composite oldComposite = g.getComposite();
    try {
      g.setComposite(AlphaComposite.SrcOver);
      g.setClip(lineClip);
      UIUtil.drawImage(g, myImage, dotX0, y, null);
    }
    finally {
      g.setComposite(oldComposite);
      g.setClip(oldClip);
    }
  }

  public static AppleBoldDottedPainter forColor(Color color) {
    AppleBoldDottedPainter painter = myPainters.get(color);
    if (painter == null) {
      painter = new AppleBoldDottedPainter(color);
      // creating a new Color instance, as the one passed as parameter can be mutable (JBColor) and shouldn't be used as a map key
      //noinspection UseJBColor
      myPainters.put(new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()), painter);
    }
    return painter;
  }
}
