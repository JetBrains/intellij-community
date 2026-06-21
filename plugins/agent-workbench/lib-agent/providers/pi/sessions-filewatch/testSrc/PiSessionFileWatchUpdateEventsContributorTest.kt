// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions.filewatch

import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEvent
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEventType
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class PiSessionFileWatchUpdateEventsContributorTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `session jsonl changes emit scoped thread update`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-watch")
      val sessionDir = tempDir.resolve("watch-sessions")
      val sessionFile = sessionDir.resolve("session-watch.jsonl")
      Files.createDirectories(sessionDir)
      Files.writeString(sessionFile, "{}\n")
      val watchEvents = MutableSharedFlow<AgentWorkbenchWatchEvent>(replay = 1)
      val watchedRoots = CompletableDeferred<Set<Path>>()
      val contributor = PiSessionFileWatchUpdateEventsContributor { roots ->
        watchedRoots.complete(roots)
        watchEvents
      }
      val projectPathsBySessionDir = MutableStateFlow(mapOf(sessionDir to setOf(projectDir.toString())))
      val update = async {
        withTimeout(5.seconds) {
          contributor.createUpdateEvents(projectPathsBySessionDir).first { event -> event.type == AgentSessionSourceUpdate.THREADS_CHANGED }
        }
      }

      assertThat(watchedRoots.await()).containsExactlyInAnyOrder(
        sessionDir.toAbsolutePath().normalize(),
        checkNotNull(sessionDir.parent).toAbsolutePath().normalize(),
      )
      watchEvents.emit(
        AgentWorkbenchWatchEvent(
          eventType = AgentWorkbenchWatchEventType.MODIFY,
          path = sessionFile.toAbsolutePath().normalize(),
          rootPath = sessionDir.toAbsolutePath().normalize(),
          isDirectory = false,
          count = 1,
        )
      )

      assertThat(update.await().scopedPaths).containsExactly(projectDir.toString())
    }
  }

  @Test
  fun `session directory creation emits scoped thread update`() {
    val sessionDir = tempDir.resolve("created-sessions")
    val projectDir = tempDir.resolve("project-created")
    val updateEvent = createPiSessionSourceUpdateEventForWatchEvent(
      event = AgentWorkbenchWatchEvent(
        eventType = AgentWorkbenchWatchEventType.CREATE,
        path = sessionDir,
        rootPath = checkNotNull(sessionDir.parent),
        isDirectory = true,
        count = 1,
      ),
      projectPathsBySessionDir = mapOf(sessionDir.toAbsolutePath().normalize() to setOf(projectDir.toString())),
    )

    assertThat(updateEvent?.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
    assertThat(updateEvent?.scopedPaths).containsExactly(projectDir.toString())
  }

  @Test
  fun `overflow refreshes scoped paths below root`() {
    val sessionDir = tempDir.resolve("overflow-sessions")
    val projectDir = tempDir.resolve("project-overflow")
    val updateEvent = createPiSessionSourceUpdateEventForWatchEvent(
      event = AgentWorkbenchWatchEvent(
        eventType = AgentWorkbenchWatchEventType.OVERFLOW,
        path = null,
        rootPath = checkNotNull(sessionDir.parent),
        isDirectory = true,
        count = 1,
      ),
      projectPathsBySessionDir = mapOf(sessionDir.toAbsolutePath().normalize() to setOf(projectDir.toString())),
    )

    assertThat(updateEvent?.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
    assertThat(updateEvent?.scopedPaths).containsExactly(projectDir.toString())
  }

  @Test
  fun `session watcher ignores non jsonl files`() {
    val sessionDir = tempDir.resolve("ignore-sessions")
    val projectDir = tempDir.resolve("project-ignore")
    val updateEvent = createPiSessionSourceUpdateEventForWatchEvent(
      event = AgentWorkbenchWatchEvent(
        eventType = AgentWorkbenchWatchEventType.MODIFY,
        path = sessionDir.resolve("notes.txt"),
        rootPath = sessionDir,
        isDirectory = false,
        count = 1,
      ),
      projectPathsBySessionDir = mapOf(sessionDir.toAbsolutePath().normalize() to setOf(projectDir.toString())),
    )

    assertThat(updateEvent).isNull()
  }
}
