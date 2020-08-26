// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.events

import com.intellij.openapi.actionSystem.AnActionEvent
import java.awt.event.InputEvent

data class FusInputEvent(val inputEvent: InputEvent?, val place: String?) {
  companion object {
    @JvmStatic
    fun from(actionEvent: AnActionEvent?): FusInputEvent? = actionEvent?.let { FusInputEvent(it.inputEvent, it.place) }
  }
}