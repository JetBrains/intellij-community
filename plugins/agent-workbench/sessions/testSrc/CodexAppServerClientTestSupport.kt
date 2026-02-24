// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.fasterxml.jackson.core.JsonFactory
import com.intellij.agent.workbench.codex.common.CodexAppServerClient
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.system.OS
import kotlinx.coroutines.CoroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

interface CodexBackend {
  val name: String

  suspend fun createClient(
    scope: CoroutineScope,
    tempDir: Path,
    configPath: Path,
  ): CodexAppServerClient

  suspend fun run(scope: CoroutineScope, tempDir: Path, configPath: Path)
}

internal fun createMockBackendDefinition(): CodexBackend {
  return object : CodexBackend {
    override val name: String
      get() = "mock"

    override suspend fun createClient(
      scope: CoroutineScope,
      tempDir: Path,
      configPath: Path,
    ): CodexAppServerClient {
      return createMockClient(
        scope = scope,
        tempDir = tempDir,
        configPath = configPath,
      )
    }

    override suspend fun run(scope: CoroutineScope, tempDir: Path, configPath: Path) {
      val client = createClient(
        scope = scope,
        tempDir = tempDir,
        configPath = configPath,
      )
      try {
        assertThreads(
          backendName = name,
          client = client,
          expectedActiveIds = listOf("thread-2", "thread-1"),
          expectedArchivedIds = listOf("thread-3"),
        )
      }
      finally {
        client.shutdown()
      }
    }

    override fun toString(): String = name
  }
}

internal fun createRealBackendDefinition(): CodexBackend {
  return object : CodexBackend {
    override val name: String
      get() = "real"

    override suspend fun createClient(
      scope: CoroutineScope,
      tempDir: Path,
      configPath: Path,
    ): CodexAppServerClient {
      val codexBinary = resolveCodexBinary()
      assumeTrue(codexBinary != null, "Codex CLI not found. Set CODEX_BIN or ensure codex is on PATH.")

      val codexHome = createCodexHome(tempDir)
      return CodexAppServerClient(
        scope,
        executablePathProvider = { codexBinary!! },
        environmentOverrides = mapOf("CODEX_HOME" to codexHome.toString()),
      )
    }

    override suspend fun run(scope: CoroutineScope, tempDir: Path, configPath: Path) {
      val client = createClient(
        scope = scope,
        tempDir = tempDir,
        configPath = configPath,
      )
      try {
        assertThreads(
          backendName = name,
          client = client,
          expectedActiveIds = null,
          expectedArchivedIds = null,
        )
      }
      finally {
        client.shutdown()
      }
    }

    override fun toString(): String = name
  }
}

internal fun writeConfig(path: Path, threads: List<ThreadSpec>) {
  val jsonFactory = JsonFactory()
  Files.newBufferedWriter(path, StandardCharsets.UTF_8).use { writer ->
    jsonFactory.createGenerator(writer).use { generator ->
      generator.writeStartObject()
      generator.writeFieldName("threads")
      generator.writeStartArray()
      for (thread in threads) {
        generator.writeStartObject()
        generator.writeStringField("id", thread.id)
        thread.title?.let { generator.writeStringField("title", it) }
        thread.preview?.let { generator.writeStringField("preview", it) }
        thread.name?.let { generator.writeStringField("name", it) }
        thread.summary?.let { generator.writeStringField("summary", it) }
        thread.cwd?.let { generator.writeStringField("cwd", it) }
        generator.writeStringField("sourceKind", thread.sourceKind)
        if (thread.sourceAsString) {
          generator.writeBooleanField("sourceAsString", true)
        }
        if (thread.sourceSubAgentFieldName != "subAgent") {
          generator.writeStringField("sourceSubAgentFieldName", thread.sourceSubAgentFieldName)
        }
        thread.parentThreadId?.let { generator.writeStringField("parentThreadId", it) }
        thread.agentNickname?.let { generator.writeStringField("agentNickname", it) }
        thread.agentRole?.let { generator.writeStringField("agentRole", it) }
        generator.writeStringField("statusType", thread.statusType)
        if (thread.statusActiveFlagsFieldName != "activeFlags") {
          generator.writeStringField("statusActiveFlagsFieldName", thread.statusActiveFlagsFieldName)
        }
        if (thread.activeFlags.isNotEmpty()) {
          generator.writeFieldName("activeFlags")
          generator.writeStartArray()
          thread.activeFlags.forEach(generator::writeString)
          generator.writeEndArray()
        }
        thread.gitBranch?.let { generator.writeStringField("gitBranch", it) }
        thread.updatedAt?.let { updatedAt ->
          val field = thread.updatedAtField.takeIf { it.isNotBlank() } ?: "updated_at"
          generator.writeNumberField(field, updatedAt)
        }
        thread.createdAt?.let { createdAt ->
          val field = thread.createdAtField.takeIf { it.isNotBlank() } ?: "created_at"
          generator.writeNumberField(field, createdAt)
        }
        generator.writeBooleanField("archived", thread.archived)
        generator.writeEndObject()
      }
      generator.writeEndArray()
      generator.writeEndObject()
    }
  }
}

