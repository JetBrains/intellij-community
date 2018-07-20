// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws a 'wavy' line of 1-pixel amplitude. Instances are cached for performance reasons.
 * <p/>
 * This class is not thread-safe, it's supposed to be used in EDT only.
 */
public class WavePainter2D extends WavePainter {
  private static final float STROKE_WIDTH = 0.7f;

  private static final Map<Color, WavePainter2D> myPainters = new HashMap<Color, WavePainter2D>();
  private static final int PATTERN_WIDTH = 4000;

  private final BufferedImage myImage;

  private WavePainter2D(Color color) {
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
   * Paints a wave in given coordinate range. {@code y} defines the lower boundary of painted wave.
   */
  @Override
  public void paint(Graphics2D g, int xStart, int xEnd, int y) {
    paint(g, (double)xStart, (double)xEnd, (double)y);
  }

  /**
   * Paints a wave in given coordinate range. {@code y} defines the lower boundary of painted wave.
   */
  public void paint(Graphics2D g, double xStart, double xEnd, double y) {
    Shape clip = g.getClip();
    final Rectangle2D rectangle = new Rectangle2D.Double(xStart, y - 3, xEnd - xStart, 3);
    final Rectangle2D waveClip = clip != null ? clip.getBounds2D().createIntersection(rectangle) : rectangle;
    if (waveClip.isEmpty()) return;

    Graphics2D g2d = (Graphics2D)g.create();
    try {
      g2d.setComposite(AlphaComposite.SrcOver);
      g2d.setClip(waveClip);
      xStart -= xStart % 4;
      g2d.translate(xStart, y - 3);
      UIUtil.drawImage(g2d, myImage, 0, 0, null);
    } finally {
      g2d.dispose();
    }
  }

  public static WavePainter2D forColor(Color color) {
    WavePainter2D painter = myPainters.get(color);
    if (painter == null) {
      painter = new WavePainter2D(color);
      // creating a new Color instance, as the one passed as parameter can be mutable (JBColor) and shouldn't be used as a map key
      //noinspection UseJBColor
      myPainters.put(new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()), painter);
    }
    return painter;
  }
}
