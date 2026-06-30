// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.agent.workbench.engine.core.EventSource
import com.intellij.agent.workbench.engine.core.ThreadEventEnvelope
import com.intellij.agent.workbench.engine.core.ThreadEventType
import com.intellij.agent.workbench.engine.core.ThreadId
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks threads that produced agent output the user hasn't looked at yet, so the tree can show an
 * attention badge ("your turn"). State is in-memory only and seeded by live events: threads restored
 * from history on a fresh start are never unread (no badge), while a live agent reply marks its thread
 * unread until its threadView is opened. See [EngineEvents].
 */
@Service(Service.Level.PROJECT)
class EngineUnreadTracker(private val project: Project, scope: CoroutineScope) {
  private val unread = ConcurrentHashMap.newKeySet<String>()

  init {
    project.messageBus.connect(scope).subscribe(
      EngineEvents.TOPIC,
      object : EngineEvents {
        override fun eventAppended(event: ThreadEventEnvelope) {
          // A completed agent message is the "there is something new to read" signal.
          if (event.type == ThreadEventType.MessageCompleted && event.source == EventSource.Agent) {
            unread.add(event.threadId.value)
          }
        }

        override fun projectionUpdated(threadId: ThreadId) {}
      },
    )
  }

  fun isUnread(threadId: ThreadId): Boolean = unread.contains(threadId.value)

  /** Marks [threadId] as seen (e.g. its threadView was opened/focused) and refreshes the tree if it changed. */
  fun markRead(threadId: ThreadId) {
    if (unread.remove(threadId.value)) {
      EngineChangeBus.fireChanged(project.basePath ?: project.locationHash, threadId)
    }
  }

  companion object {
    fun getInstance(project: Project): EngineUnreadTracker = project.service()
  }
}
