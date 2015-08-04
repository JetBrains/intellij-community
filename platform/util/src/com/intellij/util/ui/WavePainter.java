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
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws a 'wavy' line of 1-pixel amplitude. Instances are cached for performance reasons.
 * <p/>
 * This class is not thread-safe, it's supposed to be used in EDT only.
 */
public class WavePainter {
  private static final float STROKE_WIDTH = 0.7f;

  private static final Map<Color, WavePainter> myPainters = new HashMap<Color, WavePainter>();
  private static final int PATTERN_WIDTH = 4000;

  private final BufferedImage myImage;

  private WavePainter(Color color) {
    myImage = UIUtil.createImage(PATTERN_WIDTH, 3, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = myImage.createGraphics();
    try {
      GraphicsUtil.setupAAPainting(g);
      g.setStroke(new BasicStroke(STROKE_WIDTH));
      g.setColor(color);
      double height = 1;
      double cycle = 4 * height;
      final double wavedAt = 3 - STROKE_WIDTH - height;
      GeneralPath wavePath = new GeneralPath();
      wavePath.moveTo(0, wavedAt - Math.cos(0 * 2 * Math.PI / cycle) * height);
      for (int x = 0; x < PATTERN_WIDTH; x++) {
        wavePath.lineTo(x, wavedAt - Math.cos(x * 2 * Math.PI / cycle) * height);
      }
      g.draw(wavePath);
    }
    finally {
      g.dispose();
    }
  }

  /**
   * Paints a wave in given coordinate range. <code>y</code> defines the lower boundary of painted wave.
   */
  public void paint(Graphics2D g, int xStart, int xEnd, int y) {
    Shape oldClip = g.getClip();
    final Rectangle rectangle = new Rectangle(xStart, y - 3, xEnd - xStart, 3);
    final Rectangle waveClip = oldClip != null ? oldClip.getBounds().intersection(rectangle) : rectangle;
    if (waveClip.isEmpty()) return;

    Composite oldComposite = g.getComposite();
    try {
      g.setComposite(AlphaComposite.SrcOver);
      g.setClip(waveClip);
      xStart -= xStart % 4;
      UIUtil.drawImage(g, myImage, xStart, y - 3, null);
    } finally {
      g.setComposite(oldComposite);
      g.setClip(oldClip);
    }
  }

  public static WavePainter forColor(Color color) {
    WavePainter painter = myPainters.get(color);
    if (painter == null) {
      painter = new WavePainter(color);
      // creating a new Color instance, as the one passed as parameter can be mutable (JBColor) and shouldn't be used as a map key
      //noinspection UseJBColor
      myPainters.put(new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()), painter);
    }
    return painter;
  }
}
