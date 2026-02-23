// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.codex.common.CodexAppServerClient
import com.intellij.agent.workbench.codex.common.CodexAppServerException
import com.intellij.agent.workbench.codex.common.CodexCliNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

class CodexAppServerClientTest {
  companion object {
    @JvmStatic
    fun backends(): List<CodexBackend> {
      return listOf(
        createMockBackendDefinition(),
        createRealBackendDefinition(),
      )
    }
  }

  @TempDir
  lateinit var tempDir: Path

  @ParameterizedTest(name = "{0}")
  @MethodSource("backends")
  fun listThreadsUsesCodexAppServerBackends(backend: CodexBackend): Unit = runBlocking(Dispatchers.IO) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(id = "thread-1", title = "First session", updatedAt = 1_700_000_000_000L, archived = false),
        ThreadSpec(id = "thread-2", title = "Second session", updatedAt = 1_700_000_010_000L, archived = false),
        ThreadSpec(id = "thread-3", title = "Archived session", updatedAt = 1_699_999_000_000L, archived = true),
      )
    )
    backend.run(scope = this, tempDir = tempDir, configPath = configPath)
  }

  @Test
  fun listThreadsFiltersThreadsByCwdFilter(): Unit = runBlocking(Dispatchers.IO) {
    val projectA = tempDir.resolve("project-alpha")
    val projectB = tempDir.resolve("project-beta")
    Files.createDirectories(projectA)
    Files.createDirectories(projectB)
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "alpha-1",
          title = "Alpha",
          cwd = projectA.toString(),
          updatedAt = 1_700_000_000_000L,
          archived = false,
        ),
        ThreadSpec(
          id = "beta-1",
          title = "Beta",
          cwd = projectB.toString(),
          updatedAt = 1_700_000_100_000L,
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-cwd")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val threadsA = client.listThreads(archived = false, cwdFilter = projectA.toString().replace('\\', '/').trimEnd('/'))
      val threadsB = client.listThreads(archived = false, cwdFilter = projectB.toString().replace('\\', '/').trimEnd('/'))
      val threadsAll = client.listThreads(archived = false)
      assertThat(threadsA.map { it.id }).containsExactly("alpha-1")
      assertThat(threadsB.map { it.id }).containsExactly("beta-1")
      assertThat(threadsAll.map { it.id }).containsExactlyInAnyOrder("alpha-1", "beta-1")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun listThreadsPageSupportsCursorAndLimit(): Unit = runBlocking(Dispatchers.IO) {
    val workingDir = tempDir.resolve("project-page")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(id = "thread-1", title = "Thread 1", cwd = workingDir.toString(), updatedAt = 1_700_000_005_000L, archived = false),
        ThreadSpec(id = "thread-2", title = "Thread 2", cwd = workingDir.toString(), updatedAt = 1_700_000_004_000L, archived = false),
        ThreadSpec(id = "thread-3", title = "Thread 3", cwd = workingDir.toString(), updatedAt = 1_700_000_003_000L, archived = false),
        ThreadSpec(id = "thread-4", title = "Thread 4", cwd = workingDir.toString(), updatedAt = 1_700_000_002_000L, archived = false),
        ThreadSpec(id = "thread-5", title = "Thread 5", cwd = workingDir.toString(), updatedAt = 1_700_000_001_000L, archived = false),
      )
    )
    val backendDir = tempDir.resolve("backend-page")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val cwdFilter = workingDir.toString().replace('\\', '/').trimEnd('/')
      val first = client.listThreadsPage(archived = false, cursor = null, limit = 2, cwdFilter = cwdFilter)
      assertThat(first.threads.map { it.id }).containsExactly("thread-1", "thread-2")
      assertThat(first.nextCursor).isEqualTo("2")

      val second = client.listThreadsPage(archived = false, cursor = first.nextCursor, limit = 2, cwdFilter = cwdFilter)
      assertThat(second.threads.map { it.id }).containsExactly("thread-3", "thread-4")
      assertThat(second.nextCursor).isEqualTo("4")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun listThreadsParsesPreviewAndTimestampVariants(): Unit = runBlocking(Dispatchers.IO) {
    val workingDir = tempDir.resolve("project-preview")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    val longPreview = "x".repeat(160)
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-preview",
          preview = longPreview,
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_000L,
          updatedAtField = "updated_at",
          archived = false,
        ),
        ThreadSpec(
          id = "thread-name",
          name = "Named thread",
          cwd = workingDir.toString(),
          createdAt = 1_700_000_500_000L,
          createdAtField = "createdAt",
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-preview")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val cwdFilter = workingDir.toString().replace('\\', '/').trimEnd('/')
      val threads = client.listThreads(archived = false, cwdFilter = cwdFilter)
      val threadsById = threads.associateBy { it.id }
      val previewThread = threadsById.getValue("thread-preview")
      assertThat(previewThread.updatedAt).isEqualTo(1_700_000_000_000L)
      assertThat(previewThread.title).endsWith("...")
      assertThat(previewThread.title.length).isLessThan(longPreview.length)
      val namedThread = threadsById.getValue("thread-name")
      assertThat(namedThread.title).isEqualTo("Named thread")
      assertThat(namedThread.updatedAt).isEqualTo(1_700_000_500_000L)
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun listThreadsFailsOnServerError(): Unit = runBlocking(Dispatchers.IO) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-err",
          title = "Thread",
          updatedAt = 1_700_000_000_000L,
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-error")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      environmentOverrides = mapOf(
        "CODEX_TEST_ERROR_METHOD" to "thread/list",
        "CODEX_TEST_ERROR_MESSAGE" to "boom",
      ),
    )
    try {
      try {
        client.listThreads(archived = false)
        fail("Expected CodexAppServerException")
      }
      catch (e: CodexAppServerException) {
        assertThat(e.message).contains("boom")
      }
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun createThreadStartsNewThreadAndAddsItToList(): Unit = runBlocking(Dispatchers.IO) {
    val workingDir = tempDir.resolve("project-start")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-old",
          title = "Old Thread",
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_000_000L,
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-start")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val created = client.createThread()
      assertThat(created.id).startsWith("thread-start-")
      assertThat(created.archived).isFalse()
      assertThat(created.title).isNotBlank()

      val active = client.listThreads(archived = false)
      assertThat(active.first().id).isEqualTo(created.id)
      assertThat(active.map { it.id }).contains("thread-old")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun createThreadWithParamsPassesThemThrough(): Unit = runBlocking(Dispatchers.IO) {
    val workingDir = tempDir.resolve("project-start-params")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-start-params")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val created = client.createThread(
        cwd = workingDir.toString(),
        approvalPolicy = "on-request",
        sandbox = "workspace-write",
      )
      assertThat(created.id).startsWith("thread-start-")
      assertThat(created.archived).isFalse()
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun archiveThreadMovesThreadFromActiveToArchivedList(): Unit = runBlocking(Dispatchers.IO) {
    val workingDir = tempDir.resolve("project-archive")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-1",
          title = "Thread 1",
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_005_000L,
          archived = false,
        ),
        ThreadSpec(
          id = "thread-2",
          title = "Thread 2",
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_004_000L,
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-archive")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val beforeArchive = client.listThreads(archived = false)
      assertThat(beforeArchive.map { it.id }).contains("thread-1", "thread-2")

      client.archiveThread("thread-1")

      val active = client.listThreads(archived = false)
      val archived = client.listThreads(archived = true)
      assertThat(active.map { it.id }).doesNotContain("thread-1")
      assertThat(archived.map { it.id }).contains("thread-1")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun unarchiveThreadMovesThreadFromArchivedToActiveList(): Unit = runBlocking(Dispatchers.IO) {
    val workingDir = tempDir.resolve("project-unarchive")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-1",
          title = "Thread 1",
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_005_000L,
          archived = true,
        ),
        ThreadSpec(
          id = "thread-2",
          title = "Thread 2",
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_004_000L,
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-unarchive")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
    )
    try {
      val beforeUnarchive = client.listThreads(archived = true)
      assertThat(beforeUnarchive.map { it.id }).contains("thread-1")

      client.unarchiveThread("thread-1")

      val active = client.listThreads(archived = false)
      val archived = client.listThreads(archived = true)
      assertThat(active.map { it.id }).contains("thread-1", "thread-2")
      assertThat(archived.map { it.id }).doesNotContain("thread-1")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun idleTimeoutStopsLazyStartedProcess(): Unit = runBlocking(Dispatchers.IO) {
    val workingDir = tempDir.resolve("project-idle-timeout")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-1",
          title = "Thread 1",
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_005_000L,
          archived = false,
        ),
      ),
    )
    val backendDir = tempDir.resolve("backend-idle-timeout")
    Files.createDirectories(backendDir)
    val marker = backendDir.resolve("cwd-marker.txt")
    val codexShim = createMockCodexShim(backendDir, configPath)
    val client = CodexAppServerClient(
      coroutineScope = this,
      executablePathProvider = { codexShim.toString() },
      environmentOverrides = mapOf("CODEX_TEST_CWD_MARKER" to marker.toString()),
      idleShutdownTimeoutMs = 100,
    )
    try {
      client.listThreads(archived = false)
      assertThat(Files.exists(marker)).isTrue()
      Files.deleteIfExists(marker)

      delay(250.milliseconds)
      client.listThreads(archived = false)

      assertThat(Files.exists(marker))
        .describedAs("marker file should be recreated when app-server restarts after idle timeout")
        .isTrue()
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun persistThreadSendsTurnStartWithoutInterrupt(): Unit = runBlocking(Dispatchers.IO) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-persist")
    Files.createDirectories(backendDir)
    val requestLogPath = backendDir.resolve("requests.log")
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      environmentOverrides = mapOf(
        "CODEX_TEST_REQUEST_LOG" to requestLogPath.toString(),
      ),
    )
    try {
      val created = client.createThread()
      client.persistThread(created.id)

      val methods = Files.readAllLines(requestLogPath)
      assertThat(methods).contains("turn/start")
      assertThat(methods).doesNotContain("turn/interrupt")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun createThreadFailsOnServerError(): Unit = runBlocking(Dispatchers.IO) {
    val configPath = tempDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-start-error")
    Files.createDirectories(backendDir)
    val client = createMockClient(
      scope = this,
      tempDir = backendDir,
      configPath = configPath,
      environmentOverrides = mapOf(
        "CODEX_TEST_ERROR_METHOD" to "thread/start",
        "CODEX_TEST_ERROR_MESSAGE" to "boom",
      ),
    )
    try {
      try {
        client.createThread()
        fail("Expected CodexAppServerException")
      }
      catch (e: CodexAppServerException) {
        assertThat(e.message).contains("boom")
      }
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun listThreadsUsesPathOverrideWhenExecutableProviderMissing(): Unit = runBlocking(Dispatchers.IO) {
    val workingDir = tempDir.resolve("project-path-override")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(
      path = configPath,
      threads = listOf(
        ThreadSpec(
          id = "thread-path",
          title = "Path Thread",
          cwd = workingDir.toString(),
          updatedAt = 1_700_000_000_000L,
          archived = false,
        ),
      ),
    )

    val backendDir = tempDir.resolve("backend-path-override")
    Files.createDirectories(backendDir)
    val codexShim = createMockCodexShim(backendDir, configPath)
    val client = CodexAppServerClient(
      coroutineScope = this,
      executablePathProvider = { null },
      environmentOverrides = mapOf("PATH" to codexShim.parent.toString()),
      workingDirectory = workingDir,
    )
    try {
      val threads = client.listThreads(archived = false)
      assertThat(threads.map { it.id }).containsExactly("thread-path")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun listThreadsFailsWithoutFallbackWhenConfiguredExecutableIsInvalid(): Unit = runBlocking(Dispatchers.IO) {
    val workingDir = tempDir.resolve("project-invalid-exec")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val backendDir = tempDir.resolve("backend-invalid-exec")
    Files.createDirectories(backendDir)
    val codexShim = createMockCodexShim(backendDir, configPath)
    val invalidExecutable = tempDir.resolve("missing-codex").toString()
    val client = CodexAppServerClient(
      coroutineScope = this,
      executablePathProvider = { invalidExecutable },
      environmentOverrides = mapOf("PATH" to codexShim.parent.toString()),
      workingDirectory = workingDir,
    )
    try {
      try {
        client.listThreads(archived = false)
        fail("Expected CodexAppServerException")
      }
      catch (e: CodexAppServerException) {
        assertThat(e.message).contains(invalidExecutable)
      }
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun listThreadsReportsDefaultExecutableStartFailuresWithoutCliMissingError(): Unit = runBlocking(Dispatchers.IO) {
    val workingDir = tempDir.resolve("project-invalid-env")
    Files.createDirectories(workingDir)
    val configPath = workingDir.resolve("codex-config.json")
    writeConfig(path = configPath, threads = emptyList())

    val client = CodexAppServerClient(
      coroutineScope = this,
      executablePathProvider = { null },
      environmentOverrides = mapOf("INVALID=KEY" to "value"),
      workingDirectory = workingDir,
    )
    try {
      try {
        client.listThreads(archived = false)
        fail("Expected CodexAppServerException")
      }
      catch (e: CodexAppServerException) {
        assertThat(e).isNotInstanceOf(CodexCliNotFoundException::class.java)
        assertThat(e.message).contains("Failed to start Codex app-server from codex")
      }
    }
    finally {
      client.shutdown()
    }
  }
}
