package com.intellij.settingsSync.core.git

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.settingsSync.core.SettingsSyncBundle
import com.intellij.settingsSync.core.SettingsSyncMain
import git4idea.log.showExternalGitLogInToolwindow
import java.util.function.Supplier

@Deprecated("Please remove me after moving to new Settings Sync toolwindow")
private class SettingsSyncHistoryAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!

    if (Registry.`is`("settingsSync.ui.new.toolwindow.show")) {
      ToolWindowManager.getInstance(project).getToolWindow(SettingsHistoryToolWindowFactory.ID)?.activate(null)
      return
    }

    val settingsSyncStorage = SettingsSyncMain.getInstance().controls.settingsSyncStorage
    val virtualFile = VfsUtil.findFile(settingsSyncStorage, true)
    if (virtualFile == null) {
      Messages.showErrorDialog(SettingsSyncBundle.message("history.error.message"), SettingsSyncBundle.message("history.dialog.title"))
      return
    }

    val toolWindowId = "SettingsSync"
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow(toolWindowId) ?: toolWindowManager.registerToolWindow(toolWindowId) {
      stripeTitle = Supplier { SettingsSyncBundle.message("title.settings.sync") }
    }

    showExternalGitLogInToolwindow(project, toolWindow, listOf(virtualFile), SettingsSyncBundle.message("history.tab.name"), "")
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }
}