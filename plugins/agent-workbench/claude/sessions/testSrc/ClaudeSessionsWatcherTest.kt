// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.sessions.backend.store.ClaudeSessionsWatcher
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEvent
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ClaudeSessionsWatcherTest {
  @TempDir
  lateinit var tempDir: Path

  private fun withWatcher(block: (ClaudeSessionsWatcher) -> Unit) {
    val claudeHome = tempDir.resolve(".claude")
    val projectsDir = claudeHome.resolve("projects")
    Files.createDirectories(projectsDir)
    runBlocking(Dispatchers.Default) {
      ClaudeSessionsWatcher(
        claudeHomeProvider = { claudeHome },
        scope = this,
        onChange = {},
      ).use { watcher ->
        block(watcher)
      }
    }
  }

  @Test
  fun classifiesJsonlFileAsChangedPath() {
    withWatcher { watcher ->
      val jsonlPath = tempDir.resolve(".claude").resolve("projects").resolve("-work-project").resolve("session.jsonl")

      val event = AgentWorkbenchWatchEvent(
        eventType = AgentWorkbenchWatchEventType.MODIFY,
        path = jsonlPath,
        rootPath = tempDir.resolve(".claude").resolve("projects"),
        isDirectory = false,
        count = 1,
      )

      val changeSet = watcher.eventToChangeSet(event)

      assertThat(changeSet).isNotNull
      assertThat(changeSet!!.changedPaths).containsExactly(jsonlPath.toAbsolutePath().normalize())
      assertThat(changeSet.requiresFullRescan).isFalse()
    }
  }

  @Test
  fun classifiesIndexFileAsRefreshPing() {
    withWatcher { watcher ->
      val indexPath = tempDir.resolve(".claude").resolve("projects").resolve("-work-project").resolve("sessions-index.json")

      val event = AgentWorkbenchWatchEvent(
        eventType = AgentWorkbenchWatchEventType.MODIFY,
        path = indexPath,
        rootPath = tempDir.resolve(".claude").resolve("projects"),
        isDirectory = false,
        count = 1,
      )

      val changeSet = watcher.eventToChangeSet(event)

      assertThat(changeSet).isNotNull
      assertThat(changeSet!!.changedPaths).isEmpty()
      assertThat(changeSet.requiresFullRescan).isFalse()
    }
  }

  @Test
  fun classifiesOverflowAsFullRescan() {
    withWatcher { watcher ->
      val projectsRoot = tempDir.resolve(".claude").resolve("projects")

      val event = AgentWorkbenchWatchEvent(
        eventType = AgentWorkbenchWatchEventType.OVERFLOW,
        path = null,
        rootPath = projectsRoot,
        isDirectory = false,
        count = 1,
      )

      val changeSet = watcher.eventToChangeSet(event)

      assertThat(changeSet).isNotNull
      assertThat(changeSet!!.requiresFullRescan).isTrue()
    }
  }

  @Test
  fun classifiesDirectoryEventAsFullRescan() {
    withWatcher { watcher ->
      val dirPath = tempDir.resolve(".claude").resolve("projects").resolve("-work-new-project")

      val event = AgentWorkbenchWatchEvent(
        eventType = AgentWorkbenchWatchEventType.CREATE,
        path = dirPath,
        rootPath = tempDir.resolve(".claude").resolve("projects"),
        isDirectory = true,
        count = 1,
      )

      val changeSet = watcher.eventToChangeSet(event)

      assertThat(changeSet).isNotNull
      assertThat(changeSet!!.requiresFullRescan).isTrue()
    }
  }

  @Test
  fun classifiesNonJsonlNonIndexFileAsRefreshPing() {
    withWatcher { watcher ->
      val tmpFile = tempDir.resolve(".claude").resolve("projects").resolve("-work-project").resolve("temp.tmp")

      val event = AgentWorkbenchWatchEvent(
        eventType = AgentWorkbenchWatchEventType.CREATE,
        path = tmpFile,
        rootPath = tempDir.resolve(".claude").resolve("projects"),
        isDirectory = false,
        count = 1,
      )

      val changeSet = watcher.eventToChangeSet(event)

      assertThat(changeSet).isNotNull
      assertThat(changeSet!!.changedPaths).isEmpty()
      assertThat(changeSet.requiresFullRescan).isFalse()
    }
  }
}
