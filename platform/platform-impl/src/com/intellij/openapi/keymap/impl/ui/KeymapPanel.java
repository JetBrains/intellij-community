// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.keymap.impl.ShortcutRestrictions;
import com.intellij.openapi.keymap.impl.SystemShortcuts;
import com.intellij.openapi.options.BackedByPersistentState;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.awt.Component;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The Keymap settings page.
 * <p>
 * The constructor creates no Swing component, so any thread can create the page.
 * {@link #createComponent()} creates the UI, and {@link #disposeUIResources()} disposes it.
 */
public final class KeymapPanel implements SearchableConfigurable, Configurable.NoScroll, BackedByPersistentState {
  private final boolean myShowOnlyConflicts;
  private @Nullable KeymapPanelUi myUi;
  private @Nullable String actionIdToSelectAfterInit = null;
  private @Nullable String optionToShowAfterInit = null;

  public KeymapPanel() { this(false); }

  public KeymapPanel(boolean showOnlyConflicts) {
    myShowOnlyConflicts = showOnlyConflicts;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull Collection<PersistentStateComponent<?>> getBackingComponents() {
    return List.of((PersistentStateComponent<?>)KeymapManagerEx.getInstanceEx());
  }

  static void addShortcut(Keymap keymap, String actionId, Shortcut shortcut) {
    if (keymap instanceof KeymapImpl) {
      ((KeymapImpl)keymap).addShortcutFromSettings(actionId, shortcut);
    }
    else {
      keymap.addShortcut(actionId, shortcut);
    }
  }

  static void removeShortcut(Keymap keymap, String actionId, Shortcut shortcut) {
    if (keymap instanceof KeymapImpl) {
      ((KeymapImpl)keymap).removeShortcutFromSettings(actionId, shortcut);
    }
    else {
      keymap.removeShortcut(actionId, shortcut);
    }
  }

  @Override
  public Runnable enableSearch(final String option) {
    return () -> showOption(option);
  }

  public static @NotNull TreeExpander createTreeExpander(@NotNull ActionsTree actionsTree) {
    return new DefaultTreeExpander(actionsTree::getTree);
  }

  /**
   * Filters the actions tree by the option.
   * If {@link #createComponent()} did not create the UI yet, the page keeps the option and applies it in {@link #reset()}.
   */
  public void showOption(String option) {
    KeymapPanelUi ui = myUi;
    if (ui == null) {
      optionToShowAfterInit = option;
    }
    else {
      ui.showOption(option);
    }
  }

  public static void addKeyboardShortcut(@NotNull String actionId,
                                         @NotNull ShortcutRestrictions restrictions,
                                         @NotNull Keymap keymapSelected,
                                         @NotNull Component parent,
                                         QuickList @NotNull ... quickLists) {
    addKeyboardShortcut(actionId, restrictions, keymapSelected, parent, null, null, quickLists);
  }

  public static void addKeyboardShortcut(@NotNull String actionId,
                                         @NotNull ShortcutRestrictions restrictions,
                                         @NotNull Keymap keymapSelected,
                                         @NotNull Component parent,
                                         @Nullable KeyboardShortcut selectedShortcut,
                                         @Nullable SystemShortcuts systemShortcuts,
                                         QuickList @NotNull ... quickLists) {
    if (!restrictions.allowKeyboardShortcut) return;
    KeyboardShortcutDialog dialog = new KeyboardShortcutDialog(parent, restrictions.allowKeyboardSecondStroke, systemShortcuts == null ? null : systemShortcuts.createKeystroke2SysShortcutMap());
    Shortcut shortcut = dialog.showAndGet(actionId, keymapSelected, selectedShortcut, quickLists);
    if (shortcut == null) return;

    SafeKeymapAccessor accessor = new SafeKeymapAccessor(parent, keymapSelected);
    if (dialog.hasConflicts()) {
      int result = showConfirmationDialog(parent);
      if (result == Messages.YES) {
        removeConflictingShortcuts(accessor.keymap(), actionId, shortcut);
      }
      else if (result != Messages.NO) {
        return;
      }
    }
    if (systemShortcuts != null && shortcut instanceof KeyboardShortcut) { // check conflicts with system shortcuts
      final Keymap keymap = accessor.keymap();
      final Map<KeyboardShortcut, String> kscs = systemShortcuts.calculateConflicts(keymap, actionId);
      if (kscs != null && !kscs.isEmpty()) {
        for (KeyboardShortcut ksc : kscs.keySet()) {
          String keyDesc = StringUtil.notNullize(KeymapUtil.getKeystrokeText(ksc.getFirstKeyStroke()));
          if (ksc.getSecondKeyStroke() != null)
            keyDesc += ", " + KeymapUtil.getKeystrokeText(ksc.getSecondKeyStroke());
          final int result = Messages.showYesNoCancelDialog(
            parent,
            IdeBundle.message("message.action.remove.system.assigned.shortcut", keyDesc, kscs.get(ksc)),
            KeyMapBundle.message("conflict.shortcut.dialog.title"),
            KeyMapBundle.message("conflict.shortcut.dialog.remove.button"),
            KeyMapBundle.message("conflict.shortcut.dialog.keep.button"),
            KeyMapBundle.message("conflict.shortcut.dialog.cancel.button"),
            Messages.getWarningIcon());
          if (result == Messages.YES) {
            keymap.removeShortcut(actionId, ksc);
          }
        }
      }
    }
    accessor.add(actionId, shortcut);
    if (systemShortcuts != null)
      systemShortcuts.updateKeymapConflicts(accessor.keymap());
  }

  static boolean isShortcutConflictAction(@NotNull String actionId, @NotNull String conflictActionId) {
    if (conflictActionId.equals(actionId)) {
      return false;
    }
    if (actionId.startsWith("Editor") && conflictActionId.equals("$" + actionId.substring(6))) {
      return false;
    }
    String useShortcutOf = ActionManagerEx.getInstanceEx().getActionBinding(conflictActionId);
    return !actionId.equals(useShortcutOf);
  }

  static void removeConflictingShortcuts(@NotNull Keymap keymap, @NotNull String actionId, @NotNull Shortcut shortcut) {
    if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
      Map<String, List<KeyboardShortcut>> conflicts = keymap.getConflicts(actionId, keyboardShortcut);
      for (String id : conflicts.keySet()) {
        for (KeyboardShortcut s : conflicts.get(id)) {
          keymap.removeShortcut(id, s);
        }
      }
    }
    else if (shortcut instanceof KeyboardModifierGestureShortcut gestureShortcut) {
      for (String id : keymap.getActionIdList(gestureShortcut)) {
        if (isShortcutConflictAction(actionId, id)) {
          for (Shortcut existingShortcut : keymap.getShortcuts(id)) {
            if (existingShortcut.equals(gestureShortcut)) {
              keymap.removeShortcut(id, existingShortcut);
            }
          }
        }
      }
    }
  }

  static void addMouseShortcut(@NotNull String actionId,
                               @NotNull ShortcutRestrictions restrictions,
                               @NotNull Keymap keymapSelected,
                               @NotNull Component parent,
                               QuickList @NotNull ... quickLists) {
    if (!restrictions.allowMouseShortcut) return;
    MouseShortcutDialog dialog = new MouseShortcutDialog(parent, restrictions.allowMouseDoubleClick);
    MouseShortcut mouseShortcut = dialog.showAndGet(actionId, keymapSelected, quickLists);
    if (mouseShortcut == null) return;

    SafeKeymapAccessor accessor = new SafeKeymapAccessor(parent, keymapSelected);
    if (dialog.hasConflicts()) {
      int result = showConfirmationDialog(parent);
      if (result == Messages.YES) {
        Keymap keymap = accessor.keymap();
        for (String id : keymap.getActionIds(mouseShortcut)) {
          keymap.removeShortcut(id, mouseShortcut);
        }
      }
      else if (result != Messages.NO) {
        return;
      }
    }
    accessor.add(actionId, mouseShortcut);
  }

  @Override
  public @NotNull String getId() {
    return "preferences.keymap";
  }

  @Override
  public void reset() {
    KeymapPanelUi ui = myUi;
    if (ui == null) return;
    ui.reset();

    // Reset is called after the KeymapPanel is chosen in the Settings.
    String option = optionToShowAfterInit;
    if (option != null) {
      optionToShowAfterInit = null;
      ui.showOption(option);
    }
    String actionId = actionIdToSelectAfterInit;
    if (actionId != null) {
      actionIdToSelectAfterInit = null;
      selectAction(actionId);
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    KeymapPanelUi ui = myUi;
    if (ui != null) {
      ui.apply();
    }
  }

  @Override
  public boolean isModified() {
    KeymapPanelUi ui = myUi;
    return ui != null && ui.isModified();
  }

  /**
   * Navigates the actions tree to make the node of the specified action visible and selected.
   * If KeymapPanel is not initialized yet, the action will be remembered and selected once initialization is complete.
   */
  public void selectAction(String actionId) {
    KeymapPanelUi ui = myUi;
    if (ui == null || !ui.selectAction(actionId)) {
      // KeymapPanel was not initialized yet.
      // Let's add a callback to select the action once initialization is complete.
      actionIdToSelectAfterInit = actionId;
    }
  }

  @Override
  public @Nls String getDisplayName() {
    return KeyMapBundle.message("keymap.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "preferences.keymap";
  }

  @Override
  public JComponent createComponent() {
    KeymapPanelUi ui = myUi;
    if (ui == null) {
      ui = new KeymapPanelUi(myShowOnlyConflicts);
      myUi = ui;
    }
    return ui;
  }

  @Override
  public void disposeUIResources() {
    KeymapPanelUi ui = myUi;
    if (ui != null) {
      myUi = null;
      Disposer.dispose(ui);
    }
  }

  /**
   * @return the shortcuts of the action in the keymap that the page shows,
   * or {@code null} if the page did not load the keymaps yet
   */
  public Shortcut @Nullable [] getCurrentShortcuts(@NotNull String actionId) {
    KeymapPanelUi ui = myUi;
    return ui == null ? null : ui.getCurrentShortcuts(actionId);
  }

  private static int showConfirmationDialog(Component parent) {
    return Messages.showYesNoCancelDialog(
      parent,
      KeyMapBundle.message("conflict.shortcut.dialog.message"),
      KeyMapBundle.message("conflict.shortcut.dialog.title"),
      KeyMapBundle.message("conflict.shortcut.dialog.remove.button"),
      KeyMapBundle.message("conflict.shortcut.dialog.keep.button"),
      KeyMapBundle.message("conflict.shortcut.dialog.cancel.button"),
      Messages.getWarningIcon());
  }

  private static final class SafeKeymapAccessor {
    private final Component parent;
    private final Keymap selected;
    private KeymapSchemeManager manager;
    private Keymap mutable;

    SafeKeymapAccessor(@NotNull Component parent, @NotNull Keymap selected) {
      this.parent = parent;
      this.selected = selected;
    }

    Keymap keymap() {
      if (mutable == null) {
        if (parent instanceof KeymapPanelUi panel) {
          mutable = panel.getManager().getMutableKeymap(selected);
        }
        else {
          if (manager == null) {
            manager = new KeymapSelector(selectedKeymap -> { }).getManager();
            manager.reset();
          }
          mutable = manager.getMutableKeymap(selected);
        }
      }
      return mutable;
    }

    void add(@NotNull String actionId, @NotNull Shortcut newShortcut) {
      Keymap keymap = keymap();
      Shortcut[] shortcuts = keymap.getShortcuts(actionId);
      for (Shortcut shortcut : shortcuts) {
        if (shortcut.equals(newShortcut)) {
          // if shortcut is already registered to this action, just select it
          if (manager != null) manager.apply();
          return;
        }
      }
      addShortcut(keymap, actionId, newShortcut);
      if (StringUtil.startsWithChar(actionId, '$')) {
        addShortcut(keymap, KeyMapBundle.message("editor.shortcut", actionId.substring(1)), newShortcut);
      }
      if (manager != null) manager.apply();
    }
  }
}
