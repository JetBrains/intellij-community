// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEvent
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEventType
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
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
class PiSessionSourceTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `loads sessions for matching project path`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-a")
      val otherProjectDir = tempDir.resolve("project-b")
      val sessionDir = tempDir.resolve("sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-old",
        cwd = projectDir,
        piUserMessageEntry(id = "user-old", content = "Old task", timestamp = 2_000L),
      )
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-new",
        cwd = projectDir,
        piUserMessageEntry(id = "user-new", content = "First user message", timestamp = 3_000L),
        piNamedSessionInfoEntry(name = "Named Pi session"),
      )
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-other",
        cwd = otherProjectDir,
        piUserMessageEntry(id = "user-other", content = "Other project", timestamp = 4_000L),
      )
      Files.writeString(sessionDir.resolve("malformed.jsonl"), "not json\n")
      val source = sourceFor(sessionDir)

      val threads = source.listThreadsFromClosedProject("$projectDir/")

      assertThat(threads.map { it.id }).containsExactly("session-new", "session-old")
      assertThat(threads[0].title).isEqualTo("Named Pi session")
      assertThat(threads[0].updatedAt).isEqualTo(3_000L)
      assertThat(threads[0].archived).isFalse()
      assertThat(threads[0].activity).isEqualTo(AgentThreadActivity.READY)
      assertThat(threads[0].provider).isEqualTo(AgentSessionProvider.PI)
      assertThat(threads[1].title).isEqualTo("Old task")
    }
  }

  @Test
  fun `latest session info entry is used as title`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-latest-name")
      val sessionDir = tempDir.resolve("latest-name-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-latest-name",
        cwd = projectDir,
        piUserMessageEntry(id = "user-latest-name", content = "Fallback title", timestamp = 3_000L),
        piNamedSessionInfoEntry(id = "name-old", name = "Old name"),
        piNamedSessionInfoEntry(id = "name-new", name = "New name"),
      )
      val source = sourceFor(sessionDir)

      val thread = source.listThreadsFromClosedProject(projectDir.toString()).single()

      assertThat(thread.title).isEqualTo("New name")
    }
  }

  @Test
  fun `session jsonl changes emit scoped thread update`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-watch")
      val sessionDir = tempDir.resolve("watch-sessions")
      val sessionFile = writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-watch",
        cwd = projectDir,
        piUserMessageEntry(id = "user-watch", content = "Watch title", timestamp = 3_000L),
      )
      val watchEvents = MutableSharedFlow<AgentWorkbenchWatchEvent>(replay = 1)
      val watchedRoots = CompletableDeferred<Set<Path>>()
      val source = PiSessionSource(
        sessionStore = PiSessionStore(sessionDirResolver = { sessionDir }),
        sessionWatchEventsFactory = { roots ->
          watchedRoots.complete(roots)
          watchEvents
        },
      )
      val update = async {
        withTimeout(5.seconds) {
          source.updateEvents.first { event -> event.type == AgentSessionSourceUpdate.THREADS_CHANGED }
        }
      }

      source.listThreadsFromClosedProject(projectDir.toString())
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

  @Test
  fun `array text content is used as first user message fallback title`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-array-content")
      val sessionDir = tempDir.resolve("array-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-array",
        cwd = projectDir,
        """
          {"type":"message","id":"user-1","parentId":null,"timestamp":"2026-01-01T00:00:02Z","message":{"role":"user","content":[{"type":"text","text":"First"},{"type":"image","source":"ignored"},{"type":"text","text":"task"}]}}
        """.trimIndent(),
      )
      val source = sourceFor(sessionDir)

      val thread = source.listThreadsFromClosedProject(projectDir.toString()).single()

      assertThat(thread.title).isEqualTo("First task")
    }
  }

  @Test
  fun `archive and unarchive use sidecar state`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-archive")
      val sessionDir = tempDir.resolve("archive-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-archive",
        cwd = projectDir,
        piUserMessageEntry(id = "user-archive", content = "Archive me", timestamp = 3_000L),
      )
      var now = 4_000L
      val store = PiSessionStore(sessionDirResolver = { sessionDir }, timeProvider = { now })
      val source = PiSessionSource(sessionStore = store)

      assertThat(store.archiveThread(projectDir.toString(), "session-archive")).isTrue()
      assertThat(source.listThreadsFromClosedProject(projectDir.toString())).isEmpty()
      val archivedThread = source.listArchivedThreadsFromClosedProject(projectDir.toString()).single()
      assertThat(archivedThread.id).isEqualTo("session-archive")
      assertThat(archivedThread.archived).isTrue()
      assertThat(archivedThread.updatedAt).isEqualTo(4_000L)

      now = 5_000L
      assertThat(store.unarchiveThread(projectDir.toString(), "session-archive")).isTrue()
      val activeThread = source.listThreadsFromClosedProject(projectDir.toString()).single()
      assertThat(activeThread.archived).isFalse()
      assertThat(activeThread.updatedAt).isEqualTo(5_000L)
      assertThat(source.listArchivedThreadsFromClosedProject(projectDir.toString())).isEmpty()
      assertThat(lineCount(sessionDir.resolve("agent-workbench-archive-state.jsonl"))).isEqualTo(2)
    }
  }

  @Test
  fun `rename appends pi session info entry`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-rename")
      val sessionDir = tempDir.resolve("rename-sessions")
      val sessionFile = writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-rename",
        cwd = projectDir,
        piUserMessageEntry(id = "user-rename", content = "Old title", timestamp = 3_000L),
      )
      val store = PiSessionStore(sessionDirResolver = { sessionDir }, timeProvider = { 4_000L })
      val source = PiSessionSource(sessionStore = store)

      assertThat(store.renameThread(projectDir.toString(), "session-rename", "  New title  ")).isTrue()

      val thread = source.listThreadsFromClosedProject(projectDir.toString()).single()
      assertThat(thread.title).isEqualTo("New title")
      val lines = Files.readAllLines(sessionFile)
      assertThat(lines.last()).contains("\"type\":\"session_info\"")
      assertThat(lines.last()).contains("\"parentId\":\"user-rename\"")
      assertThat(lines.last()).contains("\"name\":\"New title\"")
    }
  }

  @Test
  fun `read tracker marks updated sessions unread`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-read-state")
      val sessionDir = tempDir.resolve("read-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-read",
        cwd = projectDir,
        piUserMessageEntry(id = "user-read", content = "Read state", timestamp = 3_000L),
      )
      val source = sourceFor(sessionDir)

      source.markThreadAsRead("session-read", 2_000L)
      assertThat(source.listThreadsFromClosedProject(projectDir.toString()).single().activity).isEqualTo(AgentThreadActivity.UNREAD)

      source.markThreadAsRead("session-read", 3_000L)
      assertThat(source.listThreadsFromClosedProject(projectDir.toString()).single().activity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun `effective session dir follows pi precedence`() {
    val homeDir = tempDir.resolve("home")
    val projectDir = tempDir.resolve("project-settings")
    val globalSessionDir = tempDir.resolve("global-sessions")
    val projectSessionDir = tempDir.resolve("project-sessions")
    val envSessionDir = tempDir.resolve("env-sessions")
    Files.createDirectories(homeDir.resolve(".pi").resolve("agent"))
    Files.createDirectories(projectDir.resolve(".pi"))
    Files.writeString(homeDir.resolve(".pi").resolve("agent").resolve("settings.json"), "{\"sessionDir\":\"$globalSessionDir\"}")
    Files.writeString(projectDir.resolve(".pi").resolve("settings.json"), "{\"sessionDir\":\"$projectSessionDir\"}")

    val projectConfigured = resolveEffectivePiSessionDir(
      projectPath = projectDir.toString(),
      environmentProvider = { null },
      homeDirProvider = { homeDir.toString() },
    )
    val envConfigured = resolveEffectivePiSessionDir(
      projectPath = projectDir.toString(),
      environmentProvider = { key -> if (key == "PI_CODING_AGENT_SESSION_DIR") envSessionDir.toString() else null },
      homeDirProvider = { homeDir.toString() },
    )

    assertThat(projectConfigured).isEqualTo(projectSessionDir)
    assertThat(envConfigured).isEqualTo(envSessionDir)
  }

  private fun sourceFor(sessionDir: Path): PiSessionSource {
    return PiSessionSource(sessionStore = PiSessionStore(sessionDirResolver = { sessionDir }))
  }

  private fun writePiSession(sessionDir: Path, sessionId: String, cwd: Path, vararg entries: String): Path {
    Files.createDirectories(sessionDir)
    val sessionFile = sessionDir.resolve("2026-01-01T00-00-00-000Z_$sessionId.jsonl")
    val header =
      """{"type":"session","version":3,"id":"$sessionId","timestamp":"2026-01-01T00:00:01Z","cwd":"${cwd.toString().jsonEscape()}"}"""
    Files.writeString(sessionFile, (listOf(header) + entries).joinToString(separator = "\n", postfix = "\n"))
    return sessionFile
  }

  private fun lineCount(path: Path): Int {
    return Files.readString(path).lineSequence().count { it.isNotBlank() }
  }
}

private fun piUserMessageEntry(id: String, content: String, timestamp: Long): String {
  return """
    {"type":"message","id":"$id","parentId":null,"timestamp":"2026-01-01T00:00:02Z","message":{"role":"user","content":"${content.jsonEscape()}","timestamp":$timestamp}}
  """.trimIndent()
}

private fun piNamedSessionInfoEntry(id: String = "name-new", name: String): String {
  return """
    {"type":"session_info","id":"$id","parentId":"user-new","timestamp":"2026-01-01T00:00:04Z","name":"${name.jsonEscape()}"}
  """.trimIndent()
}

private fun String.jsonEscape(): String {
  return replace("\\", "\\\\").replace("\"", "\\\"")
}
