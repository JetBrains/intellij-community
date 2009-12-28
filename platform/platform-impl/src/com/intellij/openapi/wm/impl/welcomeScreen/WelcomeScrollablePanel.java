/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
  }

  public boolean getScrollableTracksViewportHeight() {
    if (getParent() instanceof JViewport) {
      return getParent().getHeight() > getPreferredSize().height;
    }
    return false;
  }

  public boolean getScrollableTracksViewportWidth() {
    if (getParent() instanceof JViewport) {
      return getParent().getWidth() > getPreferredSize().width;
    }
    return false;
  }

  public Dimension getPreferredScrollableViewportSize() {
    return this.getPreferredSize();
  }

  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) return visibleRect.height - VERTICAL_SCROLL_INCREMENT;
    else return visibleRect.width;
  }

  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) return VERTICAL_SCROLL_INCREMENT;
    else return HORIZONTAL_SCROLL_INCREMENT;
  }
}
