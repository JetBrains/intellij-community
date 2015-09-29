/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.progress.util;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.NotNullProducer;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;

/**
 * @author Eugene Belyaev
 */
public class ColorProgressBar extends JComponent {
  private static final Dimension PREFERRED_SIZE = new Dimension(146, 17);

  public static final Color GREEN = new JBColor(new NotNullProducer<Color>() {
    @NotNull
    @Override
    public Color produce() {
      UISettings settings = UISettings.getInstance();
      return settings == null || null == settings.COLOR_BLINDNESS
             ? new JBColor(new Color(0x6cad74), new Color(0x4a8c53))
             : new JBColor(new Color(0x6ca69c), new Color(0x639990));
    }
  });
  public static final Color RED = new JBColor(new NotNullProducer<Color>() {
    @NotNull
    @Override
    public Color produce() {
      UISettings settings = UISettings.getInstance();
      return settings == null || null == settings.COLOR_BLINDNESS
             ? new JBColor(new Color(0xd67b76), new Color(0xe55757))
             : new JBColor(new Color(0xcc7447), new Color(0xcc7447));
    }
  });
  public static final Color RED_TEXT = new JBColor(new Color(0xb81708), new Color(0xdb5c5c));
  public static final Color BLUE = new JBColor(new Color(1, 68, 208), JBColor.blue);
  public static final Color YELLOW = new JBColor(new Color(0xa67a21), new Color(0x91703a));
  private static final Color SHADOW1 = new JBColor(Gray._190, UIUtil.getBorderColor()) ;
  private static final Color SHADOW2 = Gray._105;

  private static final int BRICK_WIDTH = 6;
  private static final int BRICK_SPACE = 1;

  private static final int INDETERMINATE_BRICKS_DRAW = 5;
  private static final double INDETERMINATE_INC_OFFSET = 0.02;

  private double myFraction = 0.0;
  private Color myColor = BLUE;

  private double myIndeterminateInc = INDETERMINATE_INC_OFFSET;
  private boolean myIndeterminate = false;

  public boolean isIndeterminate() {
    return myIndeterminate;
  }

  public void setIndeterminate(boolean indeterminate) {
    this.myIndeterminate = indeterminate;
  }

  public ColorProgressBar() {
    updateUI();
  }

  public void setColor(Color color) {
    myColor = color;
    if (isDisplayable()) repaint();
  }

  public double getFraction() {
    return myFraction;
  }

  public void setFraction(double fraction) {
    if (Double.isNaN(fraction)) {
      fraction = 1.0;
    }

    if (myIndeterminate) {
      if (myFraction >= 1.0) {
        myIndeterminateInc = -INDETERMINATE_INC_OFFSET;
      }
      else if (myFraction <= 0) {
        myIndeterminateInc = INDETERMINATE_INC_OFFSET;
      }

      final boolean changed = myFraction == 0 || getBricksToDraw(myFraction) != getBricksToDraw(myFraction + myIndeterminateInc);
      myFraction += myIndeterminateInc;

      if (changed) {
        repaint();
      }
    }
    else {
      boolean changed = myFraction == 0 || getBricksToDraw(myFraction) != getBricksToDraw(fraction);
      myFraction = fraction;
      if (changed) {
        repaint();
      }
    }
  }

  private int getBricksToDraw(double fraction) {
    int bricksTotal = (getWidth() - 8) / (BRICK_WIDTH + BRICK_SPACE);
    return (int)(bricksTotal * fraction) + 1;
  }

  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    Graphics2D g2 = (Graphics2D)g;
    if (myFraction > 1) {
      myFraction = 1;
    }

    Dimension size = getSize();

    g2.setPaint(UIUtil.getListBackground());
    Rectangle2D rect = new Rectangle2D.Double(2, 2, size.width - 4, size.height - 4);
    g2.fill(rect);

    g2.setPaint(new JBColor(SHADOW1, UIUtil.getBorderColor()));
    rect.setRect(1, 1, size.width - 2, size.height - 2);
    g2.drawRoundRect(1, 1, size.width - 2, size.height - 2, 5, 5);
    g2.setPaint(SHADOW2);
    g2.drawRoundRect(0, 0, size.width - 2, size.height - 2, 5, 5);

    int y_center = size.height / 2;
    int y_steps = size.height / 2 - 3;
    int alpha_step = y_steps > 0 ? (255 - 70) / y_steps : 255 - 70;
    int x_offset = 4;

