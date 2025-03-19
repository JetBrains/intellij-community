// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

final class FNKeyAction extends DumbAwareAction {
  private static final boolean SHOW_ACTION_TEMPLATE_TEXT = Boolean.getBoolean("touchbar.fn.mode.show.template");

  private final int myFN;
  private final Map<Integer, String[]> myCache = new HashMap<>();

  private AnAction myAction; // particular action (from keymap for given modifiers) calculated in last update
  private boolean myIsActionDisabled;

  private String @Nullable[] getActionsIds(int modifiers) {
    final KeymapManager manager = KeymapManager.getInstance();
    if (manager == null)
      return null;

    final @NotNull Keymap keymap = manager.getActiveKeymap();

    String[] result = myCache.get(modifiers);
    if (result != null) {
      return result;
    }
    KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F1 + myFN - 1, modifiers);
    result = keymap.getActionIds(keyStroke);
    myCache.put(modifiers, result);
    return result;
  }

  FNKeyAction(int FN) {
    myFN = Math.max(1, Math.min(FN, 12));

    // TODO: clear cache when keymap changes (or FN-shortcut changes)
    // KeymapManagerEx.getInstanceEx().addWeakListener(new MyKeymapManagerListener);
  }

  int getFN() { return myFN; }

  boolean isActionDisabled() { return myIsActionDisabled; }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (myAction == null || myIsActionDisabled) {
      Helpers.emulateKeyPress(KeyEvent.VK_F1 + myFN - 1);
      return;
    }
    myAction.actionPerformed(e);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(true); // FN-keys are always enabled and visible
    e.getPresentation().setText("");
    myIsActionDisabled = false;
    myAction = null;

    final String[] ids = getActionsIds(TouchBarsManager.getLastModifiersEx());
    if (ids == null || ids.length < 1) {
      return;
    }

    int c = 0;
    myAction = e.getActionManager().getAction(ids[c]);
    while (myAction == null && c + 1 < ids.length) {
      ++c;
      e.getActionManager().getAction(ids[c]);
    }

    if (myAction == null) {
      return;
    }

    myAction.update(e);
    myIsActionDisabled = !e.getPresentation().isEnabled();
    e.getPresentation().setEnabledAndVisible(true); // FN-keys are always enabled and visible

    final String text = e.getPresentation().getText();
    if (SHOW_ACTION_TEMPLATE_TEXT || text == null || text.isEmpty()) {
      // replace with template presentation text
      e.getPresentation().setText(myAction.getTemplateText());
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return myAction == null ? ActionUpdateThread.BGT : myAction.getActionUpdateThread();
  }
}
