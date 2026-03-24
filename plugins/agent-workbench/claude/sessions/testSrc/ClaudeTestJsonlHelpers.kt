// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal val DEFAULT_WATCHER_UPDATE_TIMEOUT = 5.seconds

internal fun claudeUserLine(timestamp: String, sessionId: String, cwd: String, content: String): String {
  return """{"type":"user","sessionId":"$sessionId","cwd":"$cwd","isSidechain":false,"timestamp":"$timestamp","message":{"role":"user","content":"$content"}}"""
}

internal fun claudeAssistantLine(timestamp: String, sessionId: String, cwd: String, content: String): String {
  return """{"type":"assistant","sessionId":"$sessionId","cwd":"$cwd","isSidechain":false,"timestamp":"$timestamp","message":{"role":"assistant","content":[{"type":"text","text":"$content"}]}}"""
}

internal fun claudeAssistantToolUseLine(timestamp: String, sessionId: String, cwd: String, content: String): String {
  return """{"type":"assistant","sessionId":"$sessionId","cwd":"$cwd","isSidechain":false,"timestamp":"$timestamp","message":{"role":"assistant","content":[{"type":"text","text":"$content"},{"type":"tool_use","id":"tu_1","name":"edit","input":{}}]}}"""
}

internal fun claudeProgressLine(timestamp: String, sessionId: String, cwd: String): String {
  return """{"type":"progress","sessionId":"$sessionId","cwd":"$cwd","isSidechain":false,"timestamp":"$timestamp"}"""
}

internal fun writeJsonl(file: Path, lines: List<String>) {
  Files.createDirectories(file.parent)
  Files.write(file, lines)
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
