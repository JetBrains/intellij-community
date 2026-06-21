// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEvent
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class JunieSessionsWatcherTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun eventsFileEventProducesPathScopedChange() = runBlocking(Dispatchers.Default) {
    withWatcher(this) { watcher, sessionsRoot ->
      val eventsPath = sessionsRoot.resolve("session-1").resolve("events.jsonl")

      val changeSet = watcher.eventToChangeSet(
        AgentWorkbenchWatchEvent(
          eventType = AgentWorkbenchWatchEventType.MODIFY,
          path = eventsPath,
          rootPath = sessionsRoot,
          isDirectory = false,
          count = 1,
        )
      )

      assertThat(changeSet).isNotNull
      assertThat(changeSet!!.requiresFullRescan).isFalse()
      assertThat(changeSet.changedPaths).containsExactly(eventsPath.toAbsolutePath().normalize())
    }
  }

  @Test
  fun indexFileEventProducesPathScopedChange() = runBlocking(Dispatchers.Default) {
    withWatcher(this) { watcher, sessionsRoot ->
      val indexPath = sessionsRoot.resolve("index.jsonl")

      val changeSet = watcher.eventToChangeSet(
        AgentWorkbenchWatchEvent(
          eventType = AgentWorkbenchWatchEventType.MODIFY,
          path = indexPath,
          rootPath = sessionsRoot,
          isDirectory = false,
          count = 1,
        )
      )

      assertThat(changeSet).isNotNull
      assertThat(changeSet!!.requiresFullRescan).isFalse()
      assertThat(changeSet.changedPaths).containsExactly(indexPath.toAbsolutePath().normalize())
    }
  }

  @Test
  fun nonSessionFileUnderSessionsEmitsRefreshPing() = runBlocking(Dispatchers.Default) {
    withWatcher(this) { watcher, sessionsRoot ->
      val tempPath = sessionsRoot.resolve("session-1").resolve("state.json")

      val changeSet = watcher.eventToChangeSet(
        AgentWorkbenchWatchEvent(
          eventType = AgentWorkbenchWatchEventType.MODIFY,
          path = tempPath,
          rootPath = sessionsRoot,
          isDirectory = false,
          count = 1,
        )
      )

      assertThat(changeSet).isNotNull
      assertThat(changeSet!!.requiresFullRescan).isFalse()
      assertThat(changeSet.changedPaths).isEmpty()
    }
  }

  @Test
  fun fileEventOutsideSessionsIsIgnoredEvenIfRootIsJunieHome() = runBlocking(Dispatchers.Default) {
    withWatcher(this) { watcher, sessionsRoot ->
      val junieHome = sessionsRoot.parent
      val outsideSessionsPath = junieHome.resolve("settings.json")

      val changeSet = watcher.eventToChangeSet(
        AgentWorkbenchWatchEvent(
          eventType = AgentWorkbenchWatchEventType.MODIFY,
          path = outsideSessionsPath,
          rootPath = junieHome,
          isDirectory = false,
          count = 1,
        )
      )

      assertThat(changeSet).isNull()
    }
  }

  @Test
  fun directoryEventInSessionsRequestsFullRescan() = runBlocking(Dispatchers.Default) {
    withWatcher(this) { watcher, sessionsRoot ->
      val sessionDir = sessionsRoot.resolve("session-new")

      val changeSet = watcher.eventToChangeSet(
        AgentWorkbenchWatchEvent(
          eventType = AgentWorkbenchWatchEventType.CREATE,
          path = sessionDir,
          rootPath = sessionsRoot,
          isDirectory = true,
          count = 1,
        )
      )

      assertThat(changeSet).isNotNull
      assertThat(changeSet!!.requiresFullRescan).isTrue()
      assertThat(changeSet.changedPaths).isEmpty()
    }
  }

  @Test
  fun overflowInSessionsRequestsFullRescan() = runBlocking(Dispatchers.Default) {
    withWatcher(this) { watcher, sessionsRoot ->
      val changeSet = watcher.eventToChangeSet(
        AgentWorkbenchWatchEvent(
          eventType = AgentWorkbenchWatchEventType.OVERFLOW,
          path = null,
          rootPath = sessionsRoot,
          isDirectory = false,
          count = 1,
        )
      )

      assertThat(changeSet).isNotNull
      assertThat(changeSet!!.requiresFullRescan).isTrue()
    }
  }

  private fun withWatcher(scope: CoroutineScope, test: (JunieSessionsWatcher, Path) -> Unit) {
    val sessionsRoot = tempDir.resolve(".junie").resolve("sessions")
    JunieSessionsWatcher(
      sessionsRootPathProvider = { sessionsRoot },
      scope = scope,
      onChange = {},
    ).use { watcher ->
      test(watcher, sessionsRoot)
    }
  }
}
