// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.opencode.sessions

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionArchivedSource
import com.intellij.platform.ai.agent.opencode.sessions.server.OpenCodeServerProject
import com.intellij.platform.ai.agent.opencode.sessions.server.OpenCodeServerSession
import com.intellij.platform.ai.agent.opencode.sessions.server.OpenCodeServerSessionBackend
import com.intellij.platform.ai.agent.opencode.sessions.server.OpenCodeServerTransport
import com.intellij.platform.ai.agent.opencode.sessions.server.createOpenCodeHttpClient
import com.intellij.platform.ai.agent.opencode.sessions.server.parseOpenCodeServerProjectDirectories
import com.intellij.platform.ai.agent.opencode.sessions.server.parseOpenCodeServerProjects
import com.intellij.platform.ai.agent.opencode.sessions.server.parseOpenCodeServerSessions
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class OpenCodeSessionSourceTest {
  @Test
  fun listsOpenCodeSessionsForProjectFromServerBackend(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val projectPath = tempDir.resolve("project").toString()
    val nestedPath = tempDir.resolve("nested").toString()
    val otherProjectPath = tempDir.resolve("other").toString()
    val transport = FakeOpenCodeServerTransport().apply {
      addProject(id = "project-1", worktree = projectPath, directories = listOf(projectPath, nestedPath))
      addProject(id = "project-2", worktree = otherProjectPath, directories = listOf(otherProjectPath))
      addSession(
        id = "old-session",
        directory = projectPath,
        title = "  Older\nthread  ",
        updatedAt = 100L,
      )
      addSession(
        id = "new-session",
        directory = projectPath,
        title = "New thread",
        updatedAt = 300L,
      )
      addSession(
        id = "worktree-session",
        directory = nestedPath,
        title = "Worktree session",
        updatedAt = 200L,
      )
      addSession(
        id = "other-session",
        directory = otherProjectPath,
        title = "Other thread",
        updatedAt = 400L,
      )
      addSession(
        id = "archived-session",
        directory = projectPath,
        title = "Archived thread",
        updatedAt = 500L,
        archivedAt = 600L,
      )
    }
    val source = OpenCodeSessionSource(OpenCodeSessionStore(backend = OpenCodeServerSessionBackend(transport)))

    val threads = source.listThreads("$projectPath/", openProject = null)

    assertThat(threads.map { it.id }).containsExactly("new-session", "worktree-session", "old-session")
    assertThat(threads.map { it.title }).containsExactly("New thread", "Worktree session", "Older thread")
    assertThat(threads.map { it.provider }).containsOnly(AgentSessionProvider.from("opencode"))
    assertThat(threads.map { it.archived }).containsOnly(false)
    assertThat(threads.map { it.activityReport.rowActivity }).containsOnly(AgentThreadActivity.READY)
  }

  @Test
  fun listsArchivedOpenCodeSessionsForProjectFromServerBackend(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val projectPath = tempDir.resolve("project").toString()
    val transport = FakeOpenCodeServerTransport().apply {
      addProject(id = "project-1", worktree = projectPath, directories = listOf(projectPath))
      addSession(
        id = "visible-session",
        directory = projectPath,
        title = "Visible thread",
        updatedAt = 100L,
      )
      addSession(
        id = "archived-session",
        directory = projectPath,
        title = "Archived thread",
        updatedAt = 300L,
        archivedAt = 400L,
      )
    }
    val source = OpenCodeSessionSource(OpenCodeSessionStore(backend = OpenCodeServerSessionBackend(transport)))

    val threads = source.listArchivedThreads(projectPath, openProject = null)

    assertThat(threads.map { it.id }).containsExactly("archived-session")
    assertThat(threads.single().archived).isTrue()
    assertThat(threads.single().provider).isEqualTo(AgentSessionProvider.from("opencode"))
  }

  @Test
  fun descriptorRenamesArchivesAndUnarchivesOpenCodeSessions(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val projectPath = tempDir.resolve("project").toString()
    val transport = FakeOpenCodeServerTransport().apply {
      addProject(id = "project-1", worktree = projectPath, directories = listOf(projectPath))
      addSession(
        id = "session-1",
        directory = projectPath,
        title = "Original title",
        updatedAt = 100L,
      )
    }
    val store = OpenCodeSessionStore(
      backend = OpenCodeServerSessionBackend(transport),
      timeProvider = { 1_000L },
    )
    val descriptor = OpenCodeAgentSessionProviderDescriptor(
      sessionStore = store,
      executableResolver = { "opencode" },
      cliAvailableProbe = { true },
    )

    assertThat(descriptor.supportsArchiveThread).isTrue()
    assertThat(descriptor.supportsUnarchiveThread).isTrue()
    assertThat(descriptor.threadRenameAction.invoke(projectPath, "session-1", "  Renamed\nthread  ")).isTrue()

    val renamedThread = descriptor.sessionSource.listThreads(projectPath, openProject = null).single()
    assertThat(renamedThread.title).isEqualTo("Renamed thread")
    assertThat(transport.titlePatches).containsExactly("session-1" to "Renamed thread")

    assertThat(descriptor.archiveThread(projectPath, "session-1")).isTrue()
    assertThat(transport.archivedPatches).containsExactly("session-1" to 1_000L)
    assertThat(descriptor.sessionSource.listThreads(projectPath, openProject = null)).isEmpty()
    val archivedSource = descriptor.sessionSource as AgentSessionArchivedSource
    assertThat(archivedSource.listArchivedThreads(projectPath, openProject = null).single().id).isEqualTo("session-1")

    assertThat(descriptor.unarchiveThread(projectPath, "session-1")).isTrue()
    assertThat(transport.archivedPatches).containsExactly("session-1" to 1_000L, "session-1" to null)
    assertThat(archivedSource.listArchivedThreads(projectPath, openProject = null)).isEmpty()
    assertThat(descriptor.sessionSource.listThreads(projectPath, openProject = null).single().id).isEqualTo("session-1")
  }

  @Test
  fun mutationIsScopedToProjectVisibleSessions(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val projectPath = tempDir.resolve("project").toString()
    val otherProjectPath = tempDir.resolve("other").toString()
    val transport = FakeOpenCodeServerTransport().apply {
      addProject(id = "project-1", worktree = projectPath, directories = listOf(projectPath))
      addProject(id = "project-2", worktree = otherProjectPath, directories = listOf(otherProjectPath))
      addSession(
        id = "other-session",
        directory = otherProjectPath,
        title = "Other thread",
        updatedAt = 100L,
      )
    }
    val store = OpenCodeSessionStore(backend = OpenCodeServerSessionBackend(transport))

    assertThat(store.renameThread(projectPath, "other-session", "Renamed")).isFalse()
    assertThat(store.archiveThread(projectPath, "other-session")).isFalse()
    assertThat(store.unarchiveThread(projectPath, "other-session")).isFalse()

    assertThat(transport.titlePatches).isEmpty()
    assertThat(transport.archivedPatches).isEmpty()
  }

  @Test
  fun returnsEmptyListWhenServerUnavailable(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val source = OpenCodeSessionSource(OpenCodeSessionStore(backend = FailingOpenCodeSessionBackend))

    assertThat(source.listThreads(tempDir.toString(), openProject = null)).isEmpty()
    assertThat(source.listArchivedThreads(tempDir.toString(), openProject = null)).isEmpty()
  }

  @Test
  fun parsesOpenCodeServerHttpResponses() {
    val sessions = parseOpenCodeServerSessions(
      """
      [
        {
          "id": " session-1 ",
          "title": "First",
          "directory": "/work/project",
          "time": {"created": 10, "updated": 20, "archived": null}
        },
        {
          "id": "archived-session",
          "title": "Archived",
          "directory": "/work/project",
          "time": {"updated": "30", "archived": 40}
        },
        {
          "id": "unarchived-zero",
          "directory": "/work/project",
          "time": {"updated": 50, "archived": 0}
        }
      ]
      """.trimIndent(),
    )

    assertThat(sessions.map { it.id }).containsExactly("session-1", "archived-session", "unarchived-zero")
    assertThat(sessions[0].title).isEqualTo("First")
    assertThat(sessions[0].directory).isEqualTo("/work/project")
    assertThat(sessions[0].updatedAt).isEqualTo(20L)
    assertThat(sessions[0].archivedAt).isNull()
    assertThat(sessions[1].updatedAt).isEqualTo(30L)
    assertThat(sessions[1].archivedAt).isEqualTo(40L)
    assertThat(sessions[2].archivedAt).isNull()

    val projects = parseOpenCodeServerProjects(
      """
      {"data": [
        {"id": "project-1", "worktree": "/work/project"},
        {"id": "project-2"}
      ]}
      """.trimIndent(),
    )
    assertThat(projects).containsExactly(
      OpenCodeServerProject(id = "project-1", worktree = "/work/project"),
      OpenCodeServerProject(id = "project-2", worktree = null),
    )

    val directories = parseOpenCodeServerProjectDirectories(
      """
      {"directories": [
        "/work/project",
        {"directory": "/work/project/nested"},
        {"path": "/work/project/linked"}
      ]}
      """.trimIndent(),
    )
    assertThat(directories).containsExactly("/work/project", "/work/project/nested", "/work/project/linked")
  }

  @Test
  fun openCodeHttpClientBypassesDefaultProxyForLoopbackServer() {
    val previousProxySelector = ProxySelector.getDefault()
    val proxySelections = AtomicInteger()
    val proxySelector = object : ProxySelector() {
      override fun select(uri: URI): List<Proxy> {
        proxySelections.incrementAndGet()
        return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 9)))
      }

      override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
      }
    }
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/health") { exchange ->
      val body = "ok".toByteArray(StandardCharsets.UTF_8)
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { responseBody -> responseBody.write(body) }
    }
    server.start()

    try {
      ProxySelector.setDefault(proxySelector)
      val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:${server.address.port}/health"))
        .timeout(Duration.ofSeconds(5))
        .GET()
        .build()

      val response = createOpenCodeHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))

      assertThat(response.statusCode()).isEqualTo(200)
      assertThat(response.body()).isEqualTo("ok")
      assertThat(proxySelections.get()).isZero()
    }
    finally {
      ProxySelector.setDefault(previousProxySelector)
      server.stop(0)
    }
  }
}

