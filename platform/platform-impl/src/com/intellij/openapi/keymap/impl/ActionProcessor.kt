// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil.doPerformActionOrShowPopup
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
    doPerformActionOrShowPopup(action, event, null)
  }
}