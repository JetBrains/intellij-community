// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.events

import com.intellij.openapi.actionSystem.AnActionEvent
import java.awt.EventQueue
import java.awt.event.ActionEvent
import java.awt.event.InputEvent

data class FusInputEvent(val inputEvent: InputEvent?, val place: String?) {
  companion object {
    @JvmStatic
    fun from(actionEvent: AnActionEvent?): FusInputEvent? = actionEvent?.let { FusInputEvent(it.inputEvent, it.place) }

    @JvmStatic
    fun from(actionEvent: ActionEvent?): FusInputEvent? {
      return actionEvent?.let { ae ->
        val currentEvent = EventQueue.getCurrentEvent()
        val inputEvent = if (currentEvent.source == ae.source) currentEvent as? InputEvent else null
        inputEvent?.let { ie -> FusInputEvent(ie, null) }
      }
    }
  }
}