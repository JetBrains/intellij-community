// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.util.Computable;
import com.intellij.ui.paint.LinePainter2D;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Encapsulates the logic of drawing arrows at graphics buffer (primary usage is to draw tabulation symbols representation arrows).
 */
public final class ArrowPainter {
  private final ColorProvider myColorHolder;
  private final Computable<Integer> myWidthProvider;
  private final Computable<Integer> myHeightProvider;

  /**
   * Creates an ArrowPainter with specified parameters.
   *
   * @param colorHolder defines arrow color
   * @param widthProvider defines character width, it is used to calculate an inset for the arrow's tip
   * @param heightProvider defines character height, it's used to calculate an arrow's width and height
   */
  public ArrowPainter(@NotNull ColorProvider colorHolder, @NotNull Computable<Integer> widthProvider, @NotNull Computable<Integer> heightProvider) {
    myColorHolder = colorHolder;
    myWidthProvider = widthProvider;
    myHeightProvider = heightProvider;
  }

  /**
   * Paints arrow at the given graphics buffer using given coordinate parameters.
   *
   * @param g       target graphics buffer to use
   * @param y       defines baseline of the row where the arrow should be painted
   * @param start   starting {@code 'x'} position to use during drawing
   * @param stop    ending {@code 'x'} position to use during drawing
   */
  public void paint(Graphics g, int y, int start, int stop) {
    stop -= myWidthProvider.compute() / 4;
    Color oldColor = g.getColor();
    g.setColor(myColorHolder.getColor());
    final int height = myHeightProvider.compute();
    final int halfHeight = height / 2;
    int mid = y - halfHeight;
    int top = y - height;
    LinePainter2D.paint((Graphics2D)g, start, mid, stop, mid);
    LinePainter2D.paint((Graphics2D)g, stop, y, stop, top);
    g.fillPolygon(new int[]{stop - halfHeight, stop - halfHeight, stop}, new int[]{y, y - height, y - halfHeight}, 3);
    g.setColor(oldColor);
  }
}
