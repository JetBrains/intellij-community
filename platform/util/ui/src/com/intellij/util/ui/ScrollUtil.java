// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.ui;

import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public final class ScrollUtil {
  private ScrollUtil() {}


  @Nullable
  public static JScrollPane findScrollPane(JComponent c) {
    if (c == null) return null;
    return UIUtil.findComponentOfType(c, JScrollPane.class);
  }

  @Nullable
  public static JScrollBar findVerticalScrollBar(JComponent c) {
    return findScrollBar(c, Adjustable.VERTICAL);
  }

  @Nullable
  public static JScrollBar findHorizontalScrollBar(JComponent c) {
    return findScrollBar(c, Adjustable.HORIZONTAL);
  }

  @Nullable
  private static JScrollBar findScrollBar(JComponent c, @JdkConstants.AdjustableOrientation int orientation) {
    if (c == null) return null;
    if (c instanceof JScrollBar && ((JScrollBar)c).getOrientation() == orientation) {
      return (JScrollBar)c;
    }
    for (Component comp : c.getComponents()) {
      if (comp instanceof JComponent) {
        final JScrollBar scrollBar = findScrollBar((JComponent)comp, orientation);
        if (scrollBar != null) {
          return scrollBar;
        }
      }
    }
    return null;
  }

  public static void scrollVertically(JComponent c, int position) {
    final JScrollPane pane = findScrollPane(c);
    if (pane != null) {
      final JScrollBar bar = pane.getVerticalScrollBar();
      if (bar != null) {
        bar.setValue(position);
      }
    } else {
      final JScrollBar scrollBar = findVerticalScrollBar(c);
      if (scrollBar != null) {
        scrollBar.setValue(position);
      }
    }
  }

  public static void scrollHorizontally(JComponent c, int position) {
    final JScrollPane pane = findScrollPane(c);
    if (pane != null) {
      final JScrollBar bar = pane.getHorizontalScrollBar();
      if (bar != null) {
        bar.setValue(position);
      }
    } else {
      final JScrollBar scrollBar = findHorizontalScrollBar(c);
      if (scrollBar != null) {
        scrollBar.setValue(position);
      }
    }
  }

  public static void center(final JComponent c, final Rectangle r) {
    center(c, r, false);
  }

  public static void center(final JComponent c, final Rectangle r, final boolean withInsets) {
    final Rectangle visible = c.getVisibleRect();
    visible.x = r.x - (visible.width - r.width) / 2;
    visible.y = r.y - (visible.height - r.height) / 2;

    Rectangle bounds = new Rectangle(c.getWidth(), c.getHeight());
    if (!withInsets) JBInsets.removeFrom(bounds, c.getInsets());

    if (visible.x < bounds.x) {
      visible.x = bounds.x;
    }

    if (visible.x + visible.width > bounds.x + bounds.width) {
      visible.x = bounds.x + bounds.width - visible.width;
    }

    if (visible.y < bounds.y) {
      visible.y = bounds.y;
    }

    if (visible.y + visible.height > bounds.y + bounds.height) {
      visible.y = bounds.y + bounds.height - visible.height;
    }

    c.scrollRectToVisible(visible);
  }

  public enum ScrollBias {
    /**
     * take the policy of the viewport
     */
    VIEWPORT,
    UNCHANGED,      // don't scroll if it fills the visible area, otherwise take the policy of the viewport
    FIRST,          // scroll the first part of the region into view
    CENTER,         // center the region
    LAST           // scroll the last part of the region into view
  }

  public static void scroll(JComponent c, Rectangle r, ScrollBias horizontalBias, ScrollBias verticalBias) {
    Rectangle visible = c.getVisibleRect();
    Rectangle dest = new Rectangle(r);

    if (dest.width > visible.width) {
      if (horizontalBias == ScrollBias.VIEWPORT) {
        // leave as is
      }
      else if (horizontalBias == ScrollBias.UNCHANGED) {
        if (dest.x <= visible.x && dest.x + dest.width >= visible.x + visible.width) {
          dest.width = visible.width;
        }
      }
      else {
        if (horizontalBias == ScrollBias.CENTER) {
          dest.x += (dest.width - visible.width) / 2;
        }
        else if (horizontalBias == ScrollBias.LAST) dest.x += dest.width - visible.width;

        dest.width = visible.width;
      }
    }

    if (dest.height > visible.height) {
      // same code as above in the other direction
    }

    if (!visible.contains(dest)) c.scrollRectToVisible(dest);
  }
}
