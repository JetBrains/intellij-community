// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.menu

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.FusInputEvent
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import java.awt.event.InputEvent

internal object MainMenuCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("main.menu", 1)

  private val FOCUSED_BY_ALT = GROUP.registerEvent("focused.by.alt")
  fun logFocusedByAlt(): Unit = FOCUSED_BY_ALT.log()

  private val OPENED_BY_SHORTCUT = GROUP.registerEvent("opened.by.shortcut",  EventFields.InputEvent)
  fun logOpenedByShortcut(inputEvent: InputEvent?, place: String?): Unit = OPENED_BY_SHORTCUT.log(FusInputEvent(inputEvent, place))

  private val OPENED_BY_MNEMONIC = GROUP.registerEvent("opened.by.mnemonic", EventFields.InputEvent)
  fun logOpenedByMnemonic(inputEvent: InputEvent?, place: String?): Unit = OPENED_BY_MNEMONIC.log(FusInputEvent(inputEvent, place))

  override fun getGroup(): EventLogGroup = GROUP
}
