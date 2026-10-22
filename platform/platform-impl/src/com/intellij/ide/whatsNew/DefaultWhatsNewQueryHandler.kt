// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.whatsNew

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.updateSettings.impl.UpdateCheckerFacade
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.await

private val LOG = logger<DefaultWhatsNewQueryHandler>()

@ApiStatus.Internal
class DefaultWhatsNewQueryHandler(private val project: Project) : HTMLEditorProvider.JsQueryHandler {
  override suspend fun query(id: Long, request: String): String = when (request) {
    "promo-get-some-air" -> {
      doAirPromo()
      "true"
    }
    else -> {
      LOG.info("unhandled request: ${request}")
      "false"
    }
  }

  // 2026.3 Air promo
  private suspend fun doAirPromo() {
    if (WelcomeScreenProjectProvider.isWelcomeScreenProject(project)) {
      val connection = ApplicationManager.getApplication().messageBus.connect()
      connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
        @Suppress("OVERRIDE_DEPRECATION", "removal")
        override fun projectOpened(openedProject: Project) {
          if (WelcomeScreenProjectProvider.isWelcomeScreenProject(openedProject)) return
          connection.disconnect()
          service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
            showAgentSessionsTooltip(openedProject)
          }
        }
      })
      if (!performAction("OpenFile")) {
        connection.disconnect()
      }
    }
    else if (!performAction("AgentThreads.MainToolbar.NewThread")) {
      notifyAirMissing(project)
    }
    WhatsNewUsagesCollector.airPromoButtonClicked(project)
  }

  private suspend fun performAction(actionId: String): Boolean {
    val action = service<ActionManager>().getAction(actionId)
    if (action == null) {
      LOG.info("no '${actionId}' action")
      return false
    }
    val dataContext = DataManager.getInstance().dataContextFromFocusAsync.await()
    withContext(Dispatchers.EDT) {
      action.actionPerformed(
        AnActionEvent.createEvent(action, dataContext, /*presentation =*/ null, WhatsNewUtil.PLACE, ActionUiKind.NONE, /*event =*/ null)
      )
    }
    return true
  }

  private suspend fun showAgentSessionsTooltip(project: Project) {
    val toolWindowManager = project.serviceAsync<ToolWindowManager>() as? ToolWindowManagerImpl ?: return
    toolWindowManager.awaitToolWindowsRegistered()

    withContext(Dispatchers.EDT) {
      if (project.isDisposed) {
        return@withContext
      }

      val toolWindow = toolWindowManager.getToolWindow("air.threads")
      if (toolWindow == null) {
        notifyAirMissing(project)
        return@withContext
      }

      @Suppress("SSBasedInspection")
      toolWindowManager.notifyByBalloon(toolWindow.id, MessageType.INFO, IdeBundle.message("whats.new.promo.air.tooltip"))
    }
  }

  private fun notifyAirMissing(project: Project) {
    UpdateCheckerFacade.getInstance().getNotificationGroupForIdeUpdateResults()
      .createNotification(IdeBundle.message("whats.new.promo.air.missing"), NotificationType.INFORMATION)
      .addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("whats.new.promo.air.plugins")) {
        PluginManagerConfigurable.showPluginConfigurable(project, listOf(PluginId("com.intellij.air")))
      })
      .notify(project)
  }
}
