// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.application.options.schemes.SchemeNameGenerator;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class CtrlYActionChooser {
  private static final String ASK_ABOUT_SHORTCUT = "ask.about.ctrl.y.shortcut";

  @Nullable
  private static Keymap getCurrentKeymap() {
    KeymapManager keymapManager = KeymapManager.getInstance();
    return keymapManager == null ? null : keymapManager.getActiveKeymap();
  }

  @NotNull
  private static Keymap getRootKeymap(@NotNull Keymap keymap) {
    while (keymap.canModify()) {
      Keymap parent = keymap.getParent();
      if (parent == null) {
        break;
      }
      else {
        keymap = parent;
      }
    }
    return keymap;
  }

  private static boolean isCtrlY(AWTEvent event) {
    if (!(event instanceof KeyEvent)) return false;
    KeyEvent keyEvent = (KeyEvent)event;
    int modifiers = keyEvent.getModifiers();
    return (keyEvent.getKeyCode() == KeyEvent.VK_Y &&
            ((modifiers & InputEvent.CTRL_MASK) != 0) &&
            ((modifiers & InputEvent.SHIFT_MASK) == 0) &&
            ((modifiers & InputEvent.ALT_GRAPH_MASK) == 0) &&
            ((modifiers & InputEvent.ALT_MASK) == 0) &&
            ((modifiers & InputEvent.META_MASK) == 0));
  }

  private static void patchKeymap(@NotNull Keymap currentKeymap) {
    KeymapManager keymapManager = KeymapManager.getInstance();
    assert keymapManager instanceof KeymapManagerEx;
    Keymap[] allKeymaps = ((KeymapManagerEx)keymapManager).getAllKeymaps();
    String name = SchemeNameGenerator.getUniqueName(KeyMapBundle.message("keymap.with.patched.redo.name",
                                                                         currentKeymap.getPresentableName()),
                                                    n -> ContainerUtil.exists(allKeymaps, t -> n.equals(t.getName()) ||
                                                                                               n.equals(t.getPresentableName())));
    Keymap newKeymap = currentKeymap.deriveKeymap(name);
    KeyboardShortcut shortcut = KeyboardShortcut.fromString("control Y");
    newKeymap.removeShortcut(IdeActions.ACTION_EDITOR_DELETE_LINE, shortcut);
    newKeymap.addShortcut(IdeActions.ACTION_REDO, shortcut);
    ((KeymapManagerEx)keymapManager).getSchemeManager().addScheme(newKeymap);
    ((KeymapManagerEx)keymapManager).setActiveKeymap(newKeymap);
  }

  private static void invokeRedo(DataContext dataContext) {
    AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_REDO);
    if (action != null) ActionUtil.invokeAction(action, dataContext, ActionPlaces.MAIN_MENU, null, null);
  }

  public static boolean isCurrentShortcutOk(DataContext dataContext) {
    if (!PropertiesComponent.getInstance().isValueSet(ASK_ABOUT_SHORTCUT)) return true;

    Keymap keymap = getCurrentKeymap();
    if (keymap == null) return true;
    Keymap rootKeymap = getRootKeymap(keymap);
    if (!KeymapManager.DEFAULT_IDEA_KEYMAP.equals(rootKeymap.getName())) return true;

    AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();
    if (!isCtrlY(event)) return true;

    int savedCount = IdeEventQueue.getInstance().getEventCount();
    int choice = Messages.showDialog(KeyMapBundle.message("keymap.patch.dialog.message"), KeyMapBundle.message("keymap.patch.dialog.title"),
                                     new String[]{
                                       KeyMapBundle.message("keymap.patch.dialog.redo.option"),
                                       KeyMapBundle.message("keymap.patch.dialog.delete.line.option"),
                                       KeyMapBundle.message("keymap.patch.dialog.cancel.option")
                                     }, -1, Messages.getInformationIcon(), null);
    IdeEventQueue.getInstance().setEventCount(savedCount); // keep data context valid after showing modal dialog

    if (choice < 0 || choice > 1) return false;
    PropertiesComponent.getInstance().unsetValue(ASK_ABOUT_SHORTCUT);
    if (choice == 1) return true;
    patchKeymap(keymap);
    invokeRedo(dataContext);
    return false;
  }

  public static void askAboutShortcut() {
    PropertiesComponent.getInstance().setValue(ASK_ABOUT_SHORTCUT, true);
  }
}
