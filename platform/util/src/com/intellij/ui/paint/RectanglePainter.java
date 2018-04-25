/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.paint;

import com.intellij.ui.paint.LinePainter2D.StrokeType;
import com.intellij.util.ui.RegionPainter;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Sergey.Malenkov
 */
public enum RectanglePainter implements RegionPainter<Integer> {
  DRAW {
    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, @Nullable Integer round) {
      Object valueAA = round != null && round >= 0 ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_DEFAULT;
      RectanglePainter2D.DRAW.paint(g, x, y, width, height, round == null ? null : Double.valueOf(round), StrokeType.INSIDE, 1, valueAA);
    }
  },
  FILL {
    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, @Nullable Integer round) {
      Object valueAA = round != null && round >= 0 ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_DEFAULT;
      RectanglePainter2D.FILL.paint(g, x, y, width, height, round == null ? null : Double.valueOf(round), StrokeType.INSIDE, 1, valueAA);
    }
  };

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
