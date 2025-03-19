// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;

@ApiStatus.Internal
public abstract class ActionProcessor {
  public @NotNull AnActionEvent createEvent(@NotNull InputEvent inputEvent,
                                            @NotNull DataContext context,
                                            @NotNull String place,
                                            @NotNull Presentation presentation,
                                            @NotNull ActionManager manager) {
    //noinspection MagicConstant
    return new AnActionEvent(context, presentation, place, ActionUiKind.NONE, inputEvent, inputEvent.getModifiersEx(), manager);
  }

  public void onUpdatePassed(@NotNull InputEvent inputEvent,
                             @NotNull AnAction action,
                             @NotNull AnActionEvent event) {
  }

  public void performAction(@NotNull InputEvent inputEvent,
                            @NotNull AnAction action,
                            @NotNull AnActionEvent event) {
    inputEvent.consume();
    ActionUtil.doPerformActionOrShowPopup(action, event, popup -> {
      popup.show(JBPopupFactory.getInstance().guessBestPopupLocation(action, event));
    });
  }
}