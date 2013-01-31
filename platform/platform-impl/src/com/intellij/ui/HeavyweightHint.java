/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.util.EventListener;
import java.util.EventObject;

public class HeavyweightHint implements Hint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.HeavyweightHint");

  private final JComponent myComponent;
  private final boolean myFocusableWindowState;
  private final EventListenerList myListenerList;

  private JWindow myWindow;
  private JComponent myParentComponent;

  public HeavyweightHint(@NotNull final JComponent component, final boolean focusableWindowState) {
    myComponent = component;
    myFocusableWindowState = focusableWindowState;
    myListenerList = new EventListenerList();
  }

  /**
   * Shows the hint as the window.
   */
  @Override
  public void show(@NotNull JComponent parentComponent, int x, int y, @Nullable JComponent focusBackComponent, @Nullable HintHint hh) {
    myParentComponent = parentComponent;
    LOG.assertTrue(parentComponent.isShowing());

    Window windowAncestor = SwingUtilities.getWindowAncestor(parentComponent);
    LOG.assertTrue(windowAncestor != null);

    myWindow = new JWindow(windowAncestor);
    myWindow.setFocusableWindowState(myFocusableWindowState);
    WindowManagerEx.getInstanceEx().setWindowShadow(myWindow, WindowManagerEx.WindowShadowMode.DISABLED);

    myWindow.getContentPane().setLayout(new BorderLayout());
    myWindow.getContentPane().add(myComponent, BorderLayout.CENTER);

    updateBounds(x, y);
    myWindow.setVisible(true);
  }

  protected void fireHintHidden() {
    EventListener[] listeners = myListenerList.getListeners(HintListener.class);
    for (EventListener listener : listeners) {
      ((HintListener)listener).hintHidden(new EventObject(this));
    }
  }

  public Dimension getPreferredSize() {
    return myComponent.getPreferredSize();
  }

  @Override
  public boolean isVisible() {
    return myComponent.isShowing();
  }

  public JComponent getComponent() {
    return myComponent;
  }

  /**
   * Hides and disposes hint window.
   */
  @Override
  public void hide() {
    if (myWindow != null) {
      myWindow.dispose();
      myWindow = null;
    }
    fireHintHidden();
  }

  @Override
  public void pack() {
    updateBounds(-1, -1, false);
  }

  @Override
  public void updateBounds(int x, int y) {
    updateBounds(x, y, true);
  }

  private void updateBounds(int x, int y, boolean updateLocation) {
    if (myWindow != null) {
      if (updateLocation) {
        Point locationOnScreen = myParentComponent.getLocationOnScreen();
        myWindow.setLocation(locationOnScreen.x + x, locationOnScreen.y + y);
      }
      myWindow.setSize(getPreferredSize());
    }
  }

  @Override
  public void setLocation(@NotNull RelativePoint point) {
    if (myWindow != null) {
      myWindow.setLocation(point.getScreenPoint().x, point.getScreenPoint().y);
    }
  }

  @Override
  public void addHintListener(@NotNull HintListener listener) {
    myListenerList.add(HintListener.class, listener);
  }

  @Override
  public void removeHintListener(@NotNull HintListener listener) {
    myListenerList.remove(HintListener.class, listener);
  }
}