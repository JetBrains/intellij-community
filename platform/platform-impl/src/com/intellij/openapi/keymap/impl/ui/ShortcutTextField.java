// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.keymap.impl.ui;

import com.intellij.icons.AllIcons.General;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.KeyStrokeAdapter;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

public final class ShortcutTextField extends ExtendableTextField {
  private KeyStroke myKeyStroke;
  private int myLastPressedKeyCode = KeyEvent.VK_UNDEFINED;

  ShortcutTextField(boolean isFocusTraversalKeysEnabled) {
    enableEvents(AWTEvent.KEY_EVENT_MASK);
    setFocusTraversalKeysEnabled(isFocusTraversalKeysEnabled);
    if (isFocusTraversalKeysEnabled) {
      setExtensions(Extension.create(General.InlineAdd, General.InlineAddHover, getPopupTooltip(), this::showPopup));
    }
    setCaret(new DefaultCaret() {
      @Override
      public boolean isVisible() {
        return false;
      }
    });
  }

  private static boolean absolutelyUnknownKey (KeyEvent e) {
    return e.getKeyCode() == 0
           && e.getKeyChar() == KeyEvent.CHAR_UNDEFINED
           && e.getKeyLocation() == KeyEvent.KEY_LOCATION_UNKNOWN
           && e.getExtendedKeyCode() == 0;
  }

  @Override
  protected void processKeyEvent(KeyEvent e) {
    int keyCode = e.getKeyCode();
    if (getFocusTraversalKeysEnabled() && e.getModifiers() == 0 && e.getModifiersEx() == 0) {
      if (keyCode == KeyEvent.VK_ESCAPE || (keyCode == KeyEvent.VK_ENTER && myKeyStroke != null)) {
        super.processKeyEvent(e);
        return;
      }
    }

    final boolean isNotModifierKey = keyCode != KeyEvent.VK_SHIFT &&
                                     keyCode != KeyEvent.VK_ALT &&
                                     keyCode != KeyEvent.VK_CONTROL &&
                                     keyCode != KeyEvent.VK_ALT_GRAPH &&
                                     keyCode != KeyEvent.VK_META &&
                                     !absolutelyUnknownKey(e);

    if (isNotModifierKey) {
      // NOTE: when user presses 'Alt + Right' at Linux the IDE can receive next sequence KeyEvents: ALT_PRESSED -> RIGHT_RELEASED ->  ALT_RELEASED
      // RIGHT_PRESSED can be skipped, it depends on WM
      if (
        e.getID() == KeyEvent.KEY_PRESSED
        || (e.getID() == KeyEvent.KEY_RELEASED &&
            SystemInfo.isLinux && (e.isAltDown() || e.isAltGraphDown()) && myLastPressedKeyCode != keyCode) // press-event was skipped
      ) {
        setKeyStroke(KeyStrokeAdapter.getDefaultKeyStroke(e));
      }

      if (e.getID() == KeyEvent.KEY_PRESSED)
        myLastPressedKeyCode = keyCode;
    }

    // Ensure TAB/Shift-TAB work as focus traversal keys, otherwise
    // there is no proper way to move the focus outside the text field.
    if (!getFocusTraversalKeysEnabled() && ScreenReader.isActive()) {
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

  private void showPopup() {
    JBPopupMenu menu = new JBPopupMenu();
    getKeyStrokes().forEach(stroke -> menu.add(getPopupAction(stroke)));
    Insets insets = getInsets();
    menu.show(this, getWidth() - insets.right, insets.top);
  }

  @NotNull
  private Action getPopupAction(@NotNull KeyStroke stroke) {
    return new AbstractAction("Set " + KeymapUtil.getKeystrokeText(stroke)) {
      @Override
      public void actionPerformed(ActionEvent event) {
        setKeyStroke(stroke);
      }
    };
  }

  @NotNull
  private String getPopupTooltip() {
    StringBuilder sb = new StringBuilder();
    String prefix = "Set shortcuts with ";
    for (KeyStroke stroke : getKeyStrokes()) {
      if (0 == stroke.getModifiers()) {
        sb.append(prefix).append(KeymapUtil.getKeystrokeText(stroke));
        prefix = ", ";
      }
    }
    return sb.append(" keys").toString();
  }

  @NotNull
  private Iterable<KeyStroke> getKeyStrokes() {
    ArrayList<KeyStroke> list = new ArrayList<>();
    addKeyStrokes(list, getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
    addKeyStrokes(list, getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS));
    addKeyStrokes(list, getFocusTraversalKeys(KeyboardFocusManager.UP_CYCLE_TRAVERSAL_KEYS));
    addKeyStrokes(list, getFocusTraversalKeys(KeyboardFocusManager.DOWN_CYCLE_TRAVERSAL_KEYS));

    list.add(0, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
    list.add(1, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
    return list;
  }

  private static void addKeyStrokes(@NotNull ArrayList<? super KeyStroke> list, @Nullable Iterable<? extends AWTKeyStroke> strokes) {
    if (strokes != null) {
      for (AWTKeyStroke stroke : strokes) {
        int keyCode = stroke.getKeyCode();
        if (keyCode != KeyEvent.VK_UNDEFINED) {
          list.add(stroke instanceof KeyStroke
                   ? (KeyStroke)stroke
                   : KeyStroke.getKeyStroke(keyCode, stroke.getModifiers(), stroke.isOnKeyRelease()));
        }
      }
    }
  }
}