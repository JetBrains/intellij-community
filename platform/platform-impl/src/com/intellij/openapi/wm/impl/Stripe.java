// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.Comparator;

/**
 * @author Eugene Belyaev
 */
class Stripe extends AbstractDroppableStripe implements UISettingsListener {
  static final Key<Rectangle> VIRTUAL_BOUNDS = Key.create("Virtual stripe bounds");

  @MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT})
  private final int anchor;

  static final int DROP_DISTANCE_SENSITIVITY = 200;

  Stripe(@MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT}) int anchor) {
    super(new GridBagLayout());

    setOpaque(true);
    this.anchor = anchor;
    setBorder(new AdaptiveBorder());
  }

  public boolean isEmpty() {
    return getButtons().isEmpty();
  }

  @Override
  public void reset() {
    super.reset();
    getButtons().clear();
    removeAll();
    revalidate();
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    updatePresentation();
  }

  private static final class AdaptiveBorder implements Border {
    @Override
    public void paintBorder(@NotNull Component c, Graphics g, int x, int y, int width, int height) {
      Insets insets = ((JComponent)c).getInsets();
      g.setColor(UIUtil.CONTRAST_BORDER_COLOR);
      drawBorder((Graphics2D)g, x, y, width, height, insets);
    }

    private static void drawBorder(Graphics2D g, int x, int y, int width, int height, Insets insets) {
      if (insets.top == 1) {
        LinePainter2D.paint(g, x, y, x + width, y);
      }
      if (insets.right == 1) {
        LinePainter2D.paint(g, x + width - 1, y, x + width - 1, y + height);
      }
      if (insets.left == 1) {
        LinePainter2D.paint(g, x, y, x, y + height);
      }
      if (insets.bottom == 1) {
        LinePainter2D.paint(g, x, y + height - 1, x + width, y + height - 1);
      }

      if (!StartupUiUtil.isUnderDarcula()) {
        return;
      }

      Color c = g.getColor();
      if (insets.top == 2) {
        g.setColor(c);
        LinePainter2D.paint(g, x, y, x + width, y);
        g.setColor(Gray._85);
        LinePainter2D.paint(g, x, y + 1, x + width, y + 1);
      }
      if (insets.right == 2) {
        g.setColor(Gray._85);
        LinePainter2D.paint(g, x + width - 1, y, x + width - 1, y + height);
        g.setColor(c);
        LinePainter2D.paint(g, x + width - 2, y, x + width - 2, y + height);
      }
      if (insets.left == 2) {
        g.setColor(Gray._85);
        LinePainter2D.paint(g, x + 1, y, x + 1, y + height);
        g.setColor(c);
        LinePainter2D.paint(g, x, y, x, y + height);
      }
    }

    @SuppressWarnings("UseDPIAwareInsets")
    @Override
    public Insets getBorderInsets(@NotNull Component c) {
      Stripe stripe = (Stripe)c;
      ToolWindowAnchor anchor = stripe.getAnchor();
      if (anchor == ToolWindowAnchor.LEFT) {
        return new Insets(1, 0, 0, 1);
      }
      else if (anchor == ToolWindowAnchor.RIGHT) {
        return new Insets(1, 1, 0, 0);
      }
      else if (anchor == ToolWindowAnchor.TOP) {
        return new Insets(1, 0, 0, 0);
      }
      else {
        return new Insets(1, 0, 0, 0);
      }
    }

    @Override
    public boolean isBorderOpaque() {
      return true;
    }
  }

  void addButton(@NotNull StripeButton button, @NotNull Comparator<? super JComponent> comparator) {
    setMyPreferredSize(null);
    getButtons().add(button);
    getButtons().sort(comparator);
    add(button);
  }

  void removeButton(@NotNull StripeButton button) {
    setMyPreferredSize(null);
    getButtons().remove(button);
    remove(button);
    revalidate();
  }

  @Override
  public @NotNull ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.get(anchor);
  }

  public void startDrag() {
    revalidate();
    repaint();
  }

  public void stopDrag() {
    revalidate();
    repaint();
  }

  @Nullable
  @Override
  public JComponent getButtonFor(@NotNull String toolWindowId) {
    return ContainerUtil.find(getButtons(), c -> ((StripeButton)c).getId().equals(toolWindowId));
  }

  public void setOverlayed(boolean overlayed) {
    if (Registry.is("disable.toolwindow.overlay")) {
      return;
    }

    Color bg = UIUtil.getPanelBackground();
    if (overlayed) {
      setBackground(ColorUtil.toAlpha(bg, 190));
    }
    else {
      setBackground(bg);
    }
  }

  @Override
  public boolean isHorizontal() {
    return anchor == SwingConstants.TOP || anchor == SwingConstants.BOTTOM;
  }

  void updatePresentation() {
    getButtons().forEach(c -> ((StripeButton)c).updatePresentation());
  }

  @Override
  public boolean containsPoint(@NotNull Point screenPoint) {
    Point point = screenPoint.getLocation();
    SwingUtilities.convertPointFromScreen(point, isVisible() ? this : getParent());
    int width = getWidth();
    int height = getHeight();
    if (!isVisible()) {
      Rectangle bounds = ClientProperty.get(this, VIRTUAL_BOUNDS);
      if (bounds != null) {
        point.x -= bounds.x;
        point.y -= bounds.y;
        width = bounds.width;
        height = bounds.height;
      }
    }
    int areaSize = Math.min(Math.min(getParent().getWidth() / 2, getParent().getHeight() / 2), JBUI.scale(DROP_DISTANCE_SENSITIVITY));
    Point[] points = {new Point(0, 0), new Point(width, 0), new Point(width, height), new Point(0, height)};
    switch (anchor) {
      //Top area should be is empty due to IDEA-271100
      case SwingConstants.TOP: {
        updateLocation(points, 1, 2, 0, 0, areaSize);
        updateLocation(points, 0, 3, 0, 0, areaSize);
        break;
      }
      case SwingConstants.LEFT: {
        updateLocation(points, 0, 1, 1, 0, areaSize);
        updateLocation(points, 3, 2, 1, -1, areaSize);
        break;
      }
      case SwingConstants.BOTTOM: {
        updateLocation(points, 3, 0, 1, -1, areaSize);
        updateLocation(points, 2, 1, -1, -1, areaSize);
        break;
      }

      case SwingConstants.RIGHT: {
        updateLocation(points, 1, 0, -1, 0, areaSize);
        updateLocation(points, 2, 3, -1, 1, areaSize);
      }
    }
    return new Polygon(new int[]{points[0].x, points[1].x, points[2].x, points[3].x},
                       new int[]{points[0].y, points[1].y, points[2].y, points[3].y}, 4).contains(point);
  }

  private static void updateLocation(Point[] points, int indexBase, int indexDest, int xSign, int ySign, int areaSize) {
    points[indexDest].setLocation(points[indexBase].x + xSign * areaSize, points[indexBase].y + ySign * areaSize);
  }

  @Override
  public String toString() {
    @NonNls String anchor = null;
    switch (this.anchor) {
      case SwingConstants.TOP:
        anchor = "TOP";
        break;
      case SwingConstants.BOTTOM:
        anchor = "BOTTOM";
        break;
      case SwingConstants.LEFT:
        anchor = "LEFT";
        break;
      case SwingConstants.RIGHT:
        anchor = "RIGHT";
        break;
    }
    return getClass().getName() + " " + anchor;
  }

  @Override
  protected Graphics getComponentGraphics(Graphics g) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g));
  }

  @Override
  protected void paintComponent(@NotNull Graphics g) {
    super.paintComponent(g);
    if (!StartupUiUtil.isUnderDarcula()) {
      ToolWindowAnchor anchor = getAnchor();
      g.setColor(Gray._255.withAlpha(40));
      Rectangle r = getBounds();
      if (anchor == ToolWindowAnchor.LEFT || anchor == ToolWindowAnchor.RIGHT) {
        LinePainter2D.paint((Graphics2D)g, 0, 0, 0, r.height);
        LinePainter2D.paint((Graphics2D)g, r.width - 2, 0, r.width - 2, r.height);
      }
      else {
        LinePainter2D.paint((Graphics2D)g, 0, 1, r.width, 1);
        LinePainter2D.paint((Graphics2D)g, 0, r.height - 1, r.width, r.height - 1);
      }
    }
  }
}
