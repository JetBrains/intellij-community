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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Arrays;
import java.util.Collection;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * @author Vladimir Kondratyev
 * @author Sergey.Malenkov
 */
final class MouseShortcutDialog extends ShortcutDialog<MouseShortcut> {
  private final JLabel myText = new JLabel("", SwingConstants.CENTER);

  MouseShortcutDialog(Component component, boolean allowDoubleClick) {
    super(component, "mouse.shortcut.dialog.title", new MouseShortcutPanel(allowDoubleClick));

    myShortcutPanel.add(BorderLayout.NORTH, new JLabel(AllIcons.General.Mouse, SwingConstants.CENTER));
    myShortcutPanel.add(BorderLayout.CENTER, myText);
    myShortcutPanel.setBorder(BorderFactory.createCompoundBorder(
      JBUI.Borders.customLine(MouseShortcutPanel.BORDER, 1, 0, 1, 0),
      JBUI.Borders.empty(20)
    ));

    init();
  }

  @Override
  protected String getHelpId() {
    return "preferences.mouse.shortcut";
  }

  @Override
  MouseShortcut toShortcut(Object value) {
    return value instanceof MouseShortcut ? (MouseShortcut)value : null;
  }

  @Override
  void setShortcut(MouseShortcut shortcut) {
    super.setShortcut(shortcut);
    if (shortcut == null) {
      myText.setForeground(MouseShortcutPanel.FOREGROUND);
      myText.setText(KeyMapBundle.message("dialog.mouse.pad.default.text"));
    }
    else {
      myText.setForeground(UIUtil.getLabelForeground());
      myText.setText(KeyMapBundle.message("dialog.mouse.pad.shortcut.text", KeymapUtil.getMouseShortcutText(
        shortcut.getButton(),
        shortcut.getModifiers(),
        shortcut.getClickCount())));
    }
  }

  @Override
  Collection<String> getConflicts(MouseShortcut shortcut, String actionId, Keymap keymap) {
    return Arrays.asList(keymap.getActionIds(shortcut));
  }
}
