// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.toolwindow

import com.intellij.diagnostic.logging.LogConsoleBase
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.actions.*
import com.intellij.internal.statistic.actions.scheme.AddGroupToTestSchemeAction
import com.intellij.internal.statistic.actions.scheme.EditEventsTestSchemeAction
import com.intellij.internal.statistic.eventLog.EventLogNotificationService
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.FilterComponent
import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

const val eventLogToolWindowsId = "Statistics Event Log"

internal class StatisticsEventLogToolWindow(project: Project, private val recorderId: String) : SimpleToolWindowPanel(false, true), Disposable {
  private val consoleLog: StatisticsEventLogConsole
  private val messageBuilder = StatisticsEventLogMessageBuilder()
  private val eventLogListener: (LogEvent) -> Unit

  init {
    val model = StatisticsLogFilterModel()
    consoleLog = StatisticsEventLogConsole(project, model)
    eventLogListener = { logEvent -> consoleLog.addLogLine(messageBuilder.buildLogMessage(logEvent)) }

    val topPanel = JPanel(FlowLayout(FlowLayout.LEFT))
    topPanel.add(createFilter(project, model))
    topPanel.add(createActionToolbar())
    topPanel.border = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border())
    add(topPanel, BorderLayout.NORTH)

    setContent(consoleLog.component)

    toolbar = ActionManager.getInstance().createActionToolbar("FusEventLogToolWindow", consoleLog.orCreateActions, false).component

    Disposer.register(this, consoleLog)
    EventLogNotificationService.subscribe(eventLogListener, recorderId)
  }

  private fun createActionToolbar(): JComponent {
    val topToolbarActions = DefaultActionGroup()
    topToolbarActions.add(RecordStateStatisticsEventLogAction(recorderId, false))
    topToolbarActions.add(ShowChangedStateEventsAction(recorderId))
    topToolbarActions.add(OpenEventLogFileAction(recorderId))
    topToolbarActions.addSeparator(StatisticsBundle.message("stats.events.scheme"))
    topToolbarActions.add(ConfigureEventsSchemeFileAction(recorderId))
    topToolbarActions.add(UpdateEventsSchemeAction(recorderId))
    topToolbarActions.add(OpenEventsSchemeFileAction(recorderId))
    topToolbarActions.addSeparator(StatisticsBundle.message("stats.events.test.scheme"))
    topToolbarActions.add(AddGroupToTestSchemeAction(recorderId))
    topToolbarActions.add(CleanupEventsTestSchemeAction())
    topToolbarActions.add(EditEventsTestSchemeAction(recorderId))
    val toolbar = ActionManager.getInstance().createActionToolbar("FusEventLogToolWindow", topToolbarActions, true)
    toolbar.setShowSeparatorTitles(true)
    return toolbar.component
  }

  private fun createFilter(project: Project, model: StatisticsLogFilterModel): FilterComponent {
    return object : FilterComponent("STATISTICS_EVENT_LOG_FILTER_HISTORY", 5) {
      override fun filter() {
        val task = object : Task.Backgroundable(project, LogConsoleBase.APPLYING_FILTER_TITLE) {
          override fun run(indicator: ProgressIndicator) {
            model.updateCustomFilter(filter)
          }
        }
        ProgressManager.getInstance().run(task)
      }
    }
  }

  override fun dispose() {
    EventLogNotificationService.unsubscribe(eventLogListener, recorderId)
  }

  companion object {
    val rejectedValidationTypes = setOf(REJECTED, INCORRECT_RULE, UNDEFINED_RULE, UNREACHABLE_METADATA, PERFORMANCE_ISSUE)
  }
}

