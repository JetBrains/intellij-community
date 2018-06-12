// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static java.awt.event.ComponentEvent.COMPONENT_FIRST;

public class PlatformAction implements NSTLibrary.Action {
  final AnAction myAction;
  final String myActionId;

  public PlatformAction(@NotNull String actionId) {
    final ActionManagerEx actionManagerEx = ActionManagerEx.getInstanceEx();
    myAction = ActionManager.getInstance().getAction(actionId);
    if (myAction == null) {
      TouchBar.LOG.error("can't find action by id '%s'", actionId);
    }
    myActionId = actionId;
  }

  public PlatformAction(@NotNull AnAction act) {
    myAction = act;
    myActionId = ActionManager.getInstance().getId(myAction);
  }

  @Override
  public void execute() {
    ApplicationManager.getApplication().invokeLater(() -> _performAction());
  }

  private void _performAction() {
    final ActionManagerEx actionManagerEx = ActionManagerEx.getInstanceEx();
    final KeyboardFocusManager focusManager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
    final Component focusOwner = focusManager.getFocusedWindow();

    final InputEvent ie = new KeyEvent(focusOwner, COMPONENT_FIRST, System.currentTimeMillis(), 0, 0, '\0');
    actionManagerEx.tryToExecute(myAction, ie, focusOwner, ActionPlaces.UNKNOWN, false);
  }
}
