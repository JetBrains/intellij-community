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
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class LabelPainters {

  public static LabelPainter createPainter(boolean paintFlag) {
    boolean square = Registry.is("vcs.log.square.labels");
    if (paintFlag) {
      return square ? new UnderlinedLabelPainter() : new FlagLabelPainter();
    }
    else {
      return new LabelPainter(square);
    }
  }

  public static Font getFont() {
    return UIUtil.getLabelFont();
  }

  public static class ReferencePainter {
    @NotNull private final VcsLogColorManager myColorManager;
    @NotNull private final LabelPainter myLabelPainter;

    public ReferencePainter(@NotNull VcsLogColorManager colorManager, boolean paintRoot) {
      myColorManager = colorManager;
      myLabelPainter = createPainter(paintRoot && colorManager.isMultipleRoots());
    }

    public void paintReference(@NotNull VcsRef reference, @NotNull Graphics g, int paddingX, int paddingY) {
      myLabelPainter.paintLabel((Graphics2D)g, reference.getName(), paddingX, paddingY, reference.getType().getBackgroundColor(),
                                VcsLogColorManagerImpl.getIndicatorColor(myColorManager.getRootColor(reference.getRoot())));
    }

    public Rectangle paintLabel(@NotNull String text,
                                @NotNull Graphics g,
                                int paddingX,
                                int paddingY,
                                @NotNull Color color,
                                @NotNull Color flagColor) {
      return myLabelPainter.paintLabel((Graphics2D)g, text, paddingX, paddingY, color, flagColor);
    }

    public Dimension getSize(@NotNull VcsRef reference, @NotNull JComponent component) {
      return myLabelPainter.calculateSize(reference.getName(), component.getFontMetrics(getFont()));
    }

    public int getHeight(@NotNull JComponent component) {
      return myLabelPainter.calculateSize("", component.getFontMetrics(getFont())).height;
    }
  }

  public static class LabelPainter {
    protected static final int TEXT_PADDING_X = 5;
    protected static final int TOP_TEXT_PADDING = 2;
    protected static final int BOTTOM_TEXT_PADDING = 1;

    private static final int LABEL_ARC = 5;

    private final boolean mySquare;

    public LabelPainter(boolean square) {
      mySquare = square;
    }

    protected Rectangle paintLabel(@NotNull Graphics2D g2,
                                   @NotNull String text,
                                   int paddingX,
                                   int paddingY,
                                   int textPadding,
                                   @NotNull Color color) {
      g2.setFont(getFont());
      g2.setStroke(new BasicStroke(1.5f));
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      FontMetrics fontMetrics = g2.getFontMetrics();
      int width = fontMetrics.stringWidth(text) + 2 * TEXT_PADDING_X + textPadding;
      int height = fontMetrics.getHeight() + TOP_TEXT_PADDING + BOTTOM_TEXT_PADDING;

      g2.setColor(color);
      if (mySquare) {
        g2.fillRect(paddingX, paddingY, width, height);
      }
      else {
        g2.fill(new RoundRectangle2D.Double(paddingX, paddingY, width, height, LABEL_ARC, LABEL_ARC));
      }

      g2.setColor(JBColor.BLACK);
      int x = paddingX + textPadding + TEXT_PADDING_X;
      int y = paddingY + SimpleColoredComponent.getTextBaseLine(fontMetrics, height);
      g2.drawString(text, x, y);

      return new Rectangle(x, y, width, height);
    }

    public Rectangle paintLabel(@NotNull Graphics2D g2, @NotNull String text, int paddingX, int paddingY, @NotNull Color color) {
      return paintLabel(g2, text, paddingX, paddingY, 0, color);
    }

    public Rectangle paintLabel(@NotNull Graphics2D g2,
                                @NotNull String text,
                                int paddingX,
                                int paddingY,
                                @NotNull Color labelColor,
                                @NotNull Color flagColor) {
      return paintLabel(g2, text, paddingX, paddingY, 0, labelColor);
    }

    public Dimension calculateSize(@NotNull String text, @NotNull FontMetrics metrics) {
      int width = metrics.stringWidth(text) + 2 * TEXT_PADDING_X;
      int height = metrics.getHeight() + TOP_TEXT_PADDING + BOTTOM_TEXT_PADDING;
      return new Dimension(width, height);
    }

  }

  public static class FlagLabelPainter extends LabelPainter {
    private static final int FLAG_WIDTH = 8;
    private static final int FLAG_PADDING = 6;
    private static final int FLAG_TOP_INDENT = 2;

    public FlagLabelPainter() {
      super(false);
    }

    @Override
    public Rectangle paintLabel(@NotNull Graphics2D g2,
                                @NotNull String text,
                                int paddingX,
                                int paddingY,
                                @NotNull Color labelColor,
                                @NotNull Color flagColor) {

      Rectangle rectangle =
        super.paintLabel(g2, text, paddingX, paddingY + FLAG_TOP_INDENT, FLAG_WIDTH + 2 * FLAG_PADDING - TEXT_PADDING_X, labelColor);

      g2.setColor(flagColor);
      int x0 = paddingX + FLAG_PADDING;
      int xMid = x0 + FLAG_WIDTH / 2;
      int xRight = x0 + FLAG_WIDTH;

      int y0 = paddingY;
      int yMid = y0 + 2 * rectangle.height / 3 - 2;
      int yBottom = y0 + rectangle.height - 4;

      // something like a knight flag
      Polygon polygon = new Polygon(new int[]{x0, xRight, xRight, xMid, x0}, new int[]{y0, y0, yMid, yBottom, yMid}, 5);
      g2.fillPolygon(polygon);

      return new Rectangle(paddingX, paddingY, rectangle.width, rectangle.height + FLAG_TOP_INDENT);
    }

    @Override
    public Dimension calculateSize(@NotNull String text, @NotNull FontMetrics metrics) {
      Dimension size = super.calculateSize(text, metrics);
      return new Dimension(size.width + FLAG_WIDTH + 2 * FLAG_PADDING, size.height + FLAG_TOP_INDENT);
    }
  }

  public static class UnderlinedLabelPainter extends LabelPainter {

    private static final int LINE_GAP = 1;
    private static final int LINE_HEIGHT = 4;

    public UnderlinedLabelPainter() {
      super(true);
    }

    @Override
    public Rectangle paintLabel(@NotNull Graphics2D g2,
                                @NotNull String text,
                                int paddingX,
                                int paddingY,
                                @NotNull Color labelColor,
                                @NotNull Color flagColor) {

      Rectangle rectangle = super.paintLabel(g2, text, paddingX, paddingY, 0, labelColor);

      g2.setColor(flagColor);
      g2.fillRect(paddingX, paddingY + rectangle.height + LINE_GAP, rectangle.width, LINE_HEIGHT);

      return new Rectangle(paddingX, paddingY, rectangle.width, rectangle.height + LINE_GAP + LINE_HEIGHT);
    }

    @Override
    public Dimension calculateSize(@NotNull String text, @NotNull FontMetrics metrics) {
      Dimension size = super.calculateSize(text, metrics);
      return new Dimension(size.width, size.height + LINE_GAP + LINE_HEIGHT);
    }
  }
}
