// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.toolwindow

import com.intellij.diagnostic.logging.LogConsoleBase
import com.intellij.icons.AllIcons.Actions.PrettyPrint
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.devkit.actions.*
import com.intellij.internal.statistic.devkit.actions.scheme.AddGroupToTestSchemeAction
import com.intellij.internal.statistic.devkit.actions.scheme.EditEventsTestSchemeAction
import com.intellij.internal.statistic.eventLog.EventLogListenersManager
import com.intellij.internal.statistic.eventLog.EventLogSystemEvents
import com.intellij.internal.statistic.eventLog.StatisticsEventLogListener
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.FilterComponent
import com.intellij.ui.JBColor
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

internal const val eventLogToolWindowsId = "Statistics Event Log"

internal class StatisticsEventLogToolWindow(project: Project, private val recorderId: String) : SimpleToolWindowPanel(false, true), Disposable {
  private val consoleLog: StatisticsEventLogConsole
  private val messageBuilder = StatisticsEventLogMessageBuilder()
  private val eventLogListener: StatisticsEventLogListener

  init {
    val model = StatisticsLogFilterModel()
    val logFormatter = StatisticsEventLogFormatter(model)
    consoleLog = StatisticsEventLogConsole(project, model, recorderId, logFormatter)
    eventLogListener = object : StatisticsEventLogListener {
      override fun onLogEvent(validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?) {
        ApplicationManager.getApplication().invokeLater {
          val message = messageBuilder.buildLogMessage(validatedEvent, rawEventId, rawData)
          consoleLog.addLogLine(message)
        }
      }
    }

    val topPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
    topPanel.add(createFilter(project, model))
    topPanel.add(createActionToolbar())
    topPanel.border = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border())
    add(topPanel, BorderLayout.NORTH)

    setContent(consoleLog.component)

    val verticalActionGroup = DefaultActionGroup()
    verticalActionGroup.addAll(consoleLog.getConsoleNotNull().createConsoleActions().toList())
    verticalActionGroup.add(StatisticsMultilineLogToggleAction(consoleLog))

    val verticalToolbar = ActionManager.getInstance().createActionToolbar("FusEventLogToolWindow", verticalActionGroup, false)
    verticalToolbar.targetComponent = this
    toolbar = verticalToolbar.component

    Disposer.register(this, consoleLog)
    service<EventLogListenersManager>().subscribe(eventLogListener, recorderId)
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
    topToolbarActions.add(CleanupEventsTestSchemeAction(recorderId))
    topToolbarActions.add(EditEventsTestSchemeAction(recorderId))
    topToolbarActions.add(GenerateEventsScheme(recorderId))
    val toolbar = ActionManager.getInstance().createActionToolbar("FusEventLogToolWindow", topToolbarActions, true)
    toolbar.setShowSeparatorTitles(true)
    toolbar.targetComponent = this
    return toolbar.component
  }

  private fun createFilter(project: Project, model: StatisticsLogFilterModel): FilterComponent {
    return object : FilterComponent("STATISTICS_EVENT_LOG_FILTER_HISTORY", 5) {
      override fun filter() {
        val task = object : Task.Backgroundable(project, LogConsoleBase.getApplyingFilterTitle()) {
          override fun run(indicator: ProgressIndicator) {
            model.updateCustomFilter(filter)
          }
        }
        ProgressManager.getInstance().run(task)
      }
    }
  }

  override fun dispose() {
    service<EventLogListenersManager>().unsubscribe(eventLogListener, recorderId)
  }

  companion object {
    @Suppress("DEPRECATION")
    val rejectedValidationTypes = setOf(REJECTED, INCORRECT_RULE, UNDEFINED_RULE, UNREACHABLE_METADATA, UNREACHABLE_METADATA_OBSOLETE, PERFORMANCE_ISSUE)
    val alertEvents = setOf(EventLogSystemEvents.TOO_MANY_EVENTS_ALERT, EventLogSystemEvents.TOO_MANY_EVENTS)
  }
}

private class StatisticsMultilineLogToggleAction(private val consoleLog: StatisticsEventLogConsole) :
  ToggleAction("Multiline Event Log Presentation", "Show event log in multiline presentation", PrettyPrint) {
  private var isMultilineLog = false

  override fun isSelected(e: AnActionEvent): Boolean {
    return isMultilineLog
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    isMultilineLog = state
    consoleLog.updateLogPresentation(state)
    update(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}