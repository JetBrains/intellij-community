// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.plaf.beg;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalTabbedPaneUI;
import javax.swing.text.View;
import java.awt.*;

public final class BegTabbedPaneUI extends MetalTabbedPaneUI {
  private static final Color LIGHT = new Color(247, 243, 239);
  private static final Color DARK = new Color(189, 187, 182);

  private boolean myNoIconSpace = false;
  private boolean myPaintContentBorder = true;

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
    Object clientProperty = UIUtil.getTabbedPanePaintContentBorder(c);
    if (clientProperty instanceof Boolean aBoolean) {
      myPaintContentBorder = aBoolean.booleanValue();
    }
  }

  @Override
  protected Insets getContentBorderInsets(int tabPlacement) {
    if (tabPlacement == TOP && !myPaintContentBorder) {
      return JBUI.insetsTop(1);
    }
    if (tabPlacement == BOTTOM && !myPaintContentBorder) {
      return JBUI.insetsBottom(1);
    }
    if (tabPlacement == LEFT && !myPaintContentBorder) {
      return JBUI.insetsLeft(1);
    }
    if (tabPlacement == RIGHT && !myPaintContentBorder) {
      return JBUI.insetsRight(1);
    }
    return JBUI.insets(1);
  }

  @Override
  protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
    g.setColor(darkShadow);
    switch (tabPlacement) {
      case TOP -> {
        if (isSelected) {
          // left
          LinePainter2D.paint((Graphics2D)g, x, y + 1, x, y + h - 1);
          // top
          LinePainter2D.paint((Graphics2D)g, x + 1, y, x + w - 3, y);
          // right
          LinePainter2D.paint((Graphics2D)g, x + w - 2, y + 1, x + w - 2, y + h - 1);
        }
        else {
          // left
          LinePainter2D.paint((Graphics2D)g, x, y + 1, x, y + h - 1);
          // top
          LinePainter2D.paint((Graphics2D)g, x + 1, y, x + w - 3, y);
          // right
          LinePainter2D.paint((Graphics2D)g, x + w - 2, y + 1, x + w - 2, y + h - 1);
        }
      }
      case LEFT -> {
        // top
        LinePainter2D.paint((Graphics2D)g, x + 1, y + 1, x + w - 1, y + 1);
        // left
        LinePainter2D.paint((Graphics2D)g, x, y + 2, x, y + h - 2);
        //bottom
        LinePainter2D.paint((Graphics2D)g, x + 1, y + h - 1, x + w - 1, y + h - 1);
      }
      case BOTTOM -> {
        if (isSelected) {
          // left
          LinePainter2D.paint((Graphics2D)g, x, y, x, y + h - 2);
          // bottom
          LinePainter2D.paint((Graphics2D)g, x + 1, y + h - 1, x + w - 2, y + h - 1);
          // right
          LinePainter2D.paint((Graphics2D)g, x + w - 1, y, x + w - 1, y + h - 2);
        }
        else {
          // left
          LinePainter2D.paint((Graphics2D)g, x, y, x, y + h - 1);
          // bottom
          LinePainter2D.paint((Graphics2D)g, x + 1, y + h - 1, x + w - 3, y + h - 1);
          // right
          LinePainter2D.paint((Graphics2D)g, x + w - 2, y, x + w - 2, y + h - 1);
        }
      }
      case RIGHT -> {
        // top
        LinePainter2D.paint((Graphics2D)g, x, y + 1, x + w - 2, y + 1);
        // right
        LinePainter2D.paint((Graphics2D)g, x + w - 1, y + 2, x + w - 1, y + h - 2);
        //bottom
        LinePainter2D.paint((Graphics2D)g, x, y + h - 1, x + w - 2, y + h - 1);
      }
      default -> {
        throw new IllegalArgumentException("unknown tabPlacement: " + tabPlacement);
      }
    }
  }

  @Override
  protected void paintText(Graphics g, int tabPlacement,
                           Font font, FontMetrics metrics, int tabIndex,
                           String title, Rectangle textRect,
                           boolean isSelected) {
    if (isSelected) {
      font = font.isBold()? font : font.deriveFont(Font.BOLD);
      metrics = metrics.getFont().isBold()? metrics : g.getFontMetrics(font);
    }
    else {
      font = font.isPlain()? font : font.deriveFont(Font.PLAIN);
      metrics = metrics.getFont().isPlain()? metrics : g.getFontMetrics(font);
    }
    g.setFont(font);
    if (tabPane.isEnabled() && tabPane.isEnabledAt(tabIndex)) {
      g.setColor(tabPane.getForegroundAt(tabIndex));
      g.drawString(title, textRect.x - (myNoIconSpace ? 5 : 0), textRect.y + metrics.getAscent());
    }
    else {
      // tab disabled
      g.setColor(tabPane.getBackgroundAt(tabIndex).brighter());
      g.drawString(title, textRect.x, textRect.y + metrics.getAscent());
      g.setColor(tabPane.getBackgroundAt(tabIndex).darker());
      g.drawString(title, textRect.x - (myNoIconSpace ? 6 : 1), textRect.y + metrics.getAscent() - 1);
    }
  }

  @Override
  protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
    if (isSelected) {
      g.setColor(LIGHT);
    }
    else {
      g.setColor(DARK);
    }
    switch (tabPlacement) {
      case LEFT -> g.fillRect(x + 1, y + 2, w - 2, h - 3);
      case RIGHT -> g.fillRect(x, y + 2, w - 1, h - 3);
      case BOTTOM -> g.fillRect(x + 1, y, w - 3, h - 1);
      //case TOP,
      default -> g.fillRect(x + 1, y + 1, w - 2, h);
    }
  }

  @Override
  protected void paintContentBorderTopEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) {
    if (tabPlacement == TOP || myPaintContentBorder) {
      boolean leftToRight = isLeftToRight(tabPane);
      int right = x + w - 1;
      Rectangle selRect = selectedIndex < 0 ? null :
                          getTabBounds(selectedIndex, calcRect);
      g.setColor(darkShadow);

      // Draw unbroken line if tabs are not on TOP, OR
      // selected tab is not in run adjacent to content, OR
      // selected tab is not visible (SCROLL_TAB_LAYOUT)
      //
      if (tabPlacement != TOP || selectedIndex < 0 ||
          (selRect.y + selRect.height + 1 < y) ||
          (selRect.x < x || selRect.x > x + w)) {
        LinePainter2D.paint((Graphics2D)g, x, y, x + w - 1, y);
      }
      else {
        // Break line to show visual connection to selected tab
        boolean lastInRun = isLastInRun(selectedIndex);

        LinePainter2D.paint((Graphics2D)g, x, y, selRect.x, y);

        if (selRect.x + selRect.width < right - 1) {
          LinePainter2D.paint((Graphics2D)g, selRect.x + selRect.width - 2, y, right, y);
        }
        else {
          LinePainter2D.paint((Graphics2D)g, x + w - 2, y, x + w - 2, y);
        }
      }
    }
  }

  @Override
  protected void paintContentBorderBottomEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) {
    if (tabPlacement == BOTTOM || myPaintContentBorder) {
      boolean leftToRight = isLeftToRight(tabPane);
      int bottom = y + h - 1;
      int right = x + w - 1;
      Rectangle selRect = selectedIndex < 0 ? null :
                          getTabBounds(selectedIndex, calcRect);
      g.setColor(darkShadow);

      // Draw unbroken line if tabs are not on BOTTOM, OR
      // selected tab is not in run adjacent to content, OR
      // selected tab is not visible (SCROLL_TAB_LAYOUT)
      //
      if (tabPlacement != BOTTOM || selectedIndex < 0 ||
          (selRect.y - 1 > h) ||
          (selRect.x < x || selRect.x > x + w)) {
        LinePainter2D.paint((Graphics2D)g, x, y + h - 1, x + w - 1, y + h - 1);
      }
      else {
        // Break line to show visual connection to selected tab
        boolean lastInRun = isLastInRun(selectedIndex);

        if (leftToRight || lastInRun) {
          LinePainter2D.paint((Graphics2D)g, x, bottom, selRect.x, bottom);
        }
        else {
          LinePainter2D.paint((Graphics2D)g, x, bottom, selRect.x - 1, bottom);
        }

        if (selRect.x + selRect.width < x + w - 2) {
          if (leftToRight && !lastInRun) {
            LinePainter2D.paint((Graphics2D)g, selRect.x + selRect.width, bottom, right, bottom);
          }
          else {
            LinePainter2D.paint((Graphics2D)g, selRect.x + selRect.width - 1, bottom, right, bottom);
          }
        }
      }
    }
  }

  @Override
  protected void paintContentBorderLeftEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) {
    if (tabPlacement == LEFT || myPaintContentBorder) {
      Rectangle selRect = selectedIndex < 0 ? null :
                          getTabBounds(selectedIndex, calcRect);
      g.setColor(darkShadow);

      // Draw unbroken line if tabs are not on LEFT, OR
      // selected tab is not in run adjacent to content, OR
      // selected tab is not visible (SCROLL_TAB_LAYOUT)
      //
      if (tabPlacement != LEFT || selectedIndex < 0 ||
          (selRect.x + selRect.width + 1 < x) ||
          (selRect.y < y || selRect.y > y + h)) {
        LinePainter2D.paint((Graphics2D)g, x, y, x, y + h - 2);
      }
      else {
        // Break line to show visual connection to selected tab
        LinePainter2D.paint((Graphics2D)g, x, y, x, selRect.y + 1);
        if (selRect.y + selRect.height < y + h - 2) {
          LinePainter2D.paint((Graphics2D)g, x, selRect.y + selRect.height + 1, x, y + h + 2);
        }
      }
    }
  }

  @Override
  protected void paintContentBorderRightEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) {
    if (tabPlacement == RIGHT || myPaintContentBorder) {
      Rectangle selRect = selectedIndex < 0 ? null :
                          getTabBounds(selectedIndex, calcRect);
      g.setColor(darkShadow);

      // Draw unbroken line if tabs are not on RIGHT, OR
      // selected tab is not in run adjacent to content, OR
      // selected tab is not visible (SCROLL_TAB_LAYOUT)
      //
      if (tabPlacement != RIGHT || selectedIndex < 0 ||
          (selRect.x - 1 > w) ||
          (selRect.y < y || selRect.y > y + h)) {
        LinePainter2D.paint((Graphics2D)g, x + w - 1, y, x + w - 1, y + h - 1);
      }
      else {
        // Break line to show visual connection to selected tab
        LinePainter2D.paint((Graphics2D)g, x + w - 1, y, x + w - 1, selRect.y);

        if (selRect.y + selRect.height < y + h - 2) {
          LinePainter2D.paint((Graphics2D)g, x + w - 1, selRect.y + selRect.height, x + w - 1, y + h - 2);
        }
      }
    }
  }

  private boolean isLastInRun(int tabIndex) {
    int run = getRunForTab(tabPane.getTabCount(), tabIndex);
    int lastIndex = lastTabInRun(tabPane.getTabCount(), run);
    return tabIndex == lastIndex;
  }

  static boolean isLeftToRight(Component c) {
    return c.getComponentOrientation().isLeftToRight();
  }

  @Override
  protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
    return (int)(super.calculateTabHeight(tabPlacement, tabIndex, fontHeight) * 1.0);
  }

  @Override
  protected int calculateMaxTabHeight(int tabPlacement) {
    FontMetrics metrics = getFontMetrics();
    int tabCount = tabPane.getTabCount();
    int result = 0;
    int fontHeight = metrics.getHeight();
    for (int i = 0; i < tabCount; i++) {
      result = Math.max(calculateTabHeight(tabPlacement, i, fontHeight), result);
    }
    return result;
  }

  /**
   * invoked by reflection
   */
  public static ComponentUI createUI(JComponent c) {
    return new BegTabbedPaneUI();
  }

  /**
   * IdeaTabbedPaneUI uses bold font for selected tab. Bold width of some fonts is
   * less then width of plain font. To handle correctly this "anomaly" we have to
   * determine maximum of these two widths.
   */
  @Override
  protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
    final Font font = metrics.getFont();
    final FontMetrics plainMetrics = font.isPlain()? metrics : tabPane.getFontMetrics(font.deriveFont(Font.PLAIN));
    final int widthPlain = super.calculateTabWidth(tabPlacement, tabIndex, plainMetrics);

    final FontMetrics boldMetrics = font.isBold()? metrics : tabPane.getFontMetrics(font.deriveFont(Font.BOLD));
    final int widthBold = super.calculateTabWidth(tabPlacement, tabIndex, boldMetrics);

    final int width = Math.max(widthPlain, widthBold);

    myLayoutMetrics = (width == widthPlain)? plainMetrics : boldMetrics;

    return width;
  }

  private FontMetrics myLayoutMetrics = null;

  @Override
  protected void layoutLabel(int tabPlacement, FontMetrics metrics, int tabIndex, @NlsContexts.TabTitle String title, Icon icon, Rectangle tabRect,
                             Rectangle iconRect, Rectangle textRect, boolean isSelected) {

    metrics = (myLayoutMetrics != null)? myLayoutMetrics : metrics;
    textRect.x = textRect.y = iconRect.x = iconRect.y = 0;

    View v = getTextViewForTab(tabIndex);
    if (v != null) {
        tabPane.putClientProperty("html", v);
    }

    SwingUtilities.layoutCompoundLabel(tabPane,
                                       metrics, title, icon,
                                       SwingUtilities.CENTER,
                                       // left align title on LEFT/RIGHT placed tab
                                       tabPlacement == RIGHT || tabPlacement == LEFT ? SwingUtilities.LEFT : SwingUtilities.CENTER,
                                       SwingUtilities.CENTER,
                                       SwingUtilities.TRAILING,
                                       tabRect,
                                       iconRect,
                                       textRect,
                                       textIconGap);

    tabPane.putClientProperty("html", null);

    int xNudge = getTabLabelShiftX(tabPlacement, tabIndex, isSelected);
    int yNudge = getTabLabelShiftY(tabPlacement, tabIndex, isSelected);
    iconRect.x += xNudge;
    iconRect.y += yNudge;
    textRect.x += xNudge;
    textRect.y += yNudge;

    //super.layoutLabel(tabPlacement, _metrics, tabIndex, title, icon, tabRect, iconRect, textRect, isSelected);
  }

  public void setNoIconSpace(boolean noIconSpace) {
    myNoIconSpace = noIconSpace;
  }

  public void setPaintContentBorder(boolean paintContentBorder) {
    myPaintContentBorder = paintContentBorder;
  }

  @Override
  protected int calculateTabAreaHeight(int tabPlacement, int horizRunCount, int maxTabHeight) {
    for (int i = 0; i < tabPane.getTabCount(); i++) {
      Component component = tabPane.getComponentAt(i);
      if (component != null) {
        return super.calculateTabAreaHeight(tabPlacement, horizRunCount, maxTabHeight);
      }
    }
    return maxTabHeight + tabRunOverlay;
  }

}