    g.setClip(4, 3, size.width - 8, size.height - 6);

    int bricksToDraw = myFraction == 0 ? 0 : getBricksToDraw(myFraction);

    if (myIndeterminate) {

      int startFrom = bricksToDraw < INDETERMINATE_BRICKS_DRAW ? 0 : bricksToDraw - INDETERMINATE_BRICKS_DRAW;
      int endTo = bricksToDraw + INDETERMINATE_BRICKS_DRAW < getBricksToDraw(1) ? bricksToDraw + INDETERMINATE_BRICKS_DRAW  : getBricksToDraw(1);

      for (int i = startFrom; i <= endTo; i++) {
        g2.setPaint(myColor);

        int startXOffset = x_offset + (BRICK_WIDTH + BRICK_SPACE) * i;
        UIUtil.drawLine(g2, startXOffset, y_center, startXOffset + BRICK_WIDTH - 1, y_center);

        for (int j = 0; j < y_steps; j++) {
          Color color = ColorUtil.toAlpha(myColor, 255 - alpha_step * (j + 1));
          g2.setPaint(color);
          UIUtil.drawLine(g2, startXOffset, y_center - 1 - j, startXOffset + BRICK_WIDTH - 1, y_center - 1 - j);

          if (!(y_center % 2 != 0 && j == y_steps - 1)) {
            UIUtil.drawLine(g2, startXOffset, y_center + 1 + j, startXOffset + BRICK_WIDTH - 1, y_center + 1 + j);
          }
        }
        g2.setColor(
          ColorUtil.toAlpha(myColor, 255 - alpha_step * (y_steps / 2 + 1)));
        g2.drawRect(startXOffset, y_center - y_steps, BRICK_WIDTH - 1, size.height - 7);
      }

    } else {
      for (int i = 0; i < bricksToDraw; i++) {
        g2.setPaint(myColor);
        UIUtil.drawLine(g2, x_offset, y_center, x_offset + BRICK_WIDTH - 1, y_center);
        for (int j = 0; j < y_steps; j++) {
          Color color = ColorUtil.toAlpha(myColor, 255 - alpha_step * (j + 1));
          g2.setPaint(color);
          UIUtil.drawLine(g2, x_offset, y_center - 1 - j, x_offset + BRICK_WIDTH - 1, y_center - 1 - j);
          if (!(y_center % 2 != 0 && j == y_steps - 1)) {
            UIUtil.drawLine(g2, x_offset, y_center + 1 + j, x_offset + BRICK_WIDTH - 1, y_center + 1 + j);
          }
        }
        g2.setColor(
          ColorUtil.toAlpha(myColor, 255 - alpha_step * (y_steps / 2 + 1)));
        g2.drawRect(x_offset, y_center - y_steps, BRICK_WIDTH - 1, size.height - 7);
        x_offset += BRICK_WIDTH + BRICK_SPACE;
      }
    }

    config.restore();
  }

  public Dimension getPreferredSize() {
    return PREFERRED_SIZE;
  }

  public Dimension getMaximumSize() {
    Dimension dimension = getPreferredSize();
    dimension.width = Short.MAX_VALUE;
    return dimension;
  }

  public Dimension getMinimumSize() {
    Dimension dimension = getPreferredSize();
    dimension.width = 13;
    return dimension;
  }

  public Color getColor() {
    return myColor;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void main(String[] args) {
    JFrame frame = new JFrame("ColorProgressBar Test");
    frame.addWindowListener(
      new WindowAdapter() {
        public void windowClosing(WindowEvent e) {
          System.exit(0);
        }
      }
    );
    frame.setSize(800, 600);
    frame.setLocation(0, 0);
    Container contentPane = frame.getContentPane();
    contentPane.setLayout(new BorderLayout());
    final ColorProgressBar colorProgressBar = new ColorProgressBar();
    colorProgressBar.setFraction(0.5);
    colorProgressBar.setIndeterminate(true);
    contentPane.add(colorProgressBar, BorderLayout.NORTH);
    frame.setVisible(true);
    JButton b = new JButton ("X");
    b.addActionListener(new ActionListener () {
      public void actionPerformed(ActionEvent e) {
         colorProgressBar.setFraction(1);
      }
    });
    contentPane.add(b, BorderLayout.SOUTH);
  }
}
