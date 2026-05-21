// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal val DEFAULT_WATCHER_UPDATE_TIMEOUT = 5.seconds

internal fun claudeUserLine(timestamp: String, sessionId: String, cwd: String, content: String, gitBranch: String? = null): String {
  val gitBranchField = gitBranch?.let { ",\"gitBranch\":\"$it\"" } ?: ""
  return """{"type":"user","sessionId":"$sessionId","cwd":"$cwd"$gitBranchField,"isSidechain":false,"timestamp":"$timestamp","message":{"role":"user","content":"$content"}}"""
}

internal fun claudeAssistantLine(timestamp: String, sessionId: String, cwd: String, content: String): String {
  return """{"type":"assistant","sessionId":"$sessionId","cwd":"$cwd","isSidechain":false,"timestamp":"$timestamp","message":{"role":"assistant","content":[{"type":"text","text":"$content"}],"stop_reason":"end_turn"}}"""
}

internal fun claudeAssistantPartialLine(timestamp: String, sessionId: String, cwd: String, content: String): String {
  return """{"type":"assistant","sessionId":"$sessionId","cwd":"$cwd","isSidechain":false,"timestamp":"$timestamp","message":{"role":"assistant","content":[{"type":"text","text":"$content"}],"stop_reason":null}}"""
}

internal fun claudeAssistantToolUseLine(timestamp: String, sessionId: String, cwd: String, content: String): String {
  return """{"type":"assistant","sessionId":"$sessionId","cwd":"$cwd","isSidechain":false,"timestamp":"$timestamp","message":{"role":"assistant","content":[{"type":"text","text":"$content"},{"type":"tool_use","id":"tu_1","name":"edit","input":{}}],"stop_reason":"tool_use"}}"""
}

internal fun claudeProgressLine(timestamp: String, sessionId: String, cwd: String): String {
  return """{"type":"progress","sessionId":"$sessionId","cwd":"$cwd","isSidechain":false,"timestamp":"$timestamp"}"""
}

internal fun claudeBackgroundToolResultLine(timestamp: String, sessionId: String, cwd: String, backgroundTaskId: String): String {
  return """{"type":"user","sessionId":"$sessionId","cwd":"$cwd","isSidechain":false,"timestamp":"$timestamp","message":{"role":"user","content":[{"tool_use_id":"tu_1","type":"tool_result","content":"Command running in background with ID: $backgroundTaskId","is_error":false}]},"toolUseResult":{"backgroundTaskId":"$backgroundTaskId","stdout":"","stderr":"","interrupted":false}}"""
}

internal fun claudeTaskNotificationLine(timestamp: String, sessionId: String, cwd: String, taskId: String): String {
  return """{"type":"user","sessionId":"$sessionId","cwd":"$cwd","isSidechain":false,"timestamp":"$timestamp","message":{"role":"user","content":"<task-notification>\n<task-id>$taskId</task-id>\n<status>completed</status>\n</task-notification>"}}"""
}

internal fun claudeQueueOperationLine(timestamp: String, sessionId: String, cwd: String, taskId: String): String {
  return """{"type":"queue-operation","sessionId":"$sessionId","cwd":"$cwd","isSidechain":false,"timestamp":"$timestamp","content":"<task-notification>\n<task-id>$taskId</task-id>\n<status>completed</status>\n</task-notification>"}"""
}

internal fun claudeCustomTitleLine(timestamp: String, sessionId: String, cwd: String, customTitle: String): String {
  return """{"type":"custom-title","sessionId":"$sessionId","cwd":"$cwd","isSidechain":false,"timestamp":"$timestamp","customTitle":"$customTitle"}"""
}

internal fun writeJsonl(file: Path, lines: List<String>) {
  Files.createDirectories(file.parent)
  Files.write(file, lines)
}

internal fun appendJsonl(file: Path, lines: List<String>) {
  Files.createDirectories(file.parent)
  Files.write(file, lines, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
}

internal fun drainUpdateChannel(updates: Channel<Unit>) {
  while (true) {
    if (!updates.tryReceive().isSuccess) {
      break
    }
  }
}

internal suspend fun awaitWatcherUpdate(
  updates: Channel<Unit>,
  timeout: Duration = DEFAULT_WATCHER_UPDATE_TIMEOUT,
): Boolean {
  val update = withTimeoutOrNull(timeout) {
    updates.receive()
  }
  return update != null
}
