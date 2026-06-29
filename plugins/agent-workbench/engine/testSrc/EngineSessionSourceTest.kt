// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.agent.workbench.engine.core.EventSource
import com.intellij.agent.workbench.engine.core.ThreadEventType
import com.intellij.agent.workbench.engine.core.ThreadId
import com.intellij.agent.workbench.engine.ui.EngineSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class EngineSessionSourceTest {
  @Test
  fun `update event carries thread presentation hint for changed engine title`(@TempDir dir: Path) {
    runBlocking {
      val projectPath = dir.toString()
      val threadId = ThreadId("acp:thread-1")
      val source = EngineSessionSource()
      val events = ArrayList<AgentSessionSourceUpdateEvent>()
      val collection = launch {
        source.updateEvents.take(1).toList(events)
      }
      yield()

      val store = EngineProjectService.storeForPath(projectPath)
      store.append(
        threadId = threadId,
        source = EventSource.Runtime,
        type = ThreadEventType.ThreadCreated,
      )
      store.append(
        threadId = threadId,
        source = EventSource.Runtime,
        type = ThreadEventType.ThreadUpdated,
        payload = buildJsonObject { put("title", "Implement telemetry") },
      )

      EngineChangeBus.fireChanged(projectPath = projectPath, threadId = threadId)
      collection.join()

      val event = events.single()
      assertThat(event.scopedPaths).containsExactly(projectPath)
      assertThat(event.threadIds).containsExactly(threadId.value)
      assertThat(event.presentationUpdatesByThreadId[threadId.value]?.title).isEqualTo("Implement telemetry")
    }
  }
}
