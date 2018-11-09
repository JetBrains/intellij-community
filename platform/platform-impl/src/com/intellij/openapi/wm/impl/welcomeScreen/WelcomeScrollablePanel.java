// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author pti
 */
public class WelcomeScrollablePanel extends JPanel implements Scrollable{

  private static final int VERTICAL_SCROLL_INCREMENT = UIUtil.getToolTipFont().getSize() * 2;
  private static final int HORIZONTAL_SCROLL_INCREMENT = VERTICAL_SCROLL_INCREMENT;

  public WelcomeScrollablePanel(LayoutManager layout) {
    super(layout);
    setOpaque(false);
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    if (getParent() instanceof JViewport) {
      return getParent().getHeight() > getPreferredSize().height;
    }
    return false;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    if (getParent() instanceof JViewport) {
      return getParent().getWidth() > getPreferredSize().width;
    }
    return false;
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return this.getPreferredSize();
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) return visibleRect.height - VERTICAL_SCROLL_INCREMENT;
    else return visibleRect.width;
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) return VERTICAL_SCROLL_INCREMENT;
    else return HORIZONTAL_SCROLL_INCREMENT;
  }
}
