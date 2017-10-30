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

import com.intellij.util.ui.RegionPainter;

import java.awt.Paint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Sergey.Malenkov
 */
public enum RectanglePainter implements RegionPainter<Integer> {
  DRAW {
    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Integer round) {
      int sw = 1; // stroke width
      int dw = sw + sw;
      if (width > dw && height > dw) {
        int arc = round == null ? 0 : round;
        if (arc > 0) {
          Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
          path.append(new RoundRectangle2D.Double(x, y, width, height, arc, arc), false);
          path.append(new RoundRectangle2D.Double(x + sw, y + sw, width - dw, height - dw, arc - dw, arc - dw), false);
          Object old = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
          g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g.fill(path);
          g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old);
        }
        else {
          Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
          path.append(new Rectangle2D.Double(x, y, width, height), false);
          path.append(new Rectangle2D.Double(x + sw, y + sw, width - dw, height - dw), false);
          g.fill(path);
        }
      }
      else {
        FILL.paint(g, x, y, width, height, round);
      }
    }
  },
  FILL {
    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Integer round) {
      if (width > 0 && height > 0) {
        int arc = round == null ? 0 : round;
        if (arc > 0) {
          Object old = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
          g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g.fillRoundRect(x, y, width, height, arc, arc);
          g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old);
        }
        else {
          g.fillRect(x, y, width, height);
        }
      }
    }
  };

  public static void paint(Graphics2D g, int x, int y, int width, int height, int arc, Paint fill, Paint draw) {
    if (fill != null) {
      g.setPaint(fill);
      if (draw != null) {
        int sw = 1; // stroke width
        int dw = sw + sw;
        FILL.paint(g, x + sw, y + sw, width - dw, height - dw, arc - dw);
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
