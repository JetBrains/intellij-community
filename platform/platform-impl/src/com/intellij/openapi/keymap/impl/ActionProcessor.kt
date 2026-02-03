// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil.doPerformActionOrShowPopup
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.WriteIntentReadAction
import org.jetbrains.annotations.ApiStatus
import java.awt.event.InputEvent

@ApiStatus.Internal
abstract class ActionProcessor {
  open fun createEvent(
    inputEvent: InputEvent,
    context: DataContext,
    place: String,
    presentation: Presentation,
    manager: ActionManager
  ): AnActionEvent {
    return AnActionEvent(context, presentation, place, ActionUiKind.NONE, inputEvent, inputEvent.modifiersEx, manager)
  }

  open fun onUpdatePassed(inputEvent: InputEvent, action: AnAction, event: AnActionEvent) {
  }

  open fun performAction(inputEvent: InputEvent, action: AnAction, event: AnActionEvent) {
    inputEvent.consume()
    if (Utils.isLockRequired(action)) {
      WriteIntentReadAction.run {
        doPerformActionOrShowPopup(action, event, null)
      }
    } else {
      doPerformActionOrShowPopup(action, event, null)
    }
  }
}