// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.RectanglePainter;
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
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
class Stripe extends JPanel implements UISettingsListener {
  private static final Dimension EMPTY_SIZE = new Dimension();
  static final Key<Rectangle> VIRTUAL_BOUNDS = Key.create("Virtual stripe bounds");

  @MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT})
  private final int anchor;
  private final List<StripeButton> buttons = new ArrayList<>();

  private Dimension preferredSize;
  private StripeButton myDragButton;
  private Rectangle myDropRectangle;
  private final Rectangle myDrawRectangle = new Rectangle();
  private JComponent myDragButtonImage;
  private LayoutData myLastLayoutData;
  private boolean myFinishingDrop;
  static final int DROP_DISTANCE_SENSITIVITY = 200;

  Stripe(@MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT}) int anchor) {
    super(new GridBagLayout());

    setOpaque(true);
    this.anchor = anchor;
    setBorder(new AdaptiveBorder());
  }

  public boolean isEmpty() {
    return buttons.isEmpty();
  }

  public void reset() {
    buttons.clear();
    preferredSize = null;
    myLastLayoutData = null;
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

  void addButton(@NotNull StripeButton button, @NotNull Comparator<? super StripeButton> comparator) {
    preferredSize = null;
    buttons.add(button);
    buttons.sort(comparator);
    add(button);
  }

  void removeButton(@NotNull StripeButton button) {
    preferredSize = null;
    buttons.remove(button);
    remove(button);
    revalidate();
  }

  @Override
  public void invalidate() {
    preferredSize = null;
    super.invalidate();
  }

  @Override
  public void doLayout() {
    if (!myFinishingDrop) {
      myLastLayoutData = recomputeBounds(true, getSize(), /* noDrop = */ false);
    }
  }

  private LayoutData recomputeBounds(boolean setBounds, Dimension toFitWith, boolean noDrop) {
    LayoutData data = new LayoutData();
    int horizontalOffset = getHeight();

    data.eachY = 0;
    data.size = new Dimension();
    data.horizontal = isHorizontal();
    data.dragInsertPosition = -1;
    if (data.horizontal) {
      data.eachX = horizontalOffset - 1;
      data.eachY = 1;
    }
    else {
      data.eachX = 0;
    }
    if (myDragButton != null) {
      data.shouldSwapCoordinates = getAnchor().isHorizontal() != myDragButton.getAnchor().isHorizontal();
    }

    data.fitSize = toFitWith != null ? toFitWith : new Dimension();

    Point point = myDropRectangle != null ? myDropRectangle.getLocation() : new Point(-1, -1);
    SwingUtilities.convertPointToScreen(point, this);
    boolean processDrop = isDroppingButton() && containsPoint(point) && !noDrop;

    if (toFitWith == null) {
      for (StripeButton button : buttons) {
        if (!button.isVisible()) {
          continue;
        }

        Dimension eachSize = button.getPreferredSize();
        data.fitSize.width = Math.max(eachSize.width, data.fitSize.width);
        data.fitSize.height = Math.max(eachSize.height, data.fitSize.height);
      }
    }

    int gap = 0;
    if (toFitWith != null) {
      LayoutData layoutData = recomputeBounds(false, null, true);
      if (data.horizontal) {
        gap = toFitWith.width - horizontalOffset - layoutData.size.width - data.eachX;
      }
      else {
        gap = toFitWith.height - layoutData.size.height - data.eachY;
      }

      if (processDrop) {
        if (data.horizontal) {
          gap -= data.shouldSwapCoordinates ? myDropRectangle.height : myDropRectangle.width;
        }
        else {
          gap -= data.shouldSwapCoordinates ? myDropRectangle.width : myDropRectangle.height;
        }
      }
      gap = Math.max(gap, 0);
    }

    int insertOrder;
    boolean sidesStarted = false;

    for (StripeButton button : getButtonsToLayOut()) {
      insertOrder = button.getWindowInfo().getOrder();
      Dimension eachSize = button.getPreferredSize();

      if (!sidesStarted && button.getWindowInfo().isSplit()) {
        if (processDrop) {
          tryDroppingOnGap(data, gap, button.getWindowInfo().getOrder());
        }
        if (data.horizontal) {
          data.eachX += gap;
          data.size.width += gap;
        }
        else {
          data.eachY += gap;
          data.size.height += gap;
        }
        sidesStarted = true;
      }

      if (processDrop && !data.dragTargetChosen) {
        if (data.horizontal) {
          int distance = myDropRectangle.x - data.eachX;
          if (distance < eachSize.width / 2 ||
              (myDropRectangle.x + (data.shouldSwapCoordinates ? myDropRectangle.height : myDropRectangle.width)) < eachSize.width / 2) {
            data.dragInsertPosition = insertOrder;
            data.dragToSide = sidesStarted;
            layoutDragButton(data);
            data.dragTargetChosen = true;
          }
        }
        else {
          int distance = myDropRectangle.y - data.eachY;
          if (distance < eachSize.height / 2 ||
              (myDropRectangle.y + (data.shouldSwapCoordinates ? myDropRectangle.width : myDropRectangle.height)) < eachSize.height / 2) {
            data.dragInsertPosition = insertOrder;
            data.dragToSide = sidesStarted;
            layoutDragButton(data);
            data.dragTargetChosen = true;
          }
        }
      }

      layoutButton(data, button, setBounds);
    }

    if (!sidesStarted && processDrop) {
      tryDroppingOnGap(data, gap, -1);
    }

    if (isDroppingButton()) {
      Dimension dragSize = myDragButton.getPreferredSize();
      if (data.shouldSwapCoordinates) {
        swap(dragSize);
      }
      data.size.width = Math.max(data.size.width, dragSize.width);
      data.size.height = Math.max(data.size.height, dragSize.height);
    }

    if (processDrop && !data.dragTargetChosen) {
      data.dragInsertPosition = -1;
      data.dragToSide = true;
      layoutDragButton(data);
      data.dragTargetChosen = true;
    }

    return data;
  }

  private void tryDroppingOnGap(LayoutData data, int gap, int insertOrder) {
    if (data.dragTargetChosen) {
      return;
    }

    int nonSideDistance;
    int sideDistance;
    if (data.horizontal) {
      nonSideDistance = myDropRectangle.x - data.eachX;
      sideDistance = data.eachX + gap - myDropRectangle.x;
    }
    else {
      nonSideDistance = myDropRectangle.y - data.eachY;
      sideDistance = data.eachY + gap - myDropRectangle.y;
    }
    nonSideDistance = Math.max(0, nonSideDistance);

    if (sideDistance > 0) {
      if (nonSideDistance > sideDistance) {
        data.dragInsertPosition = insertOrder;
        data.dragToSide = true;
      }
      else {
        data.dragInsertPosition = -1;
        data.dragToSide = false;
      }
      data.dragTargetChosen = true;
      layoutDragButton(data, gap);
    }
  }

  private void layoutDragButton(LayoutData data) {
    layoutDragButton(data, 0);
  }

  private void layoutDragButton(LayoutData data, int gap) {
    myDrawRectangle.x = data.eachX;
    myDrawRectangle.y = data.eachY;
    layoutButton(data, myDragButtonImage, false);
    if (data.horizontal) {
      myDrawRectangle.width = data.eachX - myDrawRectangle.x;
      myDrawRectangle.height = data.fitSize.height;
      if (data.dragToSide) {
        if (data.dragInsertPosition == -1) {
          myDrawRectangle.x = getWidth() - getHeight() - myDrawRectangle.width;
        } else {
          myDrawRectangle.x += gap;
        }
      }
    } else {
      myDrawRectangle.width = data.fitSize.width;
      myDrawRectangle.height = data.eachY - myDrawRectangle.y;
      if (data.dragToSide) {
        if (data.dragInsertPosition == -1) {
          myDrawRectangle.y = getHeight() - myDrawRectangle.height;
        } else {
          myDrawRectangle.y += gap;
        }
      }
    }
  }

  private @NotNull List<StripeButton> getButtonsToLayOut() {
    List<StripeButton> result = new ArrayList<>();

    List<StripeButton> tools = new ArrayList<>();
    List<StripeButton> sideTools = new ArrayList<>();

    for (StripeButton button : buttons) {
      if (!button.isVisible()) {
        continue;
      }

      if (button.getWindowInfo().isSplit()) {
        sideTools.add(button);
      }
      else {
        tools.add(button);
      }
    }

    result.addAll(tools);
    result.addAll(sideTools);

    return result;
  }

  public @NotNull ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.get(anchor);
  }

  private static void layoutButton(@NotNull LayoutData data, @NotNull JComponent button, boolean setBounds) {
    Dimension eachSize = button.getPreferredSize();
    if (data.shouldSwapCoordinates && !(button instanceof StripeButton)) {
      swap(eachSize);
    }
    if (setBounds) {
      final int width = data.horizontal ? eachSize.width : data.fitSize.width;
      final int height = data.horizontal ? data.fitSize.height : eachSize.height;
      button.setBounds(data.eachX, data.eachY, width, height);
    }
    if (data.horizontal) {
      final int deltaX = eachSize.width;
      data.eachX += deltaX;
      data.size.width += deltaX;
      data.size.height = Math.max(data.size.height, eachSize.height);
    }
    else {
      final int deltaY = eachSize.height;
      data.eachY += deltaY;
      data.size.width = Math.max(data.size.width, eachSize.width);
      data.size.height += deltaY;
    }
  }

  private static void swap(Dimension d) {
    int tmp = d.width;
    //noinspection SuspiciousNameCombination
    d.width = d.height;
    d.height = tmp;
  }

  public void startDrag() {
    revalidate();
    repaint();
  }

  public void stopDrag() {
    revalidate();
    repaint();
  }

  public @Nullable StripeButton getButtonFor(@NotNull String toolWindowId) {
    for (StripeButton button : buttons) {
      if (button.getId().equals(toolWindowId)) {
        return button;
      }
    }
    return null;
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

  private static final class LayoutData {
    int eachX;
    int eachY;
    Dimension size;
    Dimension fitSize;
    boolean horizontal;

    boolean dragTargetChosen;
    boolean dragToSide;
    boolean shouldSwapCoordinates;
    int dragInsertPosition;
  }

  private boolean isHorizontal() {
    return anchor == SwingConstants.TOP || anchor == SwingConstants.BOTTOM;
  }

  @Override
  public Dimension getPreferredSize() {
    if (preferredSize == null) {
      if (buttons.isEmpty()) {
        preferredSize = EMPTY_SIZE;
      }
      else {
        preferredSize = recomputeBounds(false, null, false).size;
      }
    }
    return preferredSize;
  }

  void updatePresentation() {
    for (StripeButton button : buttons) {
      button.updatePresentation();
    }
  }

  boolean containsPoint(@NotNull Point screenPoint) {
    Point point = screenPoint.getLocation();
    SwingUtilities.convertPointFromScreen(point, isVisible() ? this : getParent());
    int width = getWidth();
    int height = getHeight();
    if (!isVisible()) {
      Rectangle bounds = UIUtil.getClientProperty(this, VIRTUAL_BOUNDS);
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

  public void finishDrop(@NotNull ToolWindowManagerImpl manager) {
    if (myLastLayoutData == null || !isDroppingButton()) {
      return;
    }

    String id = myDragButton.toolWindow.getId();
    myFinishingDrop = true;
    manager.setSideToolAndAnchor(id, ToolWindowAnchor.get(anchor), myLastLayoutData.dragInsertPosition, myLastLayoutData.dragToSide);
    manager.invokeLater(this::resetDrop);
  }

  public void resetDrop() {
    myDragButton = null;
    myDragButtonImage = null;
    myFinishingDrop = false;
    preferredSize = null;
    revalidate();
    repaint();
  }

  void processDropButton(@NotNull StripeButton button, @NotNull JComponent buttonImage, Point screenPoint) {
    if (!isDroppingButton()) {
      final BufferedImage image = button.createDragImage();
      buttonImage.paint(image.getGraphics());
      myDragButton = button;
      myDragButtonImage = buttonImage;
      preferredSize = null;
    }

    Point point = new Point(screenPoint);
    SwingUtilities.convertPointFromScreen(point, this);

    myDropRectangle = new Rectangle(point, buttonImage.getSize());

    revalidate();
    repaint();
  }

  @Nullable
  Boolean getDropToSide() {
    if (myLastLayoutData == null || !myLastLayoutData.dragTargetChosen) return null;
    return myLastLayoutData.dragToSide;
  }

  private boolean isDroppingButton() {
    return myDragButton != null;
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

    if (!myFinishingDrop && isDroppingButton()) {
      g.setColor(getBackground().brighter());
      g.fillRect(0, 0, getWidth(), getHeight());
      if (myDrawRectangle != null) {
        g.setColor(JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND);
        RectanglePainter.FILL.paint((Graphics2D)g, myDrawRectangle.x, myDrawRectangle.y, myDrawRectangle.width, myDrawRectangle.height,
                                    null);
      }
    }

    if (StartupUiUtil.isUnderDarcula()) {
      return;
    }

    ToolWindowAnchor anchor = getAnchor();
    g.setColor(new Color(255, 255, 255, 40));
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
