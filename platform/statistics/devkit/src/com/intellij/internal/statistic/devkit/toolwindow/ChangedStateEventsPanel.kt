// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.jetbrains.fus.reporting.model.lion3.LogEvent

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
      consoleLog.addLogLine(messageBuilder.buildLogMessage(logEvent, null, null))
    }
    Disposer.register(parentDisposable, consoleLog)
  }
}