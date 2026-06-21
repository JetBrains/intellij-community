// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.json.filebacked

import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEvent
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEventType
import java.nio.file.Path

fun classifyFileBackedSessionEvent(
  event: AgentWorkbenchWatchEvent,
  isChangedPath: (Path) -> Boolean,
  isRelevantPath: (Path) -> Boolean,
  isRelevantRoot: (Path) -> Boolean,
): FileBackedSessionChangeSet? {
  val eventPath = event.path
  val rootPath = event.rootPath

  if (event.eventType == AgentWorkbenchWatchEventType.OVERFLOW) {
    val isRelevantOverflow = when {
      eventPath != null && isRelevantPath(eventPath) -> true
      rootPath != null && isRelevantRoot(rootPath) -> true
      eventPath == null && rootPath == null -> true
      else -> false
    }
    return if (isRelevantOverflow) FileBackedSessionChangeSet(requiresFullRescan = true) else null
  }

  if (eventPath != null && isChangedPath(eventPath)) {
    return FileBackedSessionChangeSet(changedPaths = setOf(normalizeFileBackedSessionPath(eventPath)))
  }

  val isRelevantEvent = when {
    eventPath != null -> isRelevantPath(eventPath)
    rootPath != null -> isRelevantRoot(rootPath)
    else -> false
  }
  if (!isRelevantEvent) return null

  val isAmbiguousDirectoryEvent = event.isDirectory || eventPath == null
  if (isAmbiguousDirectoryEvent) {
    return FileBackedSessionChangeSet(requiresFullRescan = true)
  }

  return FileBackedSessionChangeSet()
}
