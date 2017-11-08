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
package com.intellij.ui.popup;

import com.intellij.util.ReflectionUtil;

import javax.swing.JWindow;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayDeque;

/**
 * @author Sergey Malenkov
 */
final class HeavyWeightPopupCache implements ComponentListener {
  /**
   * Returns a cached window or creates a new one if cache is empty.
   *
   * @param owner the window from which a popup is displayed
   * @return a window for the specified owner
   */
  public static Window create(Window owner) {
    if (owner != null) {
      HeavyWeightPopupCache cache = getCache(owner);
      if (cache != null) {
        Window popup = cache.poll();
        if (popup != null) return popup;
      }
    }
    return new JWindow(owner);
  }

  /**
   * Caches a window if possible.
   *
   * @param popup the popup window that is not needed
   */
  public static void dispose(Window popup) {
    if (popup != null) {
      hide(popup);
      Window owner = popup.getOwner();
      if (owner == null || !owner.isDisplayable()) {
        popup.dispose();
      }
      else {
        HeavyWeightPopupCache cache = getCache(owner);
        if (cache == null) {
          cache = new HeavyWeightPopupCache();
          owner.addComponentListener(cache);
        }
        cache.push(popup);
      }
    }
  }

  private static HeavyWeightPopupCache getCache(Window owner) {
    for (ComponentListener listener : owner.getComponentListeners()) {
      if (listener instanceof HeavyWeightPopupCache) {
        return (HeavyWeightPopupCache)listener;
      }
    }
    return null;
  }

  private static Window poll(ArrayDeque<Window> windows) {
    return windows.poll();
  }

  private static void push(ArrayDeque<Window> windows, Window popup) {
    windows.push(popup);
  }

  private static void hide(ArrayDeque<Window> windows) {
    for (Window window : windows) hide(window);
  }

  /**
   * Disables showing a child window with its parent.
   *
   * @param window the popup window that is not needed
   */
  private static void hide(Window window) {
    // HACK: do not allow to show a hidden popup on showing its parent
    ReflectionUtil.setField(Window.class, window, boolean.class, "showWithParent", false);
  }

  private final ArrayDeque<Window> myWindows = new ArrayDeque<>();

  private Window poll() {
    return poll(myWindows);
  }

  private void push(Window popup) {
    push(myWindows, popup);
  }

  private void hide() {
    hide(myWindows);
  }

  @Override
  public void componentResized(ComponentEvent event) {
  }

  @Override
  public void componentMoved(ComponentEvent event) {
  }

  @Override
  public void componentShown(ComponentEvent event) {
  }

  @Override
  public void componentHidden(ComponentEvent event) {
    Object source = event.getSource();
    if (source instanceof Window) {
      HeavyWeightPopupCache cache = getCache((Window)source);
      if (cache != null) cache.hide();
    }
  }
}
