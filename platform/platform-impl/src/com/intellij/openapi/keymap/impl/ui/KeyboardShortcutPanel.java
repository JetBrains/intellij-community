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

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;

import java.awt.LayoutManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * @author Sergey.Malenkov
 */
final class KeyboardShortcutPanel extends JPanel {
  final ShortcutTextField myFirstStroke = new ShortcutTextField();
  final ShortcutTextField mySecondStroke = new ShortcutTextField();
  final JCheckBox mySecondStrokeEnable = new JCheckBox();

  private KeyboardShortcut myShortcut;

  private final ChangeListener myChangeListener = new ChangeListener() {
    @Override
    public void stateChanged(ChangeEvent event) {
      boolean enabled = mySecondStrokeEnable.isSelected();
      mySecondStroke.setEnabled(enabled);
      ShortcutTextField component = !enabled || null == myFirstStroke.getKeyStroke() ? myFirstStroke : mySecondStroke;
      setShortcut(newShortcut());
      component.requestFocus();
    }
  };
  private final PropertyChangeListener myPropertyListener = new PropertyChangeListener() {
    @Override
    public void propertyChange(PropertyChangeEvent event) {
      setShortcut(newShortcut());
    }
  };

  KeyboardShortcutPanel(LayoutManager layout) {
    super(layout);
    myFirstStroke.addPropertyChangeListener("keyStroke", myPropertyListener);
    mySecondStroke.addPropertyChangeListener("keyStroke", myPropertyListener);
    mySecondStrokeEnable.addChangeListener(myChangeListener);
  }

  KeyboardShortcut getShortcut() {
    return myShortcut;
  }

  void setShortcut(KeyboardShortcut shortcut) {
    Shortcut old = myShortcut;
    if (old != null || shortcut != null) {
      myShortcut = shortcut;
      myFirstStroke.setKeyStroke(shortcut == null ? null : shortcut.getFirstKeyStroke());
      mySecondStroke.setKeyStroke(shortcut == null ? null : shortcut.getSecondKeyStroke());
      firePropertyChange("shortcut", old, shortcut);
    }
  }

  private KeyboardShortcut newShortcut() {
    KeyStroke key = myFirstStroke.getKeyStroke();
    return key == null ? null : new KeyboardShortcut(key, !mySecondStrokeEnable.isSelected() ? null : mySecondStroke.getKeyStroke());
  }
}
