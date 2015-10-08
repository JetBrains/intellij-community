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
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;

import java.awt.*;
import java.awt.event.*;
import javax.swing.SwingUtilities;

/**
 * @author Sergey.Malenkov
 */
abstract class MouseShortcutConsumer implements HierarchyListener, Consumer<MouseShortcut> {
  private Window myWindow;
  private Component myComponent;
  private MouseShortcut myShortcut;
  private final MouseAdapter myListener = new MouseAdapter() {
    @Override
    public void mouseWheelMoved(MouseWheelEvent event) {
      setShortcutFrom(event);
    }

    @Override
    public void mouseReleased(MouseEvent event) {
      setShortcutFrom(event);
    }
  };

  MouseShortcutConsumer(MouseShortcut shortcut) {
    if (shortcut != null) {
      setShortcut(shortcut);
    }
  }

  MouseShortcut getShortcut() {
    return myShortcut;
  }

  private void setShortcut(MouseShortcut shortcut) {
    myShortcut = shortcut;
    consume(shortcut);
  }

  private void setShortcutFrom(MouseEvent event) {
    if (myComponent != null) {
      Point point = SwingUtilities.convertPoint(event.getComponent(), event.getX(), event.getY(), myComponent);
      if (0 <= point.x && point.x < myComponent.getWidth() && 0 <= point.y && point.y < myComponent.getHeight()) {
        event.consume();

        int button = MouseShortcut.getButton(event);
        if (button >= 0) {
          int modifiers = event.getModifiersEx();
          int clickCount = event instanceof MouseWheelEvent ? 1 : event.getClickCount();
          if (myShortcut == null
              || button != myShortcut.getButton()
              || modifiers != myShortcut.getModifiers()
              || clickCount != myShortcut.getClickCount()) {
            setShortcut(new MouseShortcut(button, modifiers, clickCount));
          }
        }
      }
    }
  }

  @Override
  public void hierarchyChanged(HierarchyEvent event) {
    Component component = event.getComponent();
    if (component != null) {
      if (myComponent == null) {
        myComponent = component;
      }
      if (HierarchyEvent.SHOWING_CHANGED == (HierarchyEvent.SHOWING_CHANGED & event.getChangeFlags())) {
        if (myComponent == component) {
          Window window = !component.isShowing() ? null : UIUtil.getWindow(component);
          if (myWindow != window) {
            // It's very important that MouseListener is added to the Window.
            // If you add the same listener, for example, to the component
            // you will get fake Alt and Meta modifiers.
            // Pressing of a middle button causes Alt+Button2 event:
            // http://bugs.openjdk.java.net/browse/JDK-4109826
            if (myWindow != null) {
              myWindow.removeMouseListener(myListener);
              myWindow.removeMouseWheelListener(myListener);
            }
            myWindow = window;
            if (myWindow != null) {
              myWindow.addMouseListener(myListener);
              myWindow.addMouseWheelListener(myListener);
            }
          }
        }
      }
    }
  }
}