private object FailingOpenCodeSessionBackend : OpenCodeSessionBackend {
  override suspend fun loadEntries(normalizedProjectPath: String, archived: Boolean): List<OpenCodeSessionIndexEntry> {
    error("server unavailable")
  }

  override suspend fun renameSession(normalizedProjectPath: String, sessionId: String, title: String): Boolean {
    error("server unavailable")
  }

  override suspend fun archiveSession(normalizedProjectPath: String, sessionId: String, nowMs: Long): Boolean {
    error("server unavailable")
  }

  override suspend fun unarchiveSession(normalizedProjectPath: String, sessionId: String): Boolean {
    error("server unavailable")
  }
}

private class FakeOpenCodeServerTransport : OpenCodeServerTransport {
  private val projectsById = LinkedHashMap<String, OpenCodeServerProject>()
  private val directoriesByProjectId = LinkedHashMap<String, List<String>>()
  private val sessionsById = LinkedHashMap<String, MutableOpenCodeServerSession>()

  val titlePatches = ArrayList<Pair<String, String>>()
  val archivedPatches = ArrayList<Pair<String, Long?>>()

  fun addProject(id: String, worktree: String, directories: List<String>) {
    projectsById[id] = OpenCodeServerProject(id = id, worktree = worktree)
    directoriesByProjectId[id] = directories
  }

