// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions.scheme

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil.showNotification
import com.intellij.internal.statistic.eventLog.events.scheme.EventsSchemeBuilder
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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.dialog
import java.io.IOException

/**
 * Action adds group which are not yet registered on the server to a test groups scheme for testing.
 */
class AddGroupToTestSchemeAction constructor(private val recorderId: String = StatisticsDevKitUtil.DEFAULT_RECORDER)
  : DumbAwareAction(ActionsBundle.messagePointer("action.AddGroupToTestSchemeAction.text"),
                    ActionsBundle.messagePointer("action.AddGroupToTestSchemeAction.description"),
                    AllIcons.General.Add) {

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = StatisticsRecorderUtil.isTestModeEnabled(recorderId)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: ProjectManager.getInstance().defaultProject
    val testStorage = ValidationTestRulesPersistedStorage.getTestStorage(recorderId, true)
    if (testStorage == null) {
      showNotification(project, NotificationType.ERROR, StatisticsBundle.message("stats.cannot.find.test.scheme.storage"))
      return
    }
    val group = GroupValidationTestRule("", false)
    val dialog = createAddToTestSchemeDialog(project, testStorage, group)
    if (dialog == null || !dialog.showAndGet()) return

    runBackgroundableTask(StatisticsBundle.message("stats.updating.test.scheme"), project, false) {
      try {
        testStorage.addTestGroup(group)
        showNotification(project, NotificationType.INFORMATION, StatisticsBundle.message("stats.test.scheme.was.updated"))
      }
      catch (ex: IOException) {
        showNotification(project, NotificationType.ERROR, StatisticsBundle.message("stats.failed.updating.test.scheme.0", ex.message))
      }
    }
  }

  companion object {
    fun createAddToTestSchemeDialog(project: Project,
                                    testRulesStorage: ValidationTestRulesPersistedStorage,
                                    group: GroupValidationTestRule): DialogWrapper? {
      val scheme = loadEventsScheme(project, testRulesStorage) ?: return null
      val groupConfiguration = EventsTestSchemeGroupConfiguration(project, scheme.productionGroups, group, scheme.generatedScheme)
      val dialog = dialog(
        StatisticsBundle.message("stats.add.test.group.to.test.scheme"),
        panel = groupConfiguration.panel,
        resizable = true,
        focusedComponent = groupConfiguration.getFocusedComponent(),
        project = project,
        ok = { groupConfiguration.validate() }
      )
      Disposer.register(dialog.disposable, groupConfiguration)
      return dialog
    }

    private fun loadEventsScheme(project: Project,
                                 testRulesStorage: ValidationTestRulesPersistedStorage): EditEventsTestSchemeAction.EventsTestScheme? {
      return ProgressManager.getInstance().run(object : Task.WithResult<EditEventsTestSchemeAction.EventsTestScheme?, IOException>(
        project, StatisticsBundle.message("stats.loading.validation.rules"), true) {
        override fun compute(indicator: ProgressIndicator): EditEventsTestSchemeAction.EventsTestScheme? {
          val productionGroups = testRulesStorage.loadProductionGroups()
          if (indicator.isCanceled) return null
          val eventsScheme = EventsSchemeBuilder.buildEventsScheme()
          return EditEventsTestSchemeAction.EventsTestScheme(emptyList(), productionGroups, eventsScheme)
        }
      })
    }
  }

}