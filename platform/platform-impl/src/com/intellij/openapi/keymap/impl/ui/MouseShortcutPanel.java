// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.mac.MacGestureSupportForMouseShortcutPanel;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public final class MouseShortcutPanel extends ShortcutPanel<MouseShortcut> {
  private static final JBColor BACKGROUND = JBColor.namedColor("Panel.mouseShortcutBackground", new JBColor(0xF5F5F5, 0x4B4F52));

  private final int myClickCount;
  private MouseShortcut myMouseShortcut = null;

  private final MouseAdapter myMouseListener = new MouseAdapter() {
    @Override
    public void mouseWheelMoved(MouseWheelEvent event) {
      setShortcutIfNeeded(toMouseShortcut(event));
    }

    @Override
    public void mousePressed(MouseEvent event) {
      myMouseShortcut = toMouseShortcut(event);
    }

    private MouseShortcut toMouseShortcut(MouseEvent event) {
      int button = MouseShortcut.getButton(event);
      int clickCount = event instanceof MouseWheelEvent ? 1 : event.getClickCount();
      if (0 <= button && clickCount <= myClickCount) {
        int modifiers = event.getModifiersEx();
        return new MouseShortcut(button, modifiers, clickCount);
      }
      return null;
    }

    @Override
    public void mouseReleased(MouseEvent event) {
      event.consume();
      setShortcutIfNeeded(myMouseShortcut);
    }
  };

  MouseShortcutPanel(boolean allowDoubleClick) {
    super(new BorderLayout());
    myClickCount = allowDoubleClick ? 2 : 1;
    addMouseListener(myMouseListener);
    addMouseWheelListener(myMouseListener);
    if (SystemInfo.isMac && SystemInfo.isJetBrainsJvm) {
      new MacGestureSupportForMouseShortcutPanel(this, () -> myMouseShortcut = null);
    }
    setBackground(BACKGROUND);
    setOpaque(true);
  }

  @Override
  public void setShortcut(MouseShortcut shortcut) {
    MouseShortcut old = getShortcut();
    if (old != null || shortcut != null) {
      super.setShortcut(shortcut);
    }
  }

  private void setShortcutIfNeeded(MouseShortcut shortcut) {
    if (shortcut != null) setShortcut(shortcut);
  }
}
