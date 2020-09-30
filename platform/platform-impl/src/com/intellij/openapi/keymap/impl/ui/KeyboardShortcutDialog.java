// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

final class KeyboardShortcutDialog extends ShortcutDialog<KeyboardShortcut> {
  private final JComponent myPreferredFocusedComponent;
  private final @Nullable Map<KeyStroke, String> mySystemShortcuts;

  KeyboardShortcutDialog(Component parent, boolean allowSecondStroke, @Nullable Map<KeyStroke, String> systemShortcuts) {
    super(parent, "keyboard.shortcut.dialog.title", new KeyboardShortcutPanel(true, new BorderLayout()));

    KeyboardShortcutPanel panel = (KeyboardShortcutPanel)myShortcutPanel;
    myPreferredFocusedComponent = panel.myFirstStroke;
    mySystemShortcuts = systemShortcuts;

    JPanel inner = new JPanel(new BorderLayout());
    inner.add(BorderLayout.CENTER, panel.mySecondStroke);
    inner.add(BorderLayout.WEST, panel.mySecondStrokeEnable);
    inner.setBorder(JBUI.Borders.emptyTop(5));
    inner.setVisible(allowSecondStroke);
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
  @NotNull Collection<String> getConflicts(KeyboardShortcut shortcut, String actionId, Keymap keymap) {
    String sysAct = getSystemShortcutAction(shortcut.getFirstKeyStroke());
    Collection<String> keymapConflicts = keymap.getConflicts(actionId, shortcut).keySet();
    if (sysAct == null) {
      return keymapConflicts;
    }
    if (keymapConflicts.isEmpty()) {
      return Collections.singletonList(sysAct);
    }
    List<String> result = new ArrayList<>(keymapConflicts);
    result.add(sysAct);
    return result;
  }

  @Override
  protected void addSystemActionsIfPresented(Group group) {
    if (mySystemShortcuts != null) {
      @SuppressWarnings("DialogTitleCapitalization") Group macOsSysGroup = new Group(IdeBundle.message("action.group.macos.shortcuts.text"), AllIcons.Nodes.KeymapOther);
      mySystemShortcuts.forEach((ks, id) -> macOsSysGroup.addActionId(id));
      group.addGroup(macOsSysGroup);
    }
  }

  private @Nullable String getSystemShortcutAction(@NotNull KeyStroke keyStroke) {
    return mySystemShortcuts == null ? null : mySystemShortcuts.get(keyStroke);
  }
}
