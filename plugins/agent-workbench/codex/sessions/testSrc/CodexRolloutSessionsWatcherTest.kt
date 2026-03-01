// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutSessionsWatcher
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEvent
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CodexRolloutSessionsWatcherTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun rolloutFileEventProducesPathScopedChange() = runBlocking(Dispatchers.Default) {
    withWatcher(this) { watcher, _, sessionsRoot ->
      val rolloutPath = sessionsRoot.resolve("2026/02/16/rollout-thread.jsonl")

      val changeSet = watcher.eventToChangeSet(
        AgentWorkbenchWatchEvent(
          eventType = AgentWorkbenchWatchEventType.MODIFY,
          path = rolloutPath,
          rootPath = sessionsRoot,
          isDirectory = false,
          count = 1,
        )
      )

      assertThat(changeSet).isNotNull
      assertThat(changeSet!!.requiresFullRescan).isFalse()
      assertThat(changeSet.changedRolloutPaths).containsExactly(rolloutPath.toAbsolutePath().normalize())
    }
  }

  @Test
  fun nonRolloutRegularFileEventInSessionsEmitsRefreshPing() = runBlocking(Dispatchers.Default) {
    withWatcher(this) { watcher, _, sessionsRoot ->
      val nonRolloutPath = sessionsRoot.resolve("2026/02/16/thread.tmp")

      val changeSet = watcher.eventToChangeSet(
        AgentWorkbenchWatchEvent(
          eventType = AgentWorkbenchWatchEventType.MODIFY,
          path = nonRolloutPath,
          rootPath = sessionsRoot,
          isDirectory = false,
          count = 1,
        )
      )

      assertThat(changeSet).isNotNull
      assertThat(changeSet!!.requiresFullRescan).isFalse()
      assertThat(changeSet.changedRolloutPaths).isEmpty()
    }
  }

  @Test
  fun fileEventOutsideSessionsIsIgnoredEvenIfRootIsCodexHome() = runBlocking(Dispatchers.Default) {
    withWatcher(this) { watcher, codexHome, _ ->
      val outsideSessionsPath = codexHome.resolve("config.toml")

      val changeSet = watcher.eventToChangeSet(
        AgentWorkbenchWatchEvent(
          eventType = AgentWorkbenchWatchEventType.MODIFY,
          path = outsideSessionsPath,
          rootPath = codexHome,
          isDirectory = false,
          count = 1,
        )
      )

      assertThat(changeSet).isNull()
    }
  }

  @Test
  fun directoryEventInSessionsRequestsFullRescan() = runBlocking(Dispatchers.Default) {
    withWatcher(this) { watcher, _, sessionsRoot ->
      val dayDir = sessionsRoot.resolve("2026/02/16")

      val changeSet = watcher.eventToChangeSet(
        AgentWorkbenchWatchEvent(
          eventType = AgentWorkbenchWatchEventType.CREATE,
          path = dayDir,
          rootPath = sessionsRoot,
          isDirectory = true,
          count = 1,
        )
      )

      assertThat(changeSet).isNotNull
      assertThat(changeSet!!.requiresFullRescan).isTrue()
      assertThat(changeSet.changedRolloutPaths).isEmpty()
    }
  }

  @Test
  fun overflowInSessionsRequestsFullRescan() = runBlocking(Dispatchers.Default) {
    withWatcher(this) { watcher, _, sessionsRoot ->
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
      assertThat(changeSet.changedRolloutPaths).isEmpty()
    }
  }

  private fun withWatcher(scope: CoroutineScope, test: (CodexRolloutSessionsWatcher, Path, Path) -> Unit) {
    val codexHome = tempDir.resolve("codex-home")
    val sessionsRoot = codexHome.resolve("sessions")
    CodexRolloutSessionsWatcher(
      codexHomeProvider = { codexHome },
      scope = scope,
      onRolloutChange = {},
    ).use { watcher ->
      test(watcher, codexHome, sessionsRoot)
    }
  }
}
