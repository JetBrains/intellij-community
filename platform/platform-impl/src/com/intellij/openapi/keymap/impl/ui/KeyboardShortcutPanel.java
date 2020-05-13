// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.wm.IdeFocusManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

final class KeyboardShortcutPanel extends ShortcutPanel<KeyboardShortcut> {
  final ShortcutTextField myFirstStroke;
  final ShortcutTextField mySecondStroke;
  final JCheckBox mySecondStrokeEnable = new JCheckBox();

  private final ItemListener myItemListener = new ItemListener() {
    @Override
    public void itemStateChanged(ItemEvent event) {
      boolean enabled = mySecondStrokeEnable.isSelected();
      mySecondStroke.setEnabled(enabled);
      ShortcutTextField component = !enabled || null == myFirstStroke.getKeyStroke() ? myFirstStroke : mySecondStroke;
      setShortcut(newShortcut());
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(component, true));
    }
  };
  private final PropertyChangeListener myPropertyListener = new PropertyChangeListener() {
    @Override
    public void propertyChange(PropertyChangeEvent event) {
      if (KeyboardShortcutPanel.this != event.getSource()) {
        setShortcut(newShortcut());
        if (null == myFirstStroke.getKeyStroke()) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myFirstStroke, true));
        }
        else if (null == mySecondStroke.getKeyStroke() && mySecondStrokeEnable.isSelected()) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(mySecondStroke, true));
        }
      }
      else if (event.getNewValue() instanceof KeyboardShortcut) {
        KeyboardShortcut shortcut = (KeyboardShortcut)event.getNewValue();
        myFirstStroke.setKeyStroke(shortcut.getFirstKeyStroke());
        mySecondStroke.setKeyStroke(shortcut.getSecondKeyStroke());
      }
      else {
        myFirstStroke.setKeyStroke(null);
        mySecondStroke.setKeyStroke(null);
      }
    }
  };

  KeyboardShortcutPanel(boolean isFocusTraversalKeysEnabled, LayoutManager layout) {
    super(layout);
    myFirstStroke = new ShortcutTextField(isFocusTraversalKeysEnabled);
    mySecondStroke = new ShortcutTextField(isFocusTraversalKeysEnabled);
    addPropertyChangeListener("shortcut", myPropertyListener);
    myFirstStroke.addPropertyChangeListener("keyStroke", myPropertyListener);
    mySecondStroke.addPropertyChangeListener("keyStroke", myPropertyListener);
    mySecondStroke.setEnabled(false);
    mySecondStrokeEnable.addItemListener(myItemListener);
  }

  private KeyboardShortcut newShortcut() {
    KeyStroke key = myFirstStroke.getKeyStroke();
    return key == null ? null : new KeyboardShortcut(key, !mySecondStrokeEnable.isSelected() ? null : mySecondStroke.getKeyStroke());
  }
}
