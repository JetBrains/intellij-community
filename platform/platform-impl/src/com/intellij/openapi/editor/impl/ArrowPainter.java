/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.util.Computable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Encapsulates logic of drawing arrows at graphics buffer (primary usage is to draw tabulation symbols representation arrows).
 *
 * @author Denis Zhdanov
 * @since Jul 2, 2010 11:35:23 AM
 */
public class ArrowPainter {

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
    UIUtil.drawLine(g, start, mid, stop, mid);
    UIUtil.drawLine(g, stop, y, stop, top);
    g.fillPolygon(new int[]{stop - halfHeight, stop - halfHeight, stop}, new int[]{y, y - height, y - halfHeight}, 3);
    g.setColor(oldColor);
  }
}
