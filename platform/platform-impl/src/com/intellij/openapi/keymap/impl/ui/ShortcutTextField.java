// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.KeyStrokeAdapter;
import com.intellij.util.ui.accessibility.ScreenReader;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.KeyEvent;

public final class ShortcutTextField extends JTextField {
  private KeyStroke myKeyStroke;

  ShortcutTextField() {
    enableEvents(AWTEvent.KEY_EVENT_MASK);
    setFocusTraversalKeysEnabled(false);
    setCaret(new DefaultCaret() {
      @Override
      public boolean isVisible() {
        return false;
      }
    });
  }

  private static boolean absolutelyUnknownKey (KeyEvent e) {
    return e.getKeyCode() == 0
           && e.getExtendedKeyCode() == 0
           && e.getKeyChar() == KeyEvent.CHAR_UNDEFINED
           && e.getKeyLocation() == KeyEvent.KEY_LOCATION_UNKNOWN
           && e.getExtendedKeyCode() == 0;
  }

  @Override
  protected void processKeyEvent(KeyEvent e) {
    if (e.getID() == KeyEvent.KEY_PRESSED) {
      int keyCode = e.getKeyCode();

      if (keyCode != KeyEvent.VK_SHIFT &&
          keyCode != KeyEvent.VK_ALT &&
          keyCode != KeyEvent.VK_CONTROL &&
          keyCode != KeyEvent.VK_ALT_GRAPH &&
          keyCode != KeyEvent.VK_META &&
          !absolutelyUnknownKey(e))
      {
        setKeyStroke(KeyStrokeAdapter.getDefaultKeyStroke(e));
      }
    }
    // Ensure TAB/Shift-TAB work as focus traversal keys, otherwise
    // there is no proper way to move the focus outside the text field.
    if (ScreenReader.isActive()) {
      setFocusTraversalKeysEnabled(true);
      try {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().processKeyEvent(this, e);
      }
      finally {
        setFocusTraversalKeysEnabled(false);
      }
    }
  }

  void setKeyStroke(KeyStroke keyStroke) {
    KeyStroke old = myKeyStroke;
    if (old != null || keyStroke != null) {
      myKeyStroke = keyStroke;
      super.setText(KeymapUtil.getKeystrokeText(keyStroke));
      setCaretPosition(0);
      firePropertyChange("keyStroke", old, keyStroke);
    }
  }

  KeyStroke getKeyStroke() {
    return myKeyStroke;
  }

  @Override
  public void enableInputMethods(boolean enable) {
    super.enableInputMethods(enable && Registry.is("ide.settings.keymap.input.method.enabled"));
  }

  @Override
  public void setText(String text) {
    super.setText(text);
    setCaretPosition(0);
    if (text == null || text.isEmpty()) {
      myKeyStroke = null;
      firePropertyChange("keyStroke", null, null);
    }
  }
}