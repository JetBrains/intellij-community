// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.ui.JBSwingUtilities;
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

  private final int anchor;
  private final List<StripeButton> buttons = new ArrayList<>();

  private Dimension preferredSize;
  private StripeButton myDragButton;
  private Rectangle myDropRectangle;
  private JComponent myDragButtonImage;
  private LayoutData myLastLayoutData;
  private boolean myFinishingDrop;
  static final int DROP_DISTANCE_SENSITIVITY = 20;

  Stripe(@MagicConstant(valuesFromClass = SwingConstants.class) int anchor) {
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
    data.gap = 0;
    data.horizontal = isHorizontal();
    data.dragInsertPosition = -1;
    if (data.horizontal) {
      data.eachX = horizontalOffset - 1;
      data.eachY = 1;
    }
    else {
      data.eachX = 0;
    }

    data.fitSize = toFitWith != null ? toFitWith : new Dimension();

    Rectangle stripeSensitiveRectangle = new Rectangle(-DROP_DISTANCE_SENSITIVITY, -DROP_DISTANCE_SENSITIVITY,
                                                       getWidth() + DROP_DISTANCE_SENSITIVITY * 2, getHeight() + DROP_DISTANCE_SENSITIVITY * 2);
    boolean processDrop = isDroppingButton() && stripeSensitiveRectangle.intersects(myDropRectangle) && !noDrop;

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
          gap -= myDropRectangle.width + data.gap;
        }
        else {
          gap -= myDropRectangle.height + data.gap;
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
          if (distance < eachSize.width / 2 || (myDropRectangle.x + myDropRectangle.width) < eachSize.width / 2) {
            layoutButton(data, myDragButtonImage, false);
            data.dragInsertPosition = insertOrder;
            data.dragToSide = sidesStarted;
            data.dragTargetChosen = true;
          }
        }
        else {
          int distance = myDropRectangle.y - data.eachY;
          if (distance < eachSize.height / 2 || (myDropRectangle.y + myDropRectangle.height) < eachSize.height / 2) {
            layoutButton(data, myDragButtonImage, false);
            data.dragInsertPosition = insertOrder;
            data.dragToSide = sidesStarted;
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
      if (getAnchor().isHorizontal() == myDragButton.getWindowInfo().getAnchor().isHorizontal()) {
        data.size.width = Math.max(data.size.width, dragSize.width);
        data.size.height = Math.max(data.size.height, dragSize.height);
      }
      else {
        data.size.width = Math.max(data.size.width, dragSize.height);
        data.size.height = Math.max(data.size.height, dragSize.width);
      }
    }

    if (processDrop && !data.dragTargetChosen) {
      data.dragInsertPosition = -1;
      data.dragToSide = true;
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

      layoutButton(data, myDragButtonImage, false);
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
    if (setBounds) {
      final int width = data.horizontal ? eachSize.width : data.fitSize.width;
      final int height = data.horizontal ? data.fitSize.height : eachSize.height;
      button.setBounds(data.eachX, data.eachY, width, height);
    }
    if (data.horizontal) {
      final int deltaX = eachSize.width + data.gap;
      data.eachX += deltaX;
      data.size.width += deltaX;
      data.size.height = eachSize.height;
    }
    else {
      final int deltaY = eachSize.height + data.gap;
      data.eachY += deltaY;
      data.size.width = eachSize.width;
      data.size.height += deltaY;
    }
    data.processedComponents++;
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
    int gap;
    Dimension size;
    Dimension fitSize;
    boolean horizontal;
    int processedComponents;

    boolean dragTargetChosen;
    boolean dragToSide;
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

  public boolean containsScreen(@NotNull Rectangle screenRec) {
    Point point = screenRec.getLocation();
    SwingUtilities.convertPointFromScreen(point, this);
    return new Rectangle(point, screenRec.getSize()).intersects(
      new Rectangle(-DROP_DISTANCE_SENSITIVITY,
                    -DROP_DISTANCE_SENSITIVITY,
                    getWidth() + DROP_DISTANCE_SENSITIVITY,
                    getHeight() + DROP_DISTANCE_SENSITIVITY)
    );
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
      final BufferedImage image = UIUtil.createImage(button, button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_RGB);
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

    if (!myFinishingDrop && isDroppingButton() && myDragButton.getParent() != this) {
      g.setColor(getBackground().brighter());
      g.fillRect(0, 0, getWidth(), getHeight());
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
