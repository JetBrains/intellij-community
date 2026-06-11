// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

@TestApplication
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
  @RegistryKey(key = PI_FILE_WATCH_FALLBACK_REGISTRY_KEY, value = "false")
  fun `pi file watch fallback registry flag is disabled`() {
    assertThat(isPiFileWatchFallbackEnabled()).isFalse()
  }

  @Test
  @RegistryKey(key = PI_FILE_WATCH_FALLBACK_REGISTRY_KEY, value = "true")
  fun `pi file watch fallback registry flag can be enabled`() {
    assertThat(isPiFileWatchFallbackEnabled()).isTrue()
  }

  @Test
  fun `disabled file watch fallback does not resolve contributors`() {
    var contributorProviderCallCount = 0

    val source = PiSessionSource(
      sessionStore = PiSessionStore(sessionDirResolver = { tempDir.resolve("unused-sessions") }),
      extensionStatusEvents = emptyFlow(),
      fileWatchFallbackEnabledProvider = { false },
      sessionUpdateEventsContributorProvider = {
        contributorProviderCallCount++
        listOf(object : PiSessionUpdateEventsContributor {
          override fun createUpdateEvents(watchedProjectPathsBySessionDir: StateFlow<Map<Path, Set<String>>>) =
            error("Disabled file watch fallback must not create contributor flows")
        })
      },
    )

    assertThat(source.supportsUpdates).isTrue()
    assertThat(contributorProviderCallCount).isZero()
  }

  @Test
  fun `enabled file watch fallback merges contributor updates`() {
    runBlocking(Dispatchers.Default) {
      val updateEvent = AgentSessionSourceUpdateEvent(
        type = AgentSessionSourceUpdate.THREADS_CHANGED,
        scopedPaths = setOf(tempDir.resolve("project-contributor").toString()),
      )
      val source = PiSessionSource(
        sessionStore = PiSessionStore(sessionDirResolver = { tempDir.resolve("unused-sessions") }),
        extensionStatusEvents = emptyFlow(),
        fileWatchFallbackEnabledProvider = { true },
        sessionUpdateEventsContributorProvider = {
          listOf(object : PiSessionUpdateEventsContributor {
            override fun createUpdateEvents(watchedProjectPathsBySessionDir: StateFlow<Map<Path, Set<String>>>) =
              flowOf(updateEvent)
          })
        },
      )

      assertThat(source.updateEvents.first { event -> event.type == AgentSessionSourceUpdate.THREADS_CHANGED }).isEqualTo(updateEvent)
    }
  }

  @Test
  fun `pi extension status update creates scoped activity hint`() {
    val projectStatusDir = tempDir.resolve("project-status")
    val updateEvent = PiExtensionStatusBridge.parseStatusUpdate(
      content = """
        {"sessionId":"session-status","cwd":"${projectStatusDir.toString().jsonEscape()}","activity":"processing","updatedAt":5000}
      """.trimIndent(),
      receivedAtMs = 6_000L,
    )

    assertThat(updateEvent?.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
    assertThat(updateEvent?.scopedPaths).containsExactly(projectStatusDir.toString())
    assertThat(updateEvent?.activityUpdatesByThreadId).containsOnlyKeys("session-status")
    assertThat(updateEvent?.activityUpdatesByThreadId?.get("session-status")?.activityReport).isEqualTo(
      AgentThreadActivityReport(AgentThreadActivity.PROCESSING)
    )
    assertThat(updateEvent?.activityUpdatesByThreadId?.get("session-status")?.updatedAt).isEqualTo(5_000L)
    assertThat(updateEvent?.threadIds).containsExactly("session-status")
  }

  @Test
  fun `pi extension done status update uses shared unread activity`() {
    val projectStatusDir = tempDir.resolve("project-status-done")
    val updateEvent = PiExtensionStatusBridge.parseStatusUpdate(
      content = """
        {"sessionId":"session-status-done","cwd":"${projectStatusDir.toString().jsonEscape()}","activity":"done","updatedAt":5000}
      """.trimIndent(),
      receivedAtMs = 6_000L,
    )

    assertThat(updateEvent?.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
    assertThat(updateEvent?.scopedPaths).containsExactly(projectStatusDir.toString())
    assertThat(updateEvent?.threadIds).containsExactly("session-status-done")
    assertThat(updateEvent?.activityUpdatesByThreadId?.get("session-status-done")?.activityReport).isEqualTo(
      AgentThreadActivityReport(AgentThreadActivity.UNREAD)
    )
    assertThat(updateEvent?.activityUpdatesByThreadId?.get("session-status-done")?.updatedAt).isEqualTo(5_000L)
  }

  @Test
  fun `pi extension session info update creates scoped thread refresh`() {
    val projectStatusDir = tempDir.resolve("project-status-name")
    val projectStatusDirJson = projectStatusDir.toString().jsonString()
    val updateEvent = PiExtensionStatusBridge.parseStatusUpdate(
      content = """
        {"sessionId":"session-status-name","cwd":$projectStatusDirJson,"event":"session_info_changed","name":"Renamed"}
      """.trimIndent(),
      receivedAtMs = 6_000L,
    )

    assertThat(updateEvent?.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
    assertThat(updateEvent?.scopedPaths).containsExactly(projectStatusDir.toString())
    assertThat(updateEvent?.threadIds).containsExactly("session-status-name")
    assertThat(updateEvent?.activityUpdatesByThreadId).isEmpty()
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
        piUserMessageEntry(id = "user-read", content = "Read state", timestamp = 2_000L),
        piAssistantMessageEntry(id = "assistant-read", parentId = "user-read", timestamp = 3_000L),
      )
      val source = sourceFor(sessionDir)

      source.markThreadAsRead("session-read", 2_000L)
      assertThat(source.listThreadsFromClosedProject(projectDir.toString()).single().activity).isEqualTo(AgentThreadActivity.UNREAD)

      source.markThreadAsRead("session-read", 3_000L)
      assertThat(source.listThreadsFromClosedProject(projectDir.toString()).single().activity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun `user leaf marks session processing`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-user-leaf")
      val sessionDir = tempDir.resolve("user-leaf-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-user-leaf",
        cwd = projectDir,
        piUserMessageEntry(id = "user-leaf", content = "Run task", timestamp = 3_000L),
      )
      val source = sourceFor(sessionDir)

      val thread = source.listThreadsFromClosedProject(projectDir.toString()).single()

      assertThat(thread.activity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun `assistant tool use leaf marks session processing`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-tool-use-leaf")
      val sessionDir = tempDir.resolve("tool-use-leaf-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-tool-use-leaf",
        cwd = projectDir,
        piUserMessageEntry(id = "user-tool-use", content = "Run task", timestamp = 2_000L),
        piAssistantMessageEntry(
          id = "assistant-tool-use",
          parentId = "user-tool-use",
          timestamp = 3_000L,
          stopReason = "toolUse",
        ),
      )
      val source = sourceFor(sessionDir)

      val thread = source.listThreadsFromClosedProject(projectDir.toString()).single()

      assertThat(thread.activity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun `tool result leaf marks session processing`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-tool-result-leaf")
      val sessionDir = tempDir.resolve("tool-result-leaf-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-tool-result-leaf",
        cwd = projectDir,
        piUserMessageEntry(id = "user-tool-result", content = "Run task", timestamp = 2_000L),
        piToolResultMessageEntry(),
      )
      val source = sourceFor(sessionDir)

      val thread = source.listThreadsFromClosedProject(projectDir.toString()).single()

      assertThat(thread.activity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun `custom message leaf marks session processing`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-custom-message-leaf")
      val sessionDir = tempDir.resolve("custom-message-leaf-sessions")
      writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-custom-message-leaf",
        cwd = projectDir,
        piUserMessageEntry(id = "user-custom-message", content = "Run task", timestamp = 2_000L),
        piCustomMessageEntry(),
      )
      val source = sourceFor(sessionDir)

      val thread = source.listThreadsFromClosedProject(projectDir.toString()).single()

      assertThat(thread.activity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun `completed observed session is marked unread until read`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-completed-observed")
      val sessionDir = tempDir.resolve("completed-observed-sessions")
      val sessionFile = writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-completed-observed",
        cwd = projectDir,
        piUserMessageEntry(id = "user-completed-observed", content = "Run task", timestamp = 3_000L),
      )
      val source = sourceFor(sessionDir)

      val workingThread = source.listThreadsFromClosedProject(projectDir.toString()).single()
      assertThat(workingThread.activity).isEqualTo(AgentThreadActivity.PROCESSING)
      appendPiSessionEntry(
        sessionFile,
        piAssistantMessageEntry(id = "assistant-completed-observed", parentId = "user-completed-observed", timestamp = 4_000L),
      )

      val completedThread = source.listThreadsFromClosedProject(projectDir.toString()).single()
      assertThat(completedThread.activity).isEqualTo(AgentThreadActivity.UNREAD)
      source.markThreadAsRead("session-completed-observed", 4_000L)
      val readThread = source.listThreadsFromClosedProject(projectDir.toString()).single()
      assertThat(readThread.activity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun `active working session completion is not premarked read`() {
    runBlocking(Dispatchers.Default) {
      val projectDir = tempDir.resolve("project-active-completed")
      val sessionDir = tempDir.resolve("active-completed-sessions")
      val sessionFile = writePiSession(
        sessionDir = sessionDir,
        sessionId = "session-active-completed",
        cwd = projectDir,
        piUserMessageEntry(id = "user-active-completed", content = "Run task", timestamp = 3_000L),
      )
      val source = sourceFor(sessionDir)
      source.setActiveThreadId("session-active-completed")

      val workingThread = source.listThreadsFromClosedProject(projectDir.toString()).single()
      assertThat(workingThread.activity).isEqualTo(AgentThreadActivity.PROCESSING)
      appendPiSessionEntry(
        sessionFile,
        piAssistantMessageEntry(id = "assistant-active-completed", parentId = "user-active-completed", timestamp = 4_000L),
      )

      val completedThread = source.listThreadsFromClosedProject(projectDir.toString()).single()
      assertThat(completedThread.activity).isEqualTo(AgentThreadActivity.UNREAD)
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

  private fun appendPiSessionEntry(sessionFile: Path, entry: String) {
    Files.writeString(sessionFile, entry + "\n", StandardOpenOption.APPEND)
  }

  private fun lineCount(path: Path): Int {
    return Files.readString(path).lineSequence().count { it.isNotBlank() }
  }
}

private fun piUserMessageEntry(id: String, content: String, timestamp: Long): String {
  return piMessageEntry(
    id = id,
    parentId = null,
    entryTimestamp = "2026-01-01T00:00:02Z",
    messageFields = "\"role\":\"user\",\"content\":${content.jsonString()},\"timestamp\":$timestamp",
  )
}

private fun piAssistantMessageEntry(id: String, parentId: String, timestamp: Long, stopReason: String = "stop"): String {
  return piMessageEntry(
    id = id,
    parentId = parentId,
    entryTimestamp = "2026-01-01T00:00:03Z",
    messageFields = "\"role\":\"assistant\",\"content\":[],\"stopReason\":${stopReason.jsonString()},\"timestamp\":$timestamp",
  )
}

private fun piToolResultMessageEntry(): String {
  return piMessageEntry(
    id = "tool-result",
    parentId = "user-tool-result",
    entryTimestamp = "2026-01-01T00:00:03Z",
    messageFields = "\"role\":\"toolResult\",\"content\":[],\"timestamp\":3000",
  )
}

private fun piCustomMessageEntry(): String {
  return listOf(
    "\"type\":\"custom_message\"",
    "\"id\":\"custom-message\"",
    "\"parentId\":\"user-custom-message\"",
    "\"timestamp\":\"2026-01-01T00:00:03Z\"",
    "\"customType\":\"status\"",
    "\"content\":\"working\"",
  ).joinToString(separator = ",", prefix = "{", postfix = "}")
}

private fun piNamedSessionInfoEntry(id: String = "name-new", name: String): String {
  return listOf(
    "\"type\":\"session_info\"",
    "\"id\":${id.jsonString()}",
    "\"parentId\":\"user-new\"",
    "\"timestamp\":\"2026-01-01T00:00:04Z\"",
    "\"name\":${name.jsonString()}",
  ).joinToString(separator = ",", prefix = "{", postfix = "}")
}

private fun piMessageEntry(id: String, parentId: String?, entryTimestamp: String, messageFields: String): String {
  return listOf(
    "\"type\":\"message\"",
    "\"id\":${id.jsonString()}",
    "\"parentId\":${parentId?.jsonString() ?: "null"}",
    "\"timestamp\":${entryTimestamp.jsonString()}",
    "\"message\":{$messageFields}",
  ).joinToString(separator = ",", prefix = "{", postfix = "}")
}

private fun String.jsonEscape(): String {
  return replace("\\", "\\\\").replace("\"", "\\\"")
}

private fun String.jsonString(): String {
  return "\"${jsonEscape()}\""
}
