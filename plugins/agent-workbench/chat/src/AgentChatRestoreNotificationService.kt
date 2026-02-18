// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

internal object AgentChatRestoreNotificationService {
  private val LOG = logger<AgentChatRestoreNotificationService>()
  private val reportedWarnings = ConcurrentHashMap.newKeySet<String>()

  fun reportRestoreFailure(project: Project, file: AgentChatVirtualFile, reason: String) {
    reportWarning(project, file, reason)
  }

  fun reportTerminalInitializationFailure(project: Project, file: AgentChatVirtualFile, throwable: Throwable) {
    LOG.warn("Failed to initialize Agent Chat terminal tab for ${file.url}", throwable)
    reportWarning(
      project = project,
      file = file,
      reason = AgentChatBundle.message("chat.restore.validation.editor.init"),
    )
    ApplicationManager.getApplication().invokeLater {
      if (!project.isDisposed) {
        FileEditorManager.getInstance(project).closeFile(file)
      }
    }
  }

  private fun reportWarning(project: Project, file: AgentChatVirtualFile, reason: String) {
    val deduplicationKey = "${project.locationHash}|${file.url}|$reason"
    if (!reportedWarnings.add(deduplicationKey)) {
      return
    }

    runCatching {
      NotificationGroupManager.getInstance()
        .getNotificationGroup(AGENT_CHAT_NOTIFICATION_GROUP_ID)
        .createNotification(
          AgentChatBundle.message("chat.restore.failed.title"),
          AgentChatBundle.message("chat.restore.failed.body", file.threadTitle, reason),
          NotificationType.WARNING,
        )
        .notify(project)
    }.onFailure { error ->
      LOG.warn("Failed to show Agent Chat restore warning notification", error)
    }
  }
}

private const val AGENT_CHAT_NOTIFICATION_GROUP_ID = "Agent Workbench Chat"
