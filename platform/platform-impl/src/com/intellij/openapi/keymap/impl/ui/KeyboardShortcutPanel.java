// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.KeyboardGestureAction;
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.wm.IdeFocusManager;

import javax.swing.JCheckBox;
import javax.swing.KeyStroke;
import java.awt.LayoutManager;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

final class KeyboardShortcutPanel extends ShortcutPanel<Shortcut> {
  final ShortcutTextField myFirstStroke;
  final ShortcutTextField mySecondStroke;
  final JCheckBox mySecondStrokeEnable = new JCheckBox();
  private final ModifierDoubleClickShortcutDetector myModifierDoubleClickShortcutDetector = new ModifierDoubleClickShortcutDetector();
  private KeyboardModifierGestureShortcut myModifierGestureShortcut;
  private boolean myInternal;

  private final ItemListener myItemListener = new ItemListener() {
    @Override
    public void itemStateChanged(ItemEvent event) {
      if (myInternal) {
        return;
      }

      boolean enabled = mySecondStrokeEnable.isSelected();
      mySecondStroke.setEnabled(enabled);
      if (enabled) {
        myModifierGestureShortcut = null;
      }
      ShortcutTextField component = !enabled || null == myFirstStroke.getKeyStroke() ? myFirstStroke : mySecondStroke;
      setShortcut(newShortcut());
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(component, true));
    }
  };
  private final PropertyChangeListener myPropertyListener = new PropertyChangeListener() {
    @Override
    public void propertyChange(PropertyChangeEvent event) {
      if (myInternal) {
        return;
      }

      if (KeyboardShortcutPanel.this != event.getSource()) {
        myModifierGestureShortcut = null;
        setShortcut(newShortcut());
        if (null == myFirstStroke.getKeyStroke()) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myFirstStroke, true));
        }
        else if (null == mySecondStroke.getKeyStroke() && mySecondStrokeEnable.isSelected()) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(mySecondStroke, true));
        }
      }
      else if (event.getNewValue() instanceof KeyboardShortcut shortcut) {
        applyKeyboardShortcut(shortcut);
      }
      else if (event.getNewValue() instanceof KeyboardModifierGestureShortcut shortcut) {
        applyModifierGestureShortcut(shortcut);
      }
      else {
        clearShortcutFields();
      }
    }
  };

  KeyboardShortcutPanel(boolean isFocusTraversalKeysEnabled, LayoutManager layout) {
    super(layout);
    myFirstStroke = new ShortcutTextField(isFocusTraversalKeysEnabled);
    mySecondStroke = new ShortcutTextField(isFocusTraversalKeysEnabled);
    myFirstStroke.setKeyEventConsumer(event -> {
      KeyboardModifierGestureShortcut shortcut = myModifierDoubleClickShortcutDetector.detect(event);
      if (shortcut != null) {
        setShortcut(shortcut);
      }
    });
    addPropertyChangeListener("shortcut", myPropertyListener);
    myFirstStroke.addPropertyChangeListener("keyStroke", myPropertyListener);
    mySecondStroke.addPropertyChangeListener("keyStroke", myPropertyListener);
    mySecondStroke.setEnabled(false);
    mySecondStrokeEnable.addItemListener(myItemListener);
  }

  private Shortcut newShortcut() {
    if (myModifierGestureShortcut != null && !mySecondStrokeEnable.isSelected()) {
      return myModifierGestureShortcut;
    }

    KeyStroke key = myFirstStroke.getKeyStroke();
    return key == null ? null : new KeyboardShortcut(key, !mySecondStrokeEnable.isSelected() ? null : mySecondStroke.getKeyStroke());
  }

  private void applyKeyboardShortcut(KeyboardShortcut shortcut) {
    myInternal = true;
    try {
      myModifierGestureShortcut = null;
      myFirstStroke.setKeyStroke(shortcut.getFirstKeyStroke());
      mySecondStroke.setKeyStroke(shortcut.getSecondKeyStroke());
      mySecondStrokeEnable.setSelected(shortcut.getSecondKeyStroke() != null);
      mySecondStroke.setEnabled(mySecondStrokeEnable.isSelected());
    }
    finally {
      myInternal = false;
    }
  }

  private void applyModifierGestureShortcut(KeyboardModifierGestureShortcut shortcut) {
    myInternal = true;
    try {
      myModifierGestureShortcut = shortcut;
      myFirstStroke.setKeyStroke(null);
      myFirstStroke.setText(KeymapUtil.getShortcutText(shortcut));
      mySecondStroke.setKeyStroke(null);
      mySecondStroke.setText(null);
      mySecondStrokeEnable.setSelected(false);
      mySecondStroke.setEnabled(false);
    }
    finally {
      myInternal = false;
    }
  }

  private void clearShortcutFields() {
    myInternal = true;
    try {
      myModifierGestureShortcut = null;
      myFirstStroke.setKeyStroke(null);
      myFirstStroke.setText(null);
      mySecondStroke.setKeyStroke(null);
      mySecondStroke.setText(null);
    }
    finally {
      myInternal = false;
    }
  }

  static final class ModifierDoubleClickShortcutDetector {
    private static final int DOUBLE_CLICK_TIMEOUT_MS = 300;
    @SuppressWarnings("deprecation")
    private static final int SUPPORTED_MODIFIER_MASKS =
      InputEvent.SHIFT_MASK | InputEvent.ALT_MASK | InputEvent.CTRL_MASK | InputEvent.META_MASK;
    private static final int SUPPORTED_MODIFIER_DOWN_MASKS =
      InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK;
    private static final int SUPPORTED_MODIFIER_INPUT_MASKS = SUPPORTED_MODIFIER_MASKS | SUPPORTED_MODIFIER_DOWN_MASKS;

    private int myFirstModifierKeyCode = KeyEvent.VK_UNDEFINED;
    private int myFirstModifierModifiers;
    private boolean myFirstReleased;
    private boolean mySecondPressed;
    private long myLastEventTime;

    @SuppressWarnings("MagicConstant")
    KeyboardModifierGestureShortcut detect(KeyEvent event) {
      int id = event.getID();
      if (id != KeyEvent.KEY_PRESSED && id != KeyEvent.KEY_RELEASED) {
        // KEY_TYPED has VK_UNDEFINED and would spuriously reset the state machine mid-gesture.
        return null;
      }

      int keyCode = event.getKeyCode();
      int modifierMask = getModifierMask(keyCode);
      if (modifierMask == 0) {
        reset();
        return null;
      }

      long eventTime = event.getWhen();
      if (myFirstModifierKeyCode != KeyEvent.VK_UNDEFINED && eventTime - myLastEventTime > DOUBLE_CLICK_TIMEOUT_MS) {
        reset();
      }

      if (event.getID() == KeyEvent.KEY_PRESSED) {
        int eventModifiers = getEventModifiers(event);
        if (hasUnsupportedModifiers(eventModifiers)) {
          reset();
          return null;
        }

        // Some platforms don't report the pressed modifier in event masks for bare modifier keys.
        int modifiers = (normalizeModifiers(eventModifiers) | modifierMask) & SUPPORTED_MODIFIER_MASKS;

        if (myFirstModifierKeyCode == KeyEvent.VK_UNDEFINED) {
          start(keyCode, modifiers, eventTime);
        }
        else if (myFirstReleased && myFirstModifierKeyCode == keyCode && myFirstModifierModifiers == modifiers) {
          mySecondPressed = true;
          myLastEventTime = eventTime;
        }
        else {
          start(keyCode, modifiers, eventTime);
        }
      }
      else if (event.getID() == KeyEvent.KEY_RELEASED) {
        if (myFirstModifierKeyCode != keyCode) {
          reset();
        }
        else if (!myFirstReleased) {
          myFirstReleased = true;
          myLastEventTime = eventTime;
        }
        else if (mySecondPressed) {
          KeyStroke stroke = KeyStroke.getKeyStroke(myFirstModifierKeyCode, myFirstModifierModifiers);
          reset();
          return (KeyboardModifierGestureShortcut)KeyboardModifierGestureShortcut.newInstance(KeyboardGestureAction.ModifierType.dblClick,
                                                                                              stroke);
        }
      }
      return null;
    }

    @SuppressWarnings("deprecation")
    private static int normalizeModifiers(int modifiers) {
      if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) modifiers |= InputEvent.SHIFT_MASK;
      if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) modifiers |= InputEvent.ALT_MASK;
      if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) modifiers |= InputEvent.CTRL_MASK;
      if ((modifiers & InputEvent.META_DOWN_MASK) != 0) modifiers |= InputEvent.META_MASK;
      return modifiers;
    }

    private static boolean hasUnsupportedModifiers(int modifiers) {
      return (modifiers & ~SUPPORTED_MODIFIER_INPUT_MASKS) != 0;
    }

    @SuppressWarnings("deprecation")
    private static int getEventModifiers(KeyEvent event) {
      return event.getModifiers() | event.getModifiersEx();
    }

    @SuppressWarnings("deprecation")
    private static int getModifierMask(int keyCode) {
      return switch (keyCode) {
        case KeyEvent.VK_SHIFT -> InputEvent.SHIFT_MASK;
        case KeyEvent.VK_ALT -> InputEvent.ALT_MASK;
        case KeyEvent.VK_CONTROL -> InputEvent.CTRL_MASK;
        case KeyEvent.VK_META -> InputEvent.META_MASK;
        default -> 0;
      };
    }

    private void start(int keyCode, int modifiers, long eventTime) {
      myFirstModifierKeyCode = keyCode;
      myFirstModifierModifiers = modifiers;
      myFirstReleased = false;
      mySecondPressed = false;
      myLastEventTime = eventTime;
    }

    private void reset() {
      myFirstModifierKeyCode = KeyEvent.VK_UNDEFINED;
      myFirstModifierModifiers = 0;
      myFirstReleased = false;
      mySecondPressed = false;
      myLastEventTime = 0;
    }
  }
}
