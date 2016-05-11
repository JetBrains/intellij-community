/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * @author Alexey Pegov
 * @author Konstantin Bulenkov
 */
class SlideComponent extends JComponent {
  private static final int OFFSET = 11;
  private int myPointerValue = 0;
  private int myValue = 0;
  private final boolean myVertical;
  private final String myTitle;

  private final List<Consumer<Integer>> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private LightweightHint myTooltipHint;
  private final JLabel myLabel = new JLabel();
  private Unit myUnit = Unit.LEVEL;

  enum Unit {
    PERCENT,
    LEVEL;

    private static final float PERCENT_MAX_VALUE = 100f;
    private static final float LEVEL_MAX_VALUE = 255f;

    private static float getMaxValue(Unit unit) {
      return LEVEL.equals(unit) ? LEVEL_MAX_VALUE : PERCENT_MAX_VALUE;
    }

    private static String formatValue(int value, Unit unit) {
      return String.format("%d%s", (int) (getMaxValue(unit) / LEVEL_MAX_VALUE * value),
          unit.equals(PERCENT) ? "%" : "");
    }
  }

  void setUnits(Unit unit) {
    myUnit = unit;
  }

  SlideComponent(String title, boolean vertical) {
    myTitle = title;
    myVertical = vertical;

    addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseDragged(MouseEvent e) {
        processMouse(e);
      }
    });

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        processMouse(e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        updateBalloonText();
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        updateBalloonText();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        if (myTooltipHint != null) {
          myTooltipHint.hide();
          myTooltipHint = null;
        }
      }
    });

    addMouseWheelListener(new MouseWheelListener() {
      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        final int amount = e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL ? e.getUnitsToScroll() * e.getScrollAmount() :
                           e.getWheelRotation() < 0 ? -e.getScrollAmount() : e.getScrollAmount();
        int pointerValue = myPointerValue + amount;
        pointerValue = pointerValue < OFFSET ? OFFSET : pointerValue;
        int size = myVertical ? getHeight() : getWidth();
        pointerValue = pointerValue > (size - 12) ? size - 12 : pointerValue;

        myPointerValue = pointerValue;
        myValue = pointerValueToValue(myPointerValue);

        repaint();
        fireValueChanged();
      }
    });

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        setValue(getValue());
        fireValueChanged();
        repaint();
      }
    });
  }

  private void updateBalloonText() {
    final Point point = myVertical ? new Point(0, myPointerValue) : new Point(myPointerValue, 0);
    myLabel.setText(myTitle + ": " + Unit.formatValue(myValue, myUnit));
    if (myTooltipHint == null) {
      myTooltipHint = new LightweightHint(myLabel);
      myTooltipHint.setCancelOnClickOutside(false);
      myTooltipHint.setCancelOnOtherWindowOpen(false);

      final HintHint hint = new HintHint(this, point)
        .setPreferredPosition(myVertical ? Balloon.Position.atLeft : Balloon.Position.above)
        .setBorderColor(Color.BLACK)
        .setAwtTooltip(true)
        .setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD))
        .setTextBg(HintUtil.INFORMATION_COLOR)
        .setShowImmediately(true);

      final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      myTooltipHint.show(this, point.x, point.y, owner instanceof JComponent ? (JComponent)owner : null, hint);
    }
    else {
      myTooltipHint.setLocation(new RelativePoint(this, point));
    }
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    super.processMouseMotionEvent(e);
    updateBalloonText();
  }

  private void processMouse(MouseEvent e) {
    int pointerValue = myVertical ? e.getY() : e.getX();
    pointerValue = pointerValue < OFFSET ? OFFSET : pointerValue;
    int size = myVertical ? getHeight() : getWidth();
    pointerValue = pointerValue > (size - 12) ? size - 12 : pointerValue;

    myPointerValue = pointerValue;

    myValue = pointerValueToValue(myPointerValue);

    repaint();
    fireValueChanged();
  }

  public void addListener(Consumer<Integer> listener) {
    myListeners.add(listener);
  }

  private void fireValueChanged() {
    for (Consumer<Integer> listener : myListeners) {
      listener.consume(myValue);
    }
  }

  // 0 - 255
  public void setValue(int value) {
    myPointerValue = valueToPointerValue(value);
    myValue = value;
  }

  public int getValue() {
    return myValue;
  }

  private int pointerValueToValue(int pointerValue) {
    pointerValue -= OFFSET;
    final int size = myVertical ? getHeight() : getWidth();
    float proportion = (size - 23) / 255f;
    return Math.round((pointerValue / proportion));
  }

  private int valueToPointerValue(int value) {
    final int size = myVertical ? getHeight() : getWidth();
    float proportion = (size - 23) / 255f;
    return OFFSET + (int)(value * proportion);
  }

  @Override
  public Dimension getPreferredSize() {
    return myVertical ? new Dimension(22, 100) : new Dimension(100, 22);
  }

  @Override
  public Dimension getMinimumSize() {
    return myVertical ? new Dimension(22, 50) : new Dimension(50, 22);
  }

  @Override
  public final void setToolTipText(String text) {
    //disable tooltips
  }

  @Override
  protected void paintComponent(Graphics g) {
    final Graphics2D g2d = (Graphics2D)g;

    if (myVertical) {
      g2d.setPaint(UIUtil.getGradientPaint(0f, 0f, Color.WHITE, 0f, getHeight(), Color.BLACK));
      g.fillRect(7, 10, 12, getHeight() - 20);

      g.setColor(Gray._150);
      g.drawRect(7, 10, 12, getHeight() - 20);

      g.setColor(Gray._250);
      g.drawRect(8, 11, 10, getHeight() - 22);
    }
    else {
      g2d.setPaint(UIUtil.getGradientPaint(0f, 0f, Color.WHITE, getWidth(), 0f, Color.BLACK));
      g.fillRect(10, 7, getWidth() - 20, 12);

      g.setColor(Gray._150);
      g.drawRect(10, 7, getWidth() - 20, 12);

      g.setColor(Gray._250);
      g.drawRect(11, 8, getWidth() - 22, 10);
    }

    drawKnob(g2d, myVertical ? 7 : myPointerValue, myVertical ? myPointerValue : 7, myVertical);
  }

  private static void drawKnob(Graphics2D g2d, int x, int y, boolean vertical) {
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    if (vertical) {
      y -= 6;

      Polygon arrowShadow = new Polygon();
      arrowShadow.addPoint(x - 5, y + 1);
      arrowShadow.addPoint(x + 7, y + 7);
      arrowShadow.addPoint(x - 5, y + 13);

      g2d.setColor(new Color(0, 0, 0, 70));
      g2d.fill(arrowShadow);

      Polygon arrowHead = new Polygon();
      arrowHead.addPoint(x - 6, y);
      arrowHead.addPoint(x + 6, y + 6);
      arrowHead.addPoint(x - 6, y + 12);

      g2d.setColor(new Color(153, 51, 0));
      g2d.fill(arrowHead);
    }
    else {
      x -= 6;

      Polygon arrowShadow = new Polygon();
      arrowShadow.addPoint(x + 1, y - 5);
      arrowShadow.addPoint(x + 13, y - 5);
      arrowShadow.addPoint(x + 7, y + 7);

      g2d.setColor(new Color(0, 0, 0, 70));
      g2d.fill(arrowShadow);

      Polygon arrowHead = new Polygon();
      arrowHead.addPoint(x, y - 6);
      arrowHead.addPoint(x + 12, y - 6);
      arrowHead.addPoint(x + 6, y + 6);

      g2d.setColor(new Color(153, 51, 0));
      g2d.fill(arrowHead);
    }
  }
}
