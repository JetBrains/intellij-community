// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal object AgentChatRestoreNotificationService {
  fun reportRestoreFailure(project: Project, file: AgentChatVirtualFile, reason: String) {
    reportWarning(
      project = project,
      deduplicationKey = "${project.locationHash}|${file.url}|restore|$reason",
      title = AgentChatBundle.message("chat.restore.failed.title"),
      content = AgentChatBundle.message("chat.restore.failed.body", file.threadTitle, reason),
      failureLogMessage = "Failed to show Agent Chat restore warning notification",
    )
  }

  fun reportInitialPromptPlanModeFailure(project: Project, file: AgentChatVirtualFile) {
    reportWarning(
      project = project,
      deduplicationKey = "${project.locationHash}|${file.url}|initial-prompt-plan-mode",
      title = AgentChatBundle.message("chat.initial.prompt.plan.mode.failed.title"),
      content = AgentChatBundle.message("chat.initial.prompt.plan.mode.failed.body", file.threadTitle),
      failureLogMessage = "Failed to show Agent Chat initial prompt warning notification",
    )
  }

  suspend fun reportTerminalInitializationFailure(project: Project, file: AgentChatVirtualFile, throwable: Throwable) {
    logger<AgentChatRestoreNotificationService>().warn("Failed to initialize Agent Chat terminal tab for ${file.url}", throwable)
    forgetAgentChatTabMetadata(file.tabKey)
    val reason = buildTerminalInitializationReason(throwable)
    reportRestoreFailure(project = project, file = file, reason = reason)
    if (project.isDisposed) {
      return
    }
    withContext(Dispatchers.EDT) {
      project.serviceAsync<FileEditorManager>().closeFile(file)
    }
  }

  internal fun buildTerminalInitializationReason(throwable: Throwable): String {
    val diagnostic = extractTerminalStartDiagnostic(throwable)
    val command = diagnostic?.command
    if (command != null && isCommandMissingFailure(throwable)) {
      val path = diagnostic.path ?: AgentChatBundle.message("chat.restore.validation.editor.init.path.unknown")
      return AgentChatBundle.message("chat.restore.validation.editor.init.command.missing", command, path)
    }
    return AgentChatBundle.message("chat.restore.validation.editor.init")
  }
}

private val reportedWarnings = ConcurrentHashMap.newKeySet<String>()

private fun reportWarning(
  project: Project,
  deduplicationKey: String,
  title: @Nls String,
  content: @Nls String,
  failureLogMessage: String,
) {
  if (ApplicationManager.getApplication() == null) {
    return
  }
  if (!reportedWarnings.add(deduplicationKey)) {
    return
  }

  runCatching {
    NotificationGroupManager.getInstance()
      .getNotificationGroup(AGENT_CHAT_NOTIFICATION_GROUP_ID)
      .createNotification(
        title,
        content,
        NotificationType.WARNING,
      )
      .notify(project)
  }.onFailure { error ->
    logger<AgentChatRestoreNotificationService>().warn(failureLogMessage, error)
  }
}

private fun isCommandMissingFailure(throwable: Throwable): Boolean {
  return throwableChain(throwable).any { error ->
    val message = error.message?.lowercase(Locale.ROOT) ?: return@any false
    message.contains("command not found") ||
    message.contains("no such file or directory") ||
    message.contains("createprocess error=2") ||
    message.contains("error=2")
  }
}

private fun extractTerminalStartDiagnostic(throwable: Throwable): TerminalStartDiagnostic? {
  for (error in throwableChain(throwable)) {
    val message = error.message ?: continue
    val commandMatch = START_FAILURE_COMMAND_REGEX.find(message) ?: continue
    val command = commandMatch.groupValues[1].substringBefore(',').trim()
    if (command.isEmpty()) continue
    val path = START_FAILURE_PATH_REGEX.find(message)?.groupValues?.get(1)
    return TerminalStartDiagnostic(command = command, path = path)
  }
  return null
}

private fun throwableChain(throwable: Throwable): Sequence<Throwable> = sequence {
  val seen = HashSet<Throwable>()
  var current: Throwable? = throwable
  while (current != null && seen.add(current)) {
    yield(current)
    current = current.cause
  }
}

private const val AGENT_CHAT_NOTIFICATION_GROUP_ID = "Agent Workbench Chat"
private val START_FAILURE_COMMAND_REGEX = Regex("""Failed to start \[(.+?)] in """)
private val START_FAILURE_PATH_REGEX = Regex("""(?:^|[,{ ])PATH=([^,}]+)""")

private data class TerminalStartDiagnostic(
  @JvmField val command: String,
  @JvmField val path: String?,
)
