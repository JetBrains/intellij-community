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
package com.intellij.ui.plaf.beg;

import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalScrollBarUI;
import javax.swing.plaf.metal.MetalScrollPaneUI;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Eugene Belyaev
 */
public class BegScrollPaneUI extends MetalScrollPaneUI {
  @NonNls public static final String VERTICAL_SCROLL_BAR_PROPERTY = "verticalScrollBar";
  @NonNls public static final String HORIZONTAL_SCROLL_BAR_PROPERTY = "horizontalScrollBar";
  @NonNls public static final String BORDER_PROPERTY = "border";

  public static ComponentUI createUI(JComponent x) {
    return new BegScrollPaneUI();
  }

  /**
   * If the border of the scrollpane is an instance of
   * {@code MetalBorders.ScrollPaneBorder}, the client property
   * {@code FREE_STANDING_PROP} of the scrollbars
   * is set to false, otherwise it is set to true.
   */
  private void updateScrollbarsFreeStanding() {
    if (scrollpane == null) {
      return;
    }
    Object value = Boolean.FALSE;
    scrollpane.getHorizontalScrollBar().putClientProperty
        (MetalScrollBarUI.FREE_STANDING_PROP, value);
    scrollpane.getVerticalScrollBar().putClientProperty
        (MetalScrollBarUI.FREE_STANDING_PROP, value);
  }

  protected PropertyChangeListener createScrollBarSwapListener() {
    return new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent e) {
        String propertyName = e.getPropertyName();
        if (propertyName.equals(VERTICAL_SCROLL_BAR_PROPERTY) ||
            propertyName.equals(HORIZONTAL_SCROLL_BAR_PROPERTY)) {
          ((JScrollBar) e.getOldValue()).putClientProperty(MetalScrollBarUI.FREE_STANDING_PROP,
              null);
          ((JScrollBar) e.getNewValue()).putClientProperty(MetalScrollBarUI.FREE_STANDING_PROP,
              Boolean.FALSE);
        }
        else if (BORDER_PROPERTY.equals(propertyName)) {
          updateScrollbarsFreeStanding();
        }
      }
    };
  }
}