  fun addSession(
    id: String,
    directory: String,
    title: String,
    updatedAt: Long,
    archivedAt: Long? = null,
  ) {
    sessionsById[id] = MutableOpenCodeServerSession(
      id = id,
      title = title,
      directory = directory,
      updatedAt = updatedAt,
      archivedAt = archivedAt,
    )
  }

  override suspend fun listSessions(directory: String, limit: Int?): List<OpenCodeServerSession> {
    val normalizedDirectory = normalizeOpenCodeProjectPath(directory)
    val sessions = sessionsById.values
      .asSequence()
      .filter { session -> normalizeOpenCodeProjectPath(session.directory) == normalizedDirectory }
      .map(MutableOpenCodeServerSession::toServerSession)
      .sortedByDescending(OpenCodeServerSession::updatedAt)
      .toList()
    return limit?.takeIf { it > 0 }?.let(sessions::take) ?: sessions
  }

  override suspend fun listProjects(): List<OpenCodeServerProject> {
    return projectsById.values.toList()
  }

  override suspend fun listProjectDirectories(projectId: String): List<String> {
    return directoriesByProjectId[projectId].orEmpty()
  }

  override suspend fun patchSessionTitle(id: String, title: String): Boolean {
    titlePatches.add(id to title)
    val session = sessionsById[id] ?: return false
    session.title = title
    return true
  }

  override suspend fun patchSessionArchived(id: String, archivedEpochMs: Long?): Boolean {
    archivedPatches.add(id to archivedEpochMs)
    val session = sessionsById[id] ?: return false
    session.archivedAt = archivedEpochMs?.takeIf { it > 0L }
    if (archivedEpochMs != null && archivedEpochMs > 0L) {
      session.updatedAt = archivedEpochMs
    }
    return true
  }

  override fun shutdown() {
  }
}

private data class MutableOpenCodeServerSession(
  @JvmField val id: String,
  @JvmField var title: String?,
  @JvmField val directory: String,
  @JvmField var updatedAt: Long,
  @JvmField var archivedAt: Long?,
) {
  fun toServerSession(): OpenCodeServerSession {
    return OpenCodeServerSession(
      id = id,
      title = title,
      directory = directory,
      updatedAt = updatedAt,
      archivedAt = archivedAt,
    )
  }
}