internal data class ThreadSpec(
  val id: String,
  val title: String? = null,
  val preview: String? = null,
  val name: String? = null,
  val summary: String? = null,
  val cwd: String? = null,
  val updatedAt: Long? = null,
  val createdAt: Long? = null,
  val updatedAtField: String = "updated_at",
  val createdAtField: String = "created_at",
  val sourceKind: String = "cli",
  val sourceAsString: Boolean = false,
  val sourceSubAgentFieldName: String = "subAgent",
  val parentThreadId: String? = null,
  val agentNickname: String? = null,
  val agentRole: String? = null,
  val statusType: String = "idle",
  val statusActiveFlagsFieldName: String = "activeFlags",
  val activeFlags: List<String> = emptyList(),
  val gitBranch: String? = null,
  val archived: Boolean = false,
)

internal fun createMockClient(
  scope: CoroutineScope,
  tempDir: Path,
  configPath: Path,
  environmentOverrides: Map<String, String> = emptyMap(),
): CodexAppServerClient {
  val codexPath = createCodexShim(tempDir, configPath)
  return CodexAppServerClient(
    scope,
    executablePathProvider = { codexPath.toString() },
    environmentOverrides = environmentOverrides,
  )
}

internal fun createMockCodexShim(tempDir: Path, configPath: Path): Path {
  return createCodexShim(tempDir, configPath)
}

private fun createCodexShim(tempDir: Path, configPath: Path): Path {
  val javaHome = System.getProperty("java.home")
  val javaBin = Path.of(javaHome, "bin", if (OS.CURRENT == OS.Windows) "java.exe" else "java")
  val classpath = resolveTestClasspath()
  val argsFile = writeAppServerArgsFile(tempDir, classpath, configPath)
  return if (OS.CURRENT == OS.Windows) {
    val script = tempDir.resolve("codex.cmd")
    script.writeText(
      """
      @echo off
      "${javaBin}" "@${argsFile}"
      """.trimIndent()
    )
    NioFiles.setExecutable(script)
    script
  }
  else {
    val script = tempDir.resolve("codex")
    val quotedJava = quoteForShell(javaBin.toString())
    val quotedArgsFile = quoteForShell("@${argsFile}")
    script.writeText(
      """
      #!/bin/sh
      exec $quotedJava $quotedArgsFile
      """.trimIndent()
    )
    NioFiles.setExecutable(script)
    script
  }
}

private fun resolveTestClasspath(): String {
  val classpathFile = System.getProperty("classpath.file")
  if (!classpathFile.isNullOrBlank()) {
    val entries = Files.readAllLines(Path.of(classpathFile), StandardCharsets.UTF_8)
      .map(String::trim)
      .filter(String::isNotEmpty)
    if (entries.isNotEmpty()) {
      return absolutizeClasspathEntries(entries)
    }
  }
  val classpath = System.getProperty("java.class.path")
  val entries = classpath.split(File.pathSeparator)
    .map(String::trim)
    .filter(String::isNotEmpty)
  return absolutizeClasspathEntries(entries)
}

