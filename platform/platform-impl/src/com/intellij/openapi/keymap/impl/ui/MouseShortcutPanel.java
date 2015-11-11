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

/**
 * @author Sergey.Malenkov
 */
final class MouseShortcutPanel extends ShortcutPanel<MouseShortcut> {
  static final JBColor FOREGROUND = new JBColor(0x8C8C8C, 0x8C8C8C);
  static final JBColor BACKGROUND = new JBColor(0xF5F5F5, 0x4B4F52);
  static final JBColor BORDER = new JBColor(0xDEDEDE, 0x383B3D);

  private final int myClickCount;
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
      if (0 <= button && clickCount <= myClickCount) {
        int modifiers = event.getModifiersEx();
        setShortcut(new MouseShortcut(button, modifiers, clickCount));
      }
    }
  };

  MouseShortcutPanel(boolean allowDoubleClick) {
    super(new BorderLayout());
    myClickCount = allowDoubleClick ? 2 : 1;
    addMouseListener(myMouseListener);
    addMouseWheelListener(myMouseListener);
    setBackground(BACKGROUND);
    setOpaque(true);
  }

  void setShortcut(MouseShortcut shortcut) {
    MouseShortcut old = getShortcut();
    if (old != null || shortcut != null) {
      super.setShortcut(shortcut);
    }
  }
}
