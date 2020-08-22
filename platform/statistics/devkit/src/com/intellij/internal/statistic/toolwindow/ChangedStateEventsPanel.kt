// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.toolwindow

import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer

internal class ChangedStateEventsPanel(val project: Project,
                                       parentDisposable: Disposable,
                                       difference: Collection<LogEvent>,
                                       recorderId: String)
  : SimpleToolWindowPanel(false, true) {
  private val consoleLog = StatisticsEventLogConsole(project, StatisticsLogFilterModel(), recorderId)

  init {
    setContent(consoleLog.component)
    val messageBuilder = StatisticsEventLogMessageBuilder()
    for (logEvent in difference) {
      consoleLog.addLogLine(messageBuilder.buildLogMessage(logEvent))
    }
    Disposer.register(parentDisposable, consoleLog)
  }
}