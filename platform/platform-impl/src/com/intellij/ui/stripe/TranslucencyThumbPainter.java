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
package com.intellij.ui.stripe;

import com.intellij.openapi.Disposable;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.RegionPainter;
import com.intellij.util.ui.UIUtil;

import javax.swing.JScrollBar;
import java.awt.*;

/**
 * @author Sergey.Malenkov
 */
final class TranslucencyThumbPainter implements Disposable, RegionPainter<Integer> {
  private static final BasicStroke BASIC_STROKE = new BasicStroke();
  private final ErrorStripePainter myPainter;
  private final JScrollBar myScrollBar;

  TranslucencyThumbPainter(ErrorStripePainter painter, JScrollBar bar) {
    myPainter = painter;
    myScrollBar = bar;
    UIUtil.putClientProperty(myScrollBar, BUTTONLESS_SCROLL_BAR_UI_MAXI_THUMB, this);
  }

  @Override
  public void dispose() {
    UIUtil.putClientProperty(myScrollBar, BUTTONLESS_SCROLL_BAR_UI_MAXI_THUMB, null);
  }

  @Override
  public void paint(Graphics2D g, int x, int y, int width, int height, Integer object) {
    g = (Graphics2D)g.create(x, y, width, height);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .5f));
    g.setStroke(BASIC_STROKE);

    boolean dark = UIUtil.isUnderDarcula();
    int offset = object != null ? object + 11 : 11;
    Color start = getColor(dark ? 95 + offset : 251 - offset);
    Color stop = getColor(dark ? 80 + offset : 215 - offset);

    Paint paint;
    if (Adjustable.VERTICAL == myScrollBar.getOrientation()) {
      x = 2;
      y = 1;
      width -= 3;
      height -= 2;
      if (myPainter instanceof ExtraErrorStripePainter) {
        ExtraErrorStripePainter extra = (ExtraErrorStripePainter)myPainter;
        int gap = extra.getMinimalThickness() - 1;
        x += gap;
        width -= gap;
      }
      paint = UIUtil.getGradientPaint(x, 0, start, x + width, 0, stop);
    }
    else {
      x = 1;
      y = 2;
      width -= 2;
      height -= 3;
      paint = UIUtil.getGradientPaint(0, y, start, 0, y + height, stop);
    }
    int arc = JBUI.scale(3);
    g.setPaint(paint);
    g.fillRoundRect(x + 1, y + 1, width - 2, height - 2, arc, arc);
    g.setColor(getColor(dark ? 85 : 201));
    g.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
    g.dispose();
  }

  private static Color getColor(int gray) {
    return Gray.get(gray < 0 ? 0 : gray > 255 ? 255 : gray);
  }
}
