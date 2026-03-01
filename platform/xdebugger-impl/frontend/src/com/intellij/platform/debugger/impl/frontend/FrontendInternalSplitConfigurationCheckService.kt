// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.InternalSplitConfigurationApi
import com.intellij.util.application
import com.intellij.util.ui.RestartDialog
import com.intellij.xdebugger.SplitDebuggerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class EnabledSplitDebuggerAction : DumbAwareToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    return SplitDebuggerMode.isSplitDebugger()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    setSplitDebuggerEnabled(state)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

private fun setSplitDebuggerEnabled(state: Boolean, callback: () -> Unit = {}) {
  SplitDebuggerMode.setEnabled(state)
  service<FrontendInternalSplitConfigurationCheckAppService>().cs.launch(Dispatchers.EDT) {
    InternalSplitConfigurationApi.getInstance().setSplitDebuggersEnabled(state)
    callback()
    application.service<RestartDialog>().showRestartRequired()
  }
}

@Service(Service.Level.APP)
private class FrontendInternalSplitConfigurationCheckAppService(val cs: CoroutineScope)

@Service(Service.Level.PROJECT)
internal class FrontendInternalSplitConfigurationCheckService(
  project: Project,
  cs: CoroutineScope,
) {
  init {
    cs.launch(Dispatchers.EDT) {
      val enabledOnFrontend = SplitDebuggerMode.isSplitDebugger()
      val enabledOnBackend = InternalSplitConfigurationApi.getInstance().isSplitDebuggersEnabled()
      if (enabledOnFrontend == enabledOnBackend) return@launch

      thisLogger().error("Split debugger key mismatch: frontend is ${enabledText(enabledOnFrontend)}, " +
                         "while backend is ${enabledText(enabledOnBackend)}")

      val notification = createNotification(enabledOnFrontend, enabledOnBackend)
      NotificationsManager.getNotificationsManager().showNotification(notification, project)
    }
  }

  private fun enabledText(isEnabled: Boolean) = if (isEnabled) "enabled" else "disabled"

  @Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
  private fun createNotification(enabledOnFrontend: Boolean, enabledOnBackend: Boolean): Notification {
    val title = "Split debugger key mismatch"
    val content = "${SplitDebuggerMode.SPLIT_DEBUGGER_KEY} key is ${enabledText(enabledOnFrontend)} on frontend, " +
                  "while ${enabledText(enabledOnBackend)} on backend"
    val notification = Notification("Split debugger internal", title, content, NotificationType.ERROR)
    notification.addAction(NotificationAction.create("Enable split debugger") {
      setSplitDebuggerEnabled(true) {
        notification.expire()
      }
    })
    notification.addAction(NotificationAction.create("Disable split debugger") {
      setSplitDebuggerEnabled(false) {
        notification.expire()
      }
    })
    return notification
  }
}