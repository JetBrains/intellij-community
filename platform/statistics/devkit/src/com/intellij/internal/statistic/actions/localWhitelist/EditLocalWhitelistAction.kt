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
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.ui.TextIcon
import com.intellij.ui.components.dialog
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.io.IOException

/**
 * Action opens `Edit Local Whitelist` dialog.
 *
 * If "Add custom validation rules" is disabled, all event id and event data values from the group will be allowed.
 */
class EditLocalWhitelistAction(private val recorderId: String = StatisticsDevKitUtil.DEFAULT_RECORDER)
  : DumbAwareAction(ActionsBundle.messagePointer("action.EditLocalWhitelistAction.text"),
                    ActionsBundle.messagePointer("action.EditLocalWhitelistAction.description"),
                    ICON) {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val testWhitelistStorage = WhitelistTestGroupStorage.getTestStorage(recorderId)
    if (testWhitelistStorage == null) {
      showNotification(project, NotificationType.ERROR, StatisticsBundle.message("stats.cannot.find.test.whitelist.storage"))
      return
    }
    val whitelist = loadWhitelist(project, testWhitelistStorage)
    if (whitelist == null) return

    val editLocalWhitelistPanel = EditLocalWhitelistPanel(project, whitelist.localGroups, whitelist.productionGroups)
    val dialog = dialog(
      StatisticsBundle.message("stats.edit.local.whitelist"),
      panel = editLocalWhitelistPanel,
      resizable = true,
      focusedComponent = editLocalWhitelistPanel.getFocusedComponent(),
      project = project,
      ok = { editLocalWhitelistPanel.validateGroups() }
    )
    Disposer.register(dialog.disposable, editLocalWhitelistPanel)

    if (!dialog.showAndGet()) return

    runBackgroundableTask(StatisticsBundle.message("stats.updating.local.whitelist"), project, false) {
      try {
        testWhitelistStorage.updateTestGroups(editLocalWhitelistPanel.getGroups())
        showNotification(project, NotificationType.INFORMATION, StatisticsBundle.message("stats.local.whitelist.was.updated"))
      }
      catch (ex: IOException) {
        showNotification(project, NotificationType.ERROR, StatisticsBundle.message("stats.failed.updating.local.whitelist.0", ex.message))
      }
    }
  }

  private fun loadWhitelist(project: Project, testWhitelistStorage: WhitelistTestGroupStorage): Whitelist? {
    return ProgressManager.getInstance().run(object : Task.WithResult<Whitelist?, IOException>(
      project, StatisticsBundle.message("stats.loading.local.whitelist"), true) {
      override fun compute(indicator: ProgressIndicator): Whitelist? {
        val localGroups = testWhitelistStorage.loadLocalWhitelistGroups()
        if (indicator.isCanceled) return null
        val productionGroups = testWhitelistStorage.loadProductionGroups()
        if (indicator.isCanceled) return null
        return Whitelist(localGroups, productionGroups)
      }
    })
  }

  override fun update(e: AnActionEvent) {
    val localWhitelistSize = WhitelistTestGroupStorage.getTestStorage(recorderId)?.size() ?: 0
    val text = if (localWhitelistSize < 100) localWhitelistSize.toString() else "99+"
    val sizeCountIcon = TextIcon(text, JBColor.DARK_GRAY, UIUtil.getLabelBackground(), 1)
    sizeCountIcon.setFont(Font(UIUtil.getLabelFont().name, Font.BOLD, JBUIScale.scale(9)))
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

  class Whitelist(
    val localGroups: List<LocalWhitelistGroup>,
    val productionGroups: FUStatisticsWhiteListGroupsService.WLGroups
  )
}