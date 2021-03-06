// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/**
 * @author Vladimir Kondratyev
 */
final class MouseShortcutDialog extends ShortcutDialog<MouseShortcut> {
  private final JLabel myText = new JLabel("", SwingConstants.CENTER);

  MouseShortcutDialog(Component component, boolean allowDoubleClick) {
    super(component, "mouse.shortcut.dialog.title", new MouseShortcutPanel(allowDoubleClick));

    myShortcutPanel.add(BorderLayout.NORTH, new JLabel(AllIcons.General.Mouse, SwingConstants.CENTER));
    myShortcutPanel.add(BorderLayout.CENTER, myText);
    myShortcutPanel.setBorder(BorderFactory.createCompoundBorder(
      JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0),
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
      myText.setForeground(UIUtil.getContextHelpForeground());
      myText.setText(KeyMapBundle.message("dialog.mouse.pad.default.text"));
    }
    else {
      myText.setForeground(UIUtil.getLabelForeground());
      myText.setText(KeyMapBundle.message("dialog.mouse.pad.shortcut.text", KeymapUtil.getMouseShortcutText(shortcut)));
    }
  }

  @Override
  @NotNull
  Collection<String> getConflicts(MouseShortcut shortcut, String actionId, Keymap keymap) {
    return keymap.getActionIds(shortcut);
  }
}
