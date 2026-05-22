// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class KeyboardShortcutDialog extends ShortcutDialog<Shortcut> {
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
  Shortcut toShortcut(Object value) {
    return value instanceof KeyboardShortcut || value instanceof KeyboardModifierGestureShortcut ? (Shortcut)value : null;
  }

  @Override
  @NotNull Collection<String> getConflicts(Shortcut shortcut, String actionId, Keymap keymap) {
    if (shortcut instanceof KeyboardModifierGestureShortcut gestureShortcut) {
      return getKeymapConflicts(gestureShortcut, actionId, keymap);
    }
    if (!(shortcut instanceof KeyboardShortcut keyboardShortcut)) {
      return Collections.emptyList();
    }

    String sysAct = getSystemShortcutAction(keyboardShortcut.getFirstKeyStroke());
    Collection<String> keymapConflicts = keymap.getConflicts(actionId, keyboardShortcut).keySet();
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

  static @NotNull Collection<String> getKeymapConflicts(@NotNull KeyboardModifierGestureShortcut shortcut,
                                                        @NotNull String actionId,
                                                        @NotNull Keymap keymap) {
    List<String> result = new ArrayList<>();
    for (String id : keymap.getActionIdList(shortcut)) {
      if (KeymapPanel.isShortcutConflictAction(actionId, id)) {
        result.add(id);
      }
    }
    return result;
  }

  private @Nullable String getSystemShortcutAction(@NotNull KeyStroke keyStroke) {
    return mySystemShortcuts == null ? null : mySystemShortcuts.get(keyStroke);
  }

  @Override
  protected void addSystemActionsIfPresented(Group group) {
    if (mySystemShortcuts != null) {
      @SuppressWarnings("DialogTitleCapitalization") Group macOsSysGroup = new Group(IdeBundle.message("action.group.macos.shortcuts.text"), null, () -> AllIcons.Nodes.KeymapOther);
      mySystemShortcuts.values().forEach(macOsSysGroup::addActionId);
      group.addGroup(macOsSysGroup);
    }
  }
}
