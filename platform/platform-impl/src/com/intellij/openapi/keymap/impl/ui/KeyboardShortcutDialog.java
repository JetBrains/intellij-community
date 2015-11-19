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
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.util.ui.JBUI;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Collection;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * @author Sergey.Malenkov
 */
final class KeyboardShortcutDialog extends ShortcutDialog<KeyboardShortcut> {
  private final JComponent myPreferredFocusedComponent;

  KeyboardShortcutDialog(Component parent) {
    super(parent, "keyboard.shortcut.dialog.title", new KeyboardShortcutPanel(new BorderLayout()));

    KeyboardShortcutPanel panel = (KeyboardShortcutPanel)myShortcutPanel;
    myPreferredFocusedComponent = panel.myFirstStroke;

    JPanel inner = new JPanel(new BorderLayout());
    inner.add(BorderLayout.CENTER, panel.mySecondStroke);
    inner.add(BorderLayout.WEST, panel.mySecondStrokeEnable);
    inner.setBorder(JBUI.Borders.empty(5, 0, 0, 0));
    panel.add(BorderLayout.NORTH, panel.myFirstStroke);
    panel.add(BorderLayout.SOUTH, inner);
    panel.setBorder(JBUI.Borders.empty(0, 10));
    panel.mySecondStrokeEnable.setText(KeyMapBundle.message("dialog.enable.second.stroke.checkbox"));

    init();
  }

  @Override
  protected String getHelpId() {
    return "preferences.keymap.shortcut";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPreferredFocusedComponent;
  }

  @Override
  KeyboardShortcut toShortcut(Object value) {
    return value instanceof KeyboardShortcut ? (KeyboardShortcut)value : null;
  }

  @Override
  Collection<String> getConflicts(KeyboardShortcut shortcut, String actionId, Keymap keymap) {
    return keymap.getConflicts(actionId, shortcut).keySet();
  }
}
