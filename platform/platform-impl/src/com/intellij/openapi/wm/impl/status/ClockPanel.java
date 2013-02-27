/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.util.Calendar.*;

/**
 * User: Vassiliy.Kudryashov
 */
public class ClockPanel extends JComponent implements CustomStatusBarWidget {
  @NonNls public static final String WIDGET_ID = "Clock";
  private static final int[] MASK = new int[]{
    1 << 0 | 1 << 2 | 1 << 3 | 1 << 5 | 1 << 6 | 1 << 7 | 1 << 8 | 1 << 9,//top
    1 << 0 | 1 << 4 | 1 << 5 | 1 << 6 | 1 << 8 | 1 << 9,//top-left
    1 << 0 | 1 << 1 | 1 << 2 | 1 << 3 | 1 << 4 | 1 << 7 | 1 << 8 | 1 << 9,//top-right
    1 << 2 | 1 << 3 | 1 << 4 | 1 << 5 | 1 << 6 | 1 << 8 | 1 << 9,//middle
    1 << 0 | 1 << 2 | 1 << 6 | 1 << 8,//bottom-left
    1 << 0 | 1 << 1 | 1 << 3 | 1 << 4 | 1 << 5 | 1 << 6 | 1 << 7 | 1 << 8 | 1 << 9,//bottom-right
    1 << 0 | 1 << 2 | 1 << 3 | 1 << 5 | 1 << 6 | 1 << 8 | 1 << 9//bottom
  };//1005, 881, 927, 892, 325, 1019, 877 (geek mode)

  protected final Calendar myCalendar;
  private final Timer myTimer;
  private final boolean is24Hours;

  public ClockPanel() {
    myCalendar = getInstance();
    is24Hours = new SimpleDateFormat().toLocalizedPattern().contains("H");
    myTimer = new Timer(50, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        repaint();
      }
    });
  }

  @NotNull
  @Override
  public String ID() {
    return WIDGET_ID;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    myTimer.start();
  }

  @Override
  public void dispose() {
    myTimer.stop();
  }


  @Override
  public JComponent getComponent() {
    return this;
  }

  @Nullable
  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  @Override
  public Dimension getPreferredSize() {
    int height;
    Container parent = getParent();
    if (isVisible() && parent != null) {
      height = parent.getSize().height - parent.getInsets().top - parent.getInsets().bottom;
    }
    else {
      height = super.getPreferredSize().height;
    }
    return new Dimension((int)(height * 2.75), height);
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  @Override
  public void paint(Graphics graphics) {
    Graphics2D g = (Graphics2D)graphics.create();
    try {
      g.setRenderingHint(KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      int h = getHeight() - 4;
      int w = h / 2;
      float thickness = h * .1F;
      AffineTransform transform = g.getTransform();
      if (transform == null) {
        transform = new AffineTransform(1, 0, -thickness / h, 1, thickness * 3, thickness / 4);
      }
      else {
        transform.concatenate(new AffineTransform(1, 0, -thickness / h, 1, thickness * 3, thickness / 4));
      }
      g.setTransform(transform);
      g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.setColor(UIManager.getColor("Label.foreground"));
      myCalendar.setTimeInMillis(System.currentTimeMillis());
      int hours = myCalendar.get(is24Hours ? HOUR_OF_DAY : HOUR);
      int minutes = myCalendar.get(MINUTE);
      int x = 0;
      int y = 2;
      boolean eveningDot = !is24Hours && myCalendar.get(HOUR_OF_DAY) > 11;
      if (eveningDot) {
        g.setStroke(new BasicStroke(thickness * 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(x + thickness, y + thickness, x + thickness, y + thickness + thickness / 20));
      }
      g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      if (hours >= 10) paintDigit(g, x, y, w, h, thickness, hours / 10);
      x += w + thickness * 2;
      paintDigit(g, x, y, w, h, thickness, hours % 10);
      x += w + thickness * 2;
      if (myCalendar.get(MILLISECOND) <= 500) {
        g.draw(new Line2D.Float(x, y + h / 2 - thickness * 2, x, y + h / 2 - thickness * 2 + thickness / 20));
        g.draw(new Line2D.Float(x, y + h / 2 + thickness * 2, x, y + h / 2 + thickness * 2 + thickness / 20));
      }
      x += thickness * 2;
      paintDigit(g, x, y, w, h, thickness, minutes / 10);
      x += w + thickness * 2;
      paintDigit(g, x, y, w, h, thickness, minutes % 10);
    }
    finally {
      g.dispose();
    }
  }

  private static void paintDigit(Graphics2D g, int x, int y, int width, int height, float t, int digit) {
    digit = 1 << digit;
    int h2 = height / 2;
    float t54 = t * 5 / 4;
    float t34 = t * 3 / 4;
    float t2 = t / 2;
    if ((digit & MASK[0]) != 0) g.draw(new Line2D.Float(x + t54, y + t2, x + width - t54, y + t2));
    if ((digit & MASK[1]) != 0) g.draw(new Line2D.Float(x + t2, y + t54, x + t2, y + h2 - t34));
    if ((digit & MASK[2]) != 0) g.draw(new Line2D.Float(x + width - t2, y + t54, x + width - t2, y + h2 - t34));
    if ((digit & MASK[3]) != 0) g.draw(new Line2D.Float(x + t54, y + h2, x + width - t54, y + h2));
    if ((digit & MASK[4]) != 0) g.draw(new Line2D.Float(x + t2, y + h2 + t34, x + t2, y + height - t54));
    if ((digit & MASK[5]) != 0) g.draw(new Line2D.Float(x + width - t2, y + h2 + t34, x + width - t2, y + height - t54));
    if ((digit & MASK[6]) != 0) g.draw(new Line2D.Float(x + t54, y + height - t2, x + width - t54, y + height - t2));
  }
}
