// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static java.awt.event.ComponentEvent.COMPONENT_FIRST;

final class TBItemAnActionButton extends TBItemButton {
  private @NotNull AnAction myAnAction;
  private @Nullable Component myComponent;

  TBItemAnActionButton(@Nullable ItemListener listener, @NotNull AnAction action, @Nullable TouchBarStats.AnActionStats stats) {
    super(listener, stats);
    myAnAction = action;
    setAction(this::_performAction, true);

    if (action instanceof Toggleable) {
      myFlags |= NSTLibrary.BUTTON_FLAG_TOGGLE;
    }
  }

  @Override
  public String toString() { return String.format("%s [%s]", ActionManager.getInstance().getId(myAnAction), getUid()); }

  void setComponent(@Nullable Component component/*for DataCtx*/) { myComponent = component; }

  @NotNull AnAction getAnAction() { return myAnAction; }

  void setAnAction(@NotNull AnAction newAction) { myAnAction = newAction; }

  private void _performAction() {
    final ActionManagerEx actionManagerEx = ActionManagerEx.getInstanceEx();
    final Component src = myComponent != null ? myComponent : Helpers.getCurrentFocusComponent();
    if (src == null) // KeyEvent can't have null source object
    {
      return;
    }

    final InputEvent ie = new KeyEvent(src, COMPONENT_FIRST, System.currentTimeMillis(), 0, 0, '\0');
    actionManagerEx.tryToExecute(myAnAction, ie, src, ActionPlaces.TOUCHBAR_GENERAL, true);

    // to update 'selected'-state after action has been performed
    if (myAnAction instanceof Toggleable) {
      updateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
    }
  }
}
