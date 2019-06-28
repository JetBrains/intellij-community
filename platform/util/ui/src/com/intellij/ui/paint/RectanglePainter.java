// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.paint;

import com.intellij.ui.paint.LinePainter2D.StrokeType;
import com.intellij.util.ui.RegionPainter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Sergey.Malenkov
 */
public enum RectanglePainter implements RegionPainter<Integer> {
  DRAW {
    @Override
    public void paint(@NotNull Graphics2D g, int x, int y, int width, int height, @Nullable Integer round) {
      paint2D(RectanglePainter2D.DRAW, g, x, y, width, height, round);
    }
  },
  FILL {
    @Override
    public void paint(@NotNull Graphics2D g, int x, int y, int width, int height, @Nullable Integer round) {
      paint2D(RectanglePainter2D.FILL, g, x, y, width, height, round);
    }
  };

  private static void paint2D(RectanglePainter2D p, Graphics2D g, int x, int y, int width, int height, @Nullable Integer round) {
    Double arc = round == null || round <= 0 ? null : Double.valueOf(round);
    Object valueAA = arc != null ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_DEFAULT;
    p.paint(g, x, y, width, height, arc, StrokeType.INSIDE, 1, valueAA);
  }

  public static void paint(Graphics2D g, int x, int y, int width, int height, int arc, @Nullable Paint fill, @Nullable Paint draw) {
    if (fill != null) {
      g.setPaint(fill);
      if (draw != null) {
        int sw = 1; // stroke width
        int dw = sw + sw;
        FILL.paint(g, x + sw, y + sw, width - dw, height - dw, arc > dw ? arc - dw : 0);
      }
      else {
        FILL.paint(g, x, y, width, height, arc);
      }
    }
    if (draw != null) {
      g.setPaint(draw);
      DRAW.paint(g, x, y, width, height, arc);
    }
  }
}
