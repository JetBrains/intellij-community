// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

@ApiStatus.Internal
public abstract class ActionProcessor {
  public @NotNull AnActionEvent createEvent(@NotNull InputEvent inputEvent,
                                            @NotNull DataContext context,
                                            @NotNull String place,
                                            @NotNull Presentation presentation,
                                            @NotNull ActionManager manager) {
    return new AnActionEvent(inputEvent, context, place, presentation, manager, inputEvent.getModifiersEx());
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
      if (inputEvent instanceof MouseEvent) {
        popup.show(new RelativePoint((MouseEvent)inputEvent));
      }
      else {
        popup.showInBestPositionFor(event.getDataContext());
      }
    });
  }
}