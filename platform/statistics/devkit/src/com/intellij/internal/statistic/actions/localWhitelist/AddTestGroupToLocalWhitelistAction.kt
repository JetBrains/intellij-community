// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions.localWhitelist

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.StatisticsDevKitUtil
import com.intellij.internal.statistic.StatisticsDevKitUtil.showNotification
import com.intellij.internal.statistic.eventLog.whitelist.LocalWhitelistGroup
import com.intellij.internal.statistic.eventLog.whitelist.WhitelistTestGroupStorage
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.dialog
import java.io.IOException

/**
 * Action adds not whitelisted statistics group to a local whitelist for testing.
 *
 * If "Add custom validation rules" is disabled, all event id and event data values from the group will be allowed.
 */
class AddTestGroupToLocalWhitelistAction constructor(private val recorderId: String = StatisticsDevKitUtil.DEFAULT_RECORDER)
  : DumbAwareAction(ActionsBundle.messagePointer("action.AddTestGroupToLocalWhitelistAction.text"),
                    ActionsBundle.messagePointer("action.AddTestGroupToLocalWhitelistAction.description"),
                    AllIcons.General.Add) {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val testWhitelistStorage = WhitelistTestGroupStorage.getTestStorage(recorderId)
    if (testWhitelistStorage == null) {
      showNotification(project, NotificationType.ERROR, StatisticsBundle.message("stats.cannot.find.test.whitelist.storage"))
      return
    }
    val whitelistGroup = LocalWhitelistGroup("", false)
    val dialog = createAddToLocalWhitelistDialog(project, testWhitelistStorage, whitelistGroup)
    if (dialog == null || !dialog.showAndGet()) return

    runBackgroundableTask(StatisticsBundle.message("stats.updating.local.whitelist"), project, false) {
      try {
        testWhitelistStorage.addTestGroup(whitelistGroup)
        showNotification(project, NotificationType.INFORMATION, StatisticsBundle.message("stats.local.whitelist.was.updated"))
      }
      catch (ex: IOException) {
        showNotification(project, NotificationType.ERROR, StatisticsBundle.message("stats.failed.updating.local.whitelist.0", ex.message))
      }
    }
  }

  companion object {
    fun createAddToLocalWhitelistDialog(project: Project,
                                        testGroupStorage: WhitelistTestGroupStorage,
                                        group: LocalWhitelistGroup): DialogWrapper? {
      val productionGroups = loadProductionGroups(project, testGroupStorage)
      if (productionGroups == null) return null
      val groupConfiguration = LocalWhitelistGroupConfiguration(project, productionGroups, group)
      val dialog = dialog(
        StatisticsBundle.message("stats.add.test.group.to.local.whitelist"),
        panel = groupConfiguration.panel,
        resizable = true,
        focusedComponent = groupConfiguration.getFocusedComponent(),
        project = project,
        ok = { groupConfiguration.validate() }
      )
      Disposer.register(dialog.disposable, groupConfiguration)
      return dialog
    }

    private fun loadProductionGroups(project: Project,
                                     testGroupStorage: WhitelistTestGroupStorage): FUStatisticsWhiteListGroupsService.WLGroups? {
      return ProgressManager.getInstance().run(object : Task.WithResult<FUStatisticsWhiteListGroupsService.WLGroups?, IOException>(
        project, StatisticsBundle.message("stats.loading.validation.rules"), true) {
        override fun compute(indicator: ProgressIndicator): FUStatisticsWhiteListGroupsService.WLGroups? {
          val productionGroups = testGroupStorage.loadProductionGroups()
          if (indicator.isCanceled) return null
          return productionGroups
        }
      })
    }
  }

}