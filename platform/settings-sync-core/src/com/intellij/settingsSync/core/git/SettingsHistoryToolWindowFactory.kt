package com.intellij.settingsSync.core.git

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.settingsSync.core.*
import git4idea.log.showExternalGitLogInToolwindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

internal class SettingsHistoryToolWindowFactory(private val corotineScope: CoroutineScope) : ToolWindowFactory, DumbAware {
  companion object {
    const val ID = "Backup and Sync History"
  }

  init {
    SettingsSyncEvents.getInstance().addListener(object : SettingsSyncEventListener {
      override fun enabledStateChanged(syncEnabled: Boolean) {
        corotineScope.async {
          ProjectManager.getInstanceIfCreated()?.openProjects?.forEach { project ->
            if (shouldBeAvailable()) {
              val toolWindowNullable = ToolWindowManager.getInstance(project).getToolWindow(ID)
              withContext(Dispatchers.EDT) {
                if (toolWindowNullable == null) {
                  ToolWindowManager.getInstance(project).registerToolWindow(ID) {
                    anchor = ToolWindowAnchor.LEFT
                    icon = AllIcons.Toolwindows.SettingSync
                    contentFactory = this@SettingsHistoryToolWindowFactory
                  }
                }
                else {
                  toolWindowNullable.setAvailable(true)
                }
              }
            }
            else if (!syncEnabled) { // if sync is enabled, but not available, do nothing
              val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ID) ?: return@forEach
              if (toolWindow.isAvailable) {
                withContext(Dispatchers.EDT) {
                  toolWindow.setAvailable(false)
                }
              }
            }
          }
        }
      }
    })
  }

  override fun shouldBeAvailable(project: Project): Boolean = shouldBeAvailable()

  private fun shouldBeAvailable(): Boolean {
    return Registry.`is`("settingsSync.ui.new.toolwindow.show")
           && isSettingsSyncEnabledInSettings()
  }

  override suspend fun isApplicableAsync(project: Project): Boolean  {
    return Registry.`is`("settingsSync.ui.new.toolwindow.show")
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    ApplicationManager.getApplication().executeOnPooledThread {
      val settingsSyncStorage = SettingsSyncMain.getInstance().controls.settingsSyncStorage
      val virtualFile = VfsUtil.findFile(settingsSyncStorage, true)
      if (virtualFile == null) {
        ApplicationManager.getApplication().invokeLater {
          Messages.showErrorDialog(SettingsSyncBundle.message("history.error.message"), SettingsSyncBundle.message("history.dialog.title"))
        }
        return@executeOnPooledThread
      }

      showExternalGitLogInToolwindow(project, toolWindow, {
        createLogUi(SettingsHistoryLogUiFactory())
      }, listOf(virtualFile), "", "")
    }
  }
}