private fun absolutizeClasspathEntries(entries: List<String>): String {
  return entries.joinToString(File.pathSeparator) { entry ->
    val path = Path.of(entry)
    if (path.isAbsolute) entry else path.toAbsolutePath().normalize().toString()
  }
}

private fun writeAppServerArgsFile(tempDir: Path, classpath: String, configPath: Path): Path {
  val argsFile = tempDir.resolve("codex-app-server.args")
  val args = listOf("-cp", classpath, TEST_APP_SERVER_MAIN_CLASS, configPath.toString())
  Files.newBufferedWriter(argsFile, StandardCharsets.UTF_8).use { writer ->
    for (arg in args) {
      writer.write(CommandLineWrapperUtil.quoteArg(arg))
      writer.newLine()
    }
  }
  return argsFile
}

private fun quoteForShell(value: String): String {
  return "'" + value.replace("'", "'\"'\"'") + "'"
}

private suspend fun assertThreads(
  backendName: String,
  client: CodexAppServerClient,
  expectedActiveIds: List<String>?,
  expectedArchivedIds: List<String>?,
) {
  val active = client.listThreads(archived = false)
  val archived = client.listThreads(archived = true)
  val firstPage = client.listThreadsPage(archived = false, cursor = null, limit = 2)

  assertThat(active).describedAs("$backendName active threads").allMatch { !it.archived }
  assertThat(archived).describedAs("$backendName archived threads").allMatch { it.archived }
  assertThat(firstPage.threads).describedAs("$backendName paged active threads").allMatch { !it.archived }
  assertThat(firstPage.threads.size).describedAs("$backendName paged active page size").isLessThanOrEqualTo(2)

  val comparator = Comparator.comparingLong<CodexThread> { it.updatedAt }.reversed()
  assertThat(active).describedAs("$backendName active sort").isSortedAccordingTo(comparator)
  assertThat(archived).describedAs("$backendName archived sort").isSortedAccordingTo(comparator)

  if (expectedActiveIds != null) {
    assertThat(active.map { it.id })
      .describedAs("$backendName active ids")
      .containsExactlyElementsOf(expectedActiveIds)
    assertThat(firstPage.threads.map { it.id })
      .describedAs("$backendName paged active ids")
      .containsExactlyElementsOf(expectedActiveIds.take(2))
  }
  if (expectedArchivedIds != null) {
    assertThat(archived.map { it.id })
      .describedAs("$backendName archived ids")
      .containsExactlyElementsOf(expectedArchivedIds)
  }
}

private fun resolveCodexBinary(): String? {
  val configured = System.getenv("CODEX_BIN")?.takeIf { it.isNotBlank() }
  return configured ?: PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS("codex")?.absolutePath
}

private fun createCodexHome(tempDir: Path): Path {
  val codexHome = tempDir.resolve("codex-home")
  Files.createDirectories(codexHome)
  writeCodexConfig(codexHome.resolve("config.toml"))
  return codexHome
}

private fun writeCodexConfig(configPath: Path) {
  val lines = mutableListOf<String>()
  val model = System.getenv("CODEX_MODEL")?.takeIf { it.isNotBlank() } ?: DEFAULT_TEST_MODEL
  val reasoningEffort = System.getenv("CODEX_REASONING_EFFORT")?.takeIf { it.isNotBlank() } ?: DEFAULT_TEST_REASONING_EFFORT
  lines.add("model = \"$model\"")
  lines.add("model_reasoning_effort = \"$reasoningEffort\"")
  lines.add("approval_policy = \"never\"")
  lines.add("cli_auth_credentials_store = \"file\"")
  lines.add("")
  configPath.writeText(lines.joinToString("\n"))
}

private const val DEFAULT_TEST_MODEL = "gpt-4o-mini"
private const val DEFAULT_TEST_REASONING_EFFORT = "low"
private const val TEST_APP_SERVER_MAIN_CLASS = "com.intellij.agent.workbench.sessions.CodexTestAppServer"
