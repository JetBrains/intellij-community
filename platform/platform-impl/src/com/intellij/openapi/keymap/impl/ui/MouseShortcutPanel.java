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
import com.intellij.ui.JBColor;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.JPanel;

/**
 * @author Sergey.Malenkov
 */
final class MouseShortcutPanel extends JPanel {
  static final JBColor FOREGROUND = new JBColor(0x8C8C8C, 0x8C8C8C);
  static final JBColor BACKGROUND = new JBColor(0xF5F5F5, 0x4B4F52);
  static final JBColor BORDER = new JBColor(0xDEDEDE, 0x383B3D);

  private MouseShortcut myShortcut;

  private final MouseAdapter myMouseListener = new MouseAdapter() {
    @Override
    public void mouseWheelMoved(MouseWheelEvent event) {
      mousePressed(event);
    }

    @Override
    public void mousePressed(MouseEvent event) {
      event.consume();

      int button = MouseShortcut.getButton(event);
      int clickCount = event instanceof MouseWheelEvent ? 1 : event.getClickCount();
      if (0 <= button && clickCount < 3) {
        int modifiers = event.getModifiersEx();
        if (myShortcut == null
            || button != myShortcut.getButton()
            || modifiers != myShortcut.getModifiers()
            || clickCount != myShortcut.getClickCount()) {
          setShortcut(new MouseShortcut(button, modifiers, clickCount));
        }
      }
    }
  };

  MouseShortcutPanel() {
    super(new BorderLayout());
    addMouseListener(myMouseListener);
    addMouseWheelListener(myMouseListener);
    setBackground(BACKGROUND);
    setOpaque(true);
  }

  MouseShortcut getShortcut() {
    return myShortcut;
  }

  void setShortcut(MouseShortcut shortcut) {
    MouseShortcut old = myShortcut;
    if (old != null || shortcut != null) {
      myShortcut = shortcut;
      firePropertyChange("shortcut", old, shortcut);
    }
  }
}
