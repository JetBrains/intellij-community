// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions.scheme

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil.showNotification
import com.intellij.internal.statistic.eventLog.events.scheme.EventsSchemeBuilder
import com.intellij.internal.statistic.eventLog.events.scheme.GroupDescriptor
import com.intellij.internal.statistic.eventLog.validator.storage.GroupValidationTestRule
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationTestRulesPersistedStorage
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.ui.TextIcon
import com.intellij.ui.components.dialog
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.fus.reporting.model.metadata.EventGroupRemoteDescriptors
import java.awt.Font
import java.io.IOException

/**
 * Action opens `Edit Events Test Scheme` dialog.
 */
class EditEventsTestSchemeAction(private val recorderId: String = StatisticsDevKitUtil.DEFAULT_RECORDER)
  : DumbAwareAction(ActionsBundle.messagePointer("action.EditTestSchemeAction.text"),
                    ActionsBundle.messagePointer("action.EditTestSchemeAction.description"),
                    ICON) {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: ProjectManager.getInstance().defaultProject
    val testSchemeStorage = ValidationTestRulesPersistedStorage.getTestStorage(recorderId, true)
    if (testSchemeStorage == null) {
      showNotification(project, NotificationType.ERROR, StatisticsBundle.message("stats.cannot.find.test.scheme.storage"))
      return
    }
    val scheme = loadEventsScheme(project, testSchemeStorage) ?: return

    val editTestSchemePanel = EditEventsTestSchemePanel(project, scheme.testScheme, scheme.productionGroups, scheme.generatedScheme)
    val dialog = dialog(
      StatisticsBundle.message("stats.edit.test.scheme"),
      panel = editTestSchemePanel,
      resizable = true,
      focusedComponent = editTestSchemePanel.getFocusedComponent(),
      project = project,
      ok = { editTestSchemePanel.validateGroups() }
    )
    Disposer.register(dialog.disposable, editTestSchemePanel)

    if (!dialog.showAndGet()) return

    runBackgroundableTask(StatisticsBundle.message("stats.updating.test.scheme"), project, false) {
      try {
        testSchemeStorage.updateTestGroups(editTestSchemePanel.getGroups())
        showNotification(project, NotificationType.INFORMATION, StatisticsBundle.message("stats.test.scheme.was.updated"))
      }
      catch (ex: IOException) {
        showNotification(project, NotificationType.ERROR, StatisticsBundle.message("stats.failed.updating.test.scheme.0", ex.message))
      }
    }
  }

  private fun loadEventsScheme(project: Project, testSchemeStorage: ValidationTestRulesPersistedStorage): EventsTestScheme? {
    return ProgressManager.getInstance().run(object : Task.WithResult<EventsTestScheme?, IOException>(
      project, StatisticsBundle.message("stats.loading.test.scheme"), true) {
      override fun compute(indicator: ProgressIndicator): EventsTestScheme? {
        val localGroups = testSchemeStorage.loadValidationTestRules()
        if (indicator.isCanceled) return null
        val productionGroups = testSchemeStorage.loadProductionGroups()
        if (indicator.isCanceled) return null
        val eventsScheme = EventsSchemeBuilder.buildEventsScheme()
        return EventsTestScheme(localGroups, productionGroups, eventsScheme)
      }
    })
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = StatisticsRecorderUtil.isTestModeEnabled(recorderId)
    val testSchemeSize = ValidationTestRulesPersistedStorage.getTestStorage(recorderId, true)?.size() ?: 0
    val text = if (testSchemeSize < 100) testSchemeSize.toString() else "99+"
    val sizeCountIcon = TextIcon(text, JBColor.DARK_GRAY, UIUtil.getLabelBackground(), 1)
    sizeCountIcon.setFont(Font(StartupUiUtil.getLabelFont().name, Font.BOLD, JBUIScale.scale(9)))
    sizeCountIcon.setInsets(1, 1, 0, 0)
    ICON.setIcon(sizeCountIcon, 1, JBUIScale.scale(10), JBUIScale.scale(10))
    e.presentation.icon = ICON
  }

  companion object {
    private val ICON = LayeredIcon(2)

    init {
      ICON.setIcon(AllIcons.Actions.Edit, 0)
    }
  }

  class EventsTestScheme(
    val testScheme: List<GroupValidationTestRule>,
    val productionGroups: EventGroupRemoteDescriptors,
    val generatedScheme: List<GroupDescriptor>
  )
}