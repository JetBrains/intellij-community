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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.lang.reflect.Method;

/**
 * Implements precise (with sub-line resolution) mouse wheel/trackpad scrolling in JScrollPane if platform supports it 
 * (currently known to work on Mac OS with Java 7 or later).
 * <p>
 * Scroll pane's view is supposed to implement {@link Scrollable}.
 * 
 * @see MouseWheelEvent#getPreciseWheelRotation()
 */
public class PreciseMouseWheelScroller implements MouseWheelListener {
  private static final Logger LOGGER = Logger.getInstance(PreciseMouseWheelScroller.class);
  private static Method PRECISE_WHEEL_ROTATION_GETTER;

  static {
    if (SystemInfo.isJavaVersionAtLeast("1.7")) {
      try {
        Method method = MouseWheelEvent.class.getMethod("getPreciseWheelRotation");
        Class<?> returnType = method.getReturnType();
        if (!returnType.equals(double.class)) throw new RuntimeException("Unexpected method return type: " + returnType);
        PRECISE_WHEEL_ROTATION_GETTER = method;
      }
      catch (Exception e) {
        LOGGER.warn("Couldn't access getPreciseWheelRotation method", e);
      }
    }
  }

  public static void install(@NotNull JScrollPane scrollPane) {
    scrollPane.setWheelScrollingEnabled(false);
    scrollPane.addMouseWheelListener(new PreciseMouseWheelScroller(scrollPane));
  }
  
  private final JScrollPane myScrollPane;

  private PreciseMouseWheelScroller(JScrollPane scrollPane) {
    myScrollPane = scrollPane;
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    e.consume();
    if (e.getWheelRotation() != 0) {
      int orientation = SwingConstants.VERTICAL;
      JScrollBar toScroll = myScrollPane.getVerticalScrollBar();
      if (toScroll == null || !toScroll.isVisible() || e.isShiftDown()) {
        orientation = SwingConstants.HORIZONTAL;
        toScroll = myScrollPane.getHorizontalScrollBar();
        if (toScroll == null || !toScroll.isVisible()) {
          return;
        }
      }

      int direction = e.getWheelRotation() < 0 ? -1 : 1;

      if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
        JViewport vp = myScrollPane.getViewport();
        Component comp = vp.getView();

        boolean limitScroll = Math.abs(e.getWheelRotation()) == 1;

        Scrollable scrollComp = (Scrollable) comp;
        Rectangle viewRect = vp.getViewRect();
        int scrollMin = toScroll.getMinimum();
        int scrollMax = toScroll.getMaximum() - toScroll.getModel().getExtent();

        if (limitScroll) {
          int blockIncr = scrollComp.getScrollableBlockIncrement(viewRect, orientation, direction);
          if (direction < 0) {
            scrollMin = Math.max(scrollMin, toScroll.getValue() - blockIncr);
          }
          else {
            scrollMax = Math.min(scrollMax, toScroll.getValue() + blockIncr);
          }
        }

        double units = getUnitsToScroll(e);
        double shift = scrollComp.getScrollableUnitIncrement(viewRect, orientation, direction) * units;
        if (orientation == SwingConstants.VERTICAL) {
          viewRect.y += shift;
          if (direction < 0) {
            if (viewRect.y <= scrollMin) {
              viewRect.y = scrollMin;
            }
          }
          else {
            if (viewRect.y >= scrollMax) {
              viewRect.y = scrollMax;
            }
          }
          toScroll.setValue(viewRect.y);
        }
        else {
          viewRect.x += shift;
          if (direction < 0) {
            if (viewRect.x < scrollMin) {
              viewRect.x = scrollMin;
            }
          }
          else {
            if (viewRect.x > scrollMax) {
              viewRect.x = scrollMax;
            }
          }
          toScroll.setValue(viewRect.x);
        }
      }
      else if (e.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL) {
        int oldValue = toScroll.getValue();
        int blockIncrement = toScroll.getBlockIncrement(direction);
        int delta = blockIncrement * direction;
        int newValue = oldValue + delta;

        if (delta > 0 && newValue < oldValue) {
          newValue = toScroll.getMaximum();
        }
        else if (delta < 0 && newValue > oldValue) {
          newValue = toScroll.getMinimum();
        }

        toScroll.setValue(newValue);
      }
    }
  }

  // Required method was added in Java 7, but we need to compile for Java 6, so we resort to reflection
  private static double getUnitsToScroll(MouseWheelEvent e) {
    if (PRECISE_WHEEL_ROTATION_GETTER != null) {
      try {
        Double value = (Double)PRECISE_WHEEL_ROTATION_GETTER.invoke(e);
        return e.getScrollAmount() * value;
      }
      catch (Exception ex) {
        LOGGER.debug("Error calling getPreciseWheelRotation method", ex);
      }
    }
    return e.getUnitsToScroll();
  }
}
