/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

@ApiStatus.Internal
public abstract class ActionProcessor {
  @NotNull
  public AnActionEvent createEvent(@NotNull InputEvent inputEvent,
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

    if (action instanceof ActionGroup && !event.getPresentation().isPerformGroup()) {
      DataContext ctx = event.getDataContext();
      ActionGroup group = (ActionGroup)action;
      String groupId = ActionManager.getInstance().getId(action);
      ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
        group.getTemplatePresentation().getText(), group, ctx,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        false, null, -1, null, ActionPlaces.getActionGroupPopupPlace(groupId));
      if (inputEvent instanceof MouseEvent) {
        popup.show(new RelativePoint((MouseEvent)inputEvent));
      }
      else {
        popup.showInBestPositionFor(ctx);
      }
    }
    else {
      action.actionPerformed(event);
    }
  }
}