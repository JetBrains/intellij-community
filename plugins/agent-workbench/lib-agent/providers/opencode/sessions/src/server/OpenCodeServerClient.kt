// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.opencode.sessions.server

import com.intellij.agent.workbench.json.createJsonGenerator
import com.intellij.agent.workbench.json.createJsonParser
import com.intellij.agent.workbench.json.forEachJsonObjectField
import com.intellij.agent.workbench.json.readJsonLongOrNull
import com.intellij.agent.workbench.json.readJsonStringOrNull
import com.intellij.agent.workbench.opencode.sessions.OpenCodeCliSupport
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.StringWriter
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val HTTP_REQUEST_TIMEOUT_MS = 30_000L
private const val SERVER_STARTUP_TIMEOUT_MS = 10_000L
private const val SERVER_READY_POLL_INTERVAL_MS = 100L
private const val PROCESS_TERMINATION_TIMEOUT_MS = 2_000L

private val LOG = logger<OpenCodeServerClient>()

internal interface OpenCodeServerTransport {
  suspend fun listSessions(directory: String, limit: Int? = null): List<OpenCodeServerSession>
  suspend fun listProjects(): List<OpenCodeServerProject>
  suspend fun listProjectDirectories(projectId: String): List<String>
  suspend fun patchSessionTitle(id: String, title: String): Boolean
  suspend fun patchSessionArchived(id: String, archivedEpochMs: Long?): Boolean
  fun shutdown()
}

internal class OpenCodeServerClient(
  private val coroutineScope: CoroutineScope,
  private val executablePathProvider: suspend () -> String? = { OpenCodeCliSupport.findExecutableViaTerminalResolver() },
  private val environmentOverrides: Map<String, String> = emptyMap(),
  workingDirectory: Path? = null,
) : OpenCodeServerTransport {
  private val startMutex = Mutex()
  private val workingDirectoryPath = workingDirectory
  private val httpClient = createOpenCodeHttpClient()
  private val jsonFactory = JsonFactory()

  @Volatile
  private var process: Process? = null

  @Volatile
  private var baseUrl: String? = null

  private var stdoutJob: Job? = null
  private var stderrJob: Job? = null
  private var waitJob: Job? = null

  override suspend fun listSessions(directory: String, limit: Int?): List<OpenCodeServerSession> {
    val normalizedDirectory = directory.trim().takeIf { it.isNotEmpty() } ?: return emptyList()
    val limitQuery = limit?.takeIf { it > 0 }?.let { "&limit=$it" }.orEmpty()
    return httpGet(
      pathAndQuery = "/session?directory=${urlEncode(normalizedDirectory)}$limitQuery",
      parseBody = ::parseOpenCodeServerSessions,
    )
  }

  override suspend fun listProjects(): List<OpenCodeServerProject> {
    return httpGet(
      pathAndQuery = "/project",
      parseBody = ::parseOpenCodeServerProjects,
    )
  }

  override suspend fun listProjectDirectories(projectId: String): List<String> {
    val normalizedProjectId = projectId.trim().takeIf { it.isNotEmpty() } ?: return emptyList()
    return httpGet(
      pathAndQuery = "/project/${urlEncode(normalizedProjectId)}/directories",
      parseBody = ::parseOpenCodeServerProjectDirectories,
    )
  }

  override suspend fun patchSessionTitle(id: String, title: String): Boolean {
    val normalizedId = id.trim().takeIf { it.isNotEmpty() } ?: return false
    return httpPatchJson("/session/${urlEncode(normalizedId)}") { generator ->
      generator.writeStartObject()
      generator.writeName("title")
      generator.writeString(title)
      generator.writeEndObject()
    }
  }

  override suspend fun patchSessionArchived(id: String, archivedEpochMs: Long?): Boolean {
    val normalizedId = id.trim().takeIf { it.isNotEmpty() } ?: return false
    return httpPatchJson("/session/${urlEncode(normalizedId)}") { generator ->
      generator.writeStartObject()
      generator.writeName("time")
      generator.writeStartObject()
      generator.writeName("archived")
      generator.writeNumber(archivedEpochMs ?: 0L)
      generator.writeEndObject()
      generator.writeEndObject()
    }
  }

  override fun shutdown() {
    stopProcess()
  }

  private suspend fun <T> httpGet(pathAndQuery: String, parseBody: (String) -> T): T {
    val request = HttpRequest.newBuilder(URI.create(ensureBaseUrl() + pathAndQuery))
      .timeout(Duration.ofMillis(HTTP_REQUEST_TIMEOUT_MS))
      .GET()
      .build()
    val response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    val statusCode = response.statusCode()
    if (statusCode !in 200..299) {
      throw OpenCodeServerException("OpenCode server GET $pathAndQuery failed with HTTP $statusCode")
    }
    return try {
      parseBody(response.body())
    }
    catch (t: Throwable) {
      if (t is CancellationException) throw t
      throw OpenCodeServerException("Failed to parse OpenCode server response from $pathAndQuery", t)
    }
  }

  private suspend fun httpPatchJson(path: String, bodyWriter: (JsonGenerator) -> Unit): Boolean {
    val request = HttpRequest.newBuilder(URI.create(ensureBaseUrl() + path))
      .timeout(Duration.ofMillis(HTTP_REQUEST_TIMEOUT_MS))
      .header("Content-Type", "application/json")
      .method("PATCH", HttpRequest.BodyPublishers.ofString(writeJson(bodyWriter), StandardCharsets.UTF_8))
      .build()
    val response = sendHttpRequest(request, HttpResponse.BodyHandlers.discarding())
    return response.statusCode() in 200..299
  }

  private suspend fun <T> sendHttpRequest(
    request: HttpRequest,
    bodyHandler: HttpResponse.BodyHandler<T>,
  ): HttpResponse<T> {
    return try {
      runInterruptible {
        httpClient.send(request, bodyHandler)
      }
    }
    catch (t: Throwable) {
      if (t is CancellationException) throw t
      currentCoroutineContext().ensureActive()
      stopProcess()
      throw OpenCodeServerException("Failed to send request to OpenCode server: ${request.method()} ${request.uri()}", t)
    }
  }

  private fun writeJson(bodyWriter: (JsonGenerator) -> Unit): String {
    val output = StringWriter()
    jsonFactory.createJsonGenerator(output).use(bodyWriter)
    return output.toString()
  }

  private suspend fun ensureBaseUrl(): String {
    val currentProcess = process
    val currentBaseUrl = baseUrl
    if (currentProcess != null && currentProcess.isAlive && currentBaseUrl != null) {
      return currentBaseUrl
    }

    return startMutex.withLock {
      val existingProcess = process
      val existingBaseUrl = baseUrl
      if (existingProcess != null && existingProcess.isAlive && existingBaseUrl != null) {
        existingBaseUrl
      }
      else {
        if (existingProcess != null) {
          stopProcess()
        }
        startProcess()
      }
    }
  }

  private suspend fun startProcess(): String {
    val configuredExecutable = executablePathProvider()
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
    val executable = configuredExecutable ?: OpenCodeCliSupport.OPENCODE_COMMAND
    val requestedWorkingDirectory = workingDirectoryPath
    val effectiveWorkingDirectory = resolveOpenCodeServerWorkingDirectory(requestedWorkingDirectory)
    LOG.debug {
      "Starting OpenCode server(executable=$executable, executableSource=${if (configuredExecutable != null) "configured" else "default"}, requestedWorkingDirectory=${requestedWorkingDirectory ?: "<none>"}, effectiveWorkingDirectory=$effectiveWorkingDirectory, environmentOverrideCount=${environmentOverrides.size})"
    }

    val bindUrlDeferred = CompletableDeferred<String>()
    val createdProcess = try {
      GeneralCommandLine(executable, "serve", "--port", "0", "--hostname", "127.0.0.1")
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withEnvironment(environmentOverrides)
        .withWorkingDirectory(effectiveWorkingDirectory)
        .createProcess()
    }
    catch (t: Throwable) {
      if (t is CancellationException) throw t
      if (configuredExecutable == null && isExecutableNotFound(t)) {
        throw OpenCodeServerCliNotFoundException()
      }
      throw OpenCodeServerException("Failed to start OpenCode server from $executable", t)
    }

    process = createdProcess
    baseUrl = null
    startStdoutReader(createdProcess, bindUrlDeferred)
    startStderrReader(createdProcess)
    startWaiter(createdProcess)

    val boundUrl = try {
      withTimeout(SERVER_STARTUP_TIMEOUT_MS.milliseconds) { bindUrlDeferred.await() }
    }
    catch (t: TimeoutCancellationException) {
      stopProcess()
      throw OpenCodeServerException("Timed out waiting for OpenCode server to report its bind address", t)
    }
    catch (t: Throwable) {
      stopProcess()
      throw t
    }

    try {
      awaitReady(boundUrl)
      baseUrl = boundUrl
      return boundUrl
    }
    catch (t: Throwable) {
      stopProcess()
      if (t is OpenCodeServerException) throw t
      throw OpenCodeServerException("Failed to connect to OpenCode server", t)
    }
  }

  private fun startStdoutReader(process: Process, bindUrlDeferred: CompletableDeferred<String>) {
    stdoutJob?.cancel()
    stdoutJob = coroutineScope.launch(Dispatchers.IO) {
      val reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
      try {
        while (isActive) {
          val line = runInterruptible { reader.readLine() } ?: break
          if (line.isBlank()) {
            continue
          }
          LOG.debug { "OpenCode server stdout: $line" }
          if (!bindUrlDeferred.isCompleted) {
            extractHttpUrl(line)?.let(bindUrlDeferred::complete)
          }
        }
      }
      catch (e: Throwable) {
        if (!isActive || !process.isAlive) {
          return@launch
        }
        LOG.warn("OpenCode server stdout reader failed", e)
      }
    }
  }

  private fun startStderrReader(process: Process) {
    stderrJob?.cancel()
    stderrJob = coroutineScope.launch(Dispatchers.IO) {
      val reader = BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8))
      try {
        while (isActive) {
          val line = runInterruptible { reader.readLine() } ?: break
          if (line.isNotBlank()) {
            LOG.debug { "OpenCode server stderr: $line" }
          }
        }
      }
      catch (e: Throwable) {
        if (!isActive || !process.isAlive) {
          return@launch
        }
        LOG.warn("OpenCode server stderr reader failed", e)
      }
    }
  }

  private fun startWaiter(process: Process) {
    waitJob?.cancel()
    waitJob = coroutineScope.launch(Dispatchers.IO) {
      try {
        process.awaitExit()
      }
      catch (_: Throwable) {
        return@launch
      }
      handleProcessExit()
    }
  }

  private suspend fun awaitReady(boundUrl: String) {
    val readyPaths = listOf(
      "/global/health",
      "/doc",
    )
    withTimeout(SERVER_STARTUP_TIMEOUT_MS.milliseconds) {
      while (true) {
        for (path in readyPaths) {
          try {
            val request = HttpRequest.newBuilder(URI.create(boundUrl + path))
              .timeout(Duration.ofMillis(HTTP_REQUEST_TIMEOUT_MS))
              .GET()
              .build()
            val response = runInterruptible {
              httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            }
            if (response.statusCode() == 200) {
              return@withTimeout
            }
          }
          catch (_: Throwable) {
          }
        }
        delay(SERVER_READY_POLL_INTERVAL_MS.milliseconds)
      }
    }
  }

  private fun handleProcessExit() {
    process = null
    baseUrl = null
  }

  private fun stopProcess() {
    val currentProcess = process
    process = null
    baseUrl = null

    stdoutJob?.cancel()
    stderrJob?.cancel()
    waitJob?.cancel()
    stdoutJob = null
    stderrJob = null
    waitJob = null

    if (currentProcess != null) {
      stopOpenCodeServerProcess(currentProcess, PROCESS_TERMINATION_TIMEOUT_MS, coroutineScope)
    }
  }
}

internal data class OpenCodeServerSession(
  @JvmField val id: String,
  @JvmField val title: String?,
  @JvmField val directory: String?,
  @JvmField val updatedAt: Long,
  @JvmField val archivedAt: Long?,
)

internal data class OpenCodeServerProject(
  @JvmField val id: String,
  @JvmField val worktree: String?,
)

internal open class OpenCodeServerException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

internal class OpenCodeServerCliNotFoundException : OpenCodeServerException("OpenCode CLI not found")

internal fun createOpenCodeHttpClient(): HttpClient {
  return HttpClient.newBuilder()
    .connectTimeout(Duration.ofMillis(HTTP_REQUEST_TIMEOUT_MS))
    .version(HttpClient.Version.HTTP_1_1)
    .proxy(OpenCodeLoopbackNoProxySelector)
    .build()
}

private object OpenCodeLoopbackNoProxySelector : ProxySelector() {
  override fun select(uri: URI): List<Proxy> = listOf(Proxy.NO_PROXY)

  override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
  }
}

internal fun parseOpenCodeServerSessions(content: String): List<OpenCodeServerSession> {
  return JsonFactory().createJsonParser(content).use { parser ->
    when (parser.nextToken()) {
      JsonToken.START_ARRAY -> parseSessionArray(parser)
      JsonToken.START_OBJECT -> {
        val sessions = ArrayList<OpenCodeServerSession>()
        forEachJsonObjectField(parser) { fieldName ->
          when (fieldName) {
            "sessions", "data" -> {
              if (parser.currentToken() == JsonToken.START_ARRAY) {
                sessions.addAll(parseSessionArray(parser))
              }
              else {
                parser.skipChildren()
              }
            }
            else -> parser.skipChildren()
          }
          true
        }
        sessions
      }
      else -> emptyList()
    }
  }
}

internal fun parseOpenCodeServerProjects(content: String): List<OpenCodeServerProject> {
  return JsonFactory().createJsonParser(content).use { parser ->
    when (parser.nextToken()) {
      JsonToken.START_ARRAY -> parseProjectArray(parser)
      JsonToken.START_OBJECT -> {
        val projects = ArrayList<OpenCodeServerProject>()
        forEachJsonObjectField(parser) { fieldName ->
          when (fieldName) {
            "projects", "data" -> {
              if (parser.currentToken() == JsonToken.START_ARRAY) {
                projects.addAll(parseProjectArray(parser))
              }
              else {
                parser.skipChildren()
              }
            }
            else -> parser.skipChildren()
          }
          true
        }
        projects
      }
      else -> emptyList()
    }
  }
}

internal fun parseOpenCodeServerProjectDirectories(content: String): List<String> {
  return JsonFactory().createJsonParser(content).use { parser ->
    when (parser.nextToken()) {
      JsonToken.START_ARRAY -> parseDirectoryArray(parser)
      JsonToken.START_OBJECT -> {
        val directories = ArrayList<String>()
        forEachJsonObjectField(parser) { fieldName ->
          when (fieldName) {
            "directories", "data" -> {
              if (parser.currentToken() == JsonToken.START_ARRAY) {
                directories.addAll(parseDirectoryArray(parser))
              }
              else {
                parser.skipChildren()
              }
            }
            "directory", "path" -> readJsonStringOrNull(parser)?.trimToNull()?.let(directories::add)
            else -> parser.skipChildren()
          }
          true
        }
        directories
      }
      else -> emptyList()
    }
  }
}

private fun parseSessionArray(parser: JsonParser): List<OpenCodeServerSession> {
  val sessions = ArrayList<OpenCodeServerSession>()
  while (true) {
    val token = parser.nextToken() ?: return sessions
    if (token == JsonToken.END_ARRAY) return sessions
    if (token == JsonToken.START_OBJECT) {
      parseSessionObject(parser)?.let(sessions::add)
    }
    else {
      parser.skipChildren()
    }
  }
}

private fun parseSessionObject(parser: JsonParser): OpenCodeServerSession? {
  var id: String? = null
  var title: String? = null
  var directory: String? = null
  var createdAt: Long? = null
  var updatedAt: Long? = null
  var archivedAt: Long? = null

  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "id" -> id = readJsonStringOrNull(parser)
      "title" -> title = readJsonStringOrNull(parser)
      "directory" -> directory = readJsonStringOrNull(parser)
      "time" -> {
        val time = parseSessionTime(parser)
        createdAt = time.createdAt ?: createdAt
        updatedAt = time.updatedAt ?: updatedAt
        archivedAt = time.archivedAt ?: archivedAt
      }
      "time_created", "created", "createdAt" -> createdAt = readJsonLongOrNull(parser)
      "time_updated", "updated", "updatedAt" -> updatedAt = readJsonLongOrNull(parser)
      "time_archived", "archived", "archivedAt" -> archivedAt = readJsonLongOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }

  val resolvedId = id?.trimToNull() ?: return null
  return OpenCodeServerSession(
    id = resolvedId,
    title = title,
    directory = directory?.trimToNull(),
    updatedAt = updatedAt ?: createdAt ?: 0L,
    archivedAt = archivedAt?.takeIf { it > 0L },
  )
}

private fun parseSessionTime(parser: JsonParser): OpenCodeServerSessionTime {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return OpenCodeServerSessionTime()
  }

  var createdAt: Long? = null
  var updatedAt: Long? = null
  var archivedAt: Long? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "created" -> createdAt = readJsonLongOrNull(parser)
      "updated" -> updatedAt = readJsonLongOrNull(parser)
      "archived" -> archivedAt = readJsonLongOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return OpenCodeServerSessionTime(createdAt = createdAt, updatedAt = updatedAt, archivedAt = archivedAt)
}

private fun parseProjectArray(parser: JsonParser): List<OpenCodeServerProject> {
  val projects = ArrayList<OpenCodeServerProject>()
  while (true) {
    val token = parser.nextToken() ?: return projects
    if (token == JsonToken.END_ARRAY) return projects
    if (token == JsonToken.START_OBJECT) {
      parseProjectObject(parser)?.let(projects::add)
    }
    else {
      parser.skipChildren()
    }
  }
}

private fun parseProjectObject(parser: JsonParser): OpenCodeServerProject? {
  var id: String? = null
  var worktree: String? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "id" -> id = readJsonStringOrNull(parser)
      "worktree" -> worktree = readJsonStringOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }

  val resolvedId = id?.trimToNull() ?: return null
  return OpenCodeServerProject(id = resolvedId, worktree = worktree?.trimToNull())
}

private fun parseDirectoryArray(parser: JsonParser): List<String> {
  val directories = ArrayList<String>()
  while (true) {
    val token = parser.nextToken() ?: return directories
    when (token) {
      JsonToken.END_ARRAY -> return directories
      JsonToken.VALUE_STRING -> parser.string.trimToNull()?.let(directories::add)
      JsonToken.VALUE_NUMBER_INT, JsonToken.VALUE_NUMBER_FLOAT -> parser.numberValue.toString().trimToNull()?.let(directories::add)
      JsonToken.START_OBJECT -> parseDirectoryObject(parser)?.let(directories::add)
      else -> parser.skipChildren()
    }
  }
}

private fun parseDirectoryObject(parser: JsonParser): String? {
  var directory: String? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "directory", "path" -> directory = readJsonStringOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return directory?.trimToNull()
}

private data class OpenCodeServerSessionTime(
  @JvmField val createdAt: Long? = null,
  @JvmField val updatedAt: Long? = null,
  @JvmField val archivedAt: Long? = null,
)

@Suppress("HttpUrlsUsage")
private fun extractHttpUrl(line: String): String? {
  return stripAnsiEscapes(line)
    .splitToSequence(' ', '\t')
    .map(String::trim)
    .firstOrNull { it.startsWith("http://") }
    ?.trimEnd('/', '.', ';')
}

private fun stripAnsiEscapes(text: String): String {
  val stripped = StringBuilder(text.length)
  val chars = text.iterator()
  while (chars.hasNext()) {
    val ch = chars.nextChar()
    if (ch == '\u001B' && chars.hasNext() && chars.nextChar() == '[') {
      while (chars.hasNext()) {
        val next = chars.nextChar()
        if (next in '@'..'~') {
          break
        }
      }
      continue
    }
    stripped.append(ch)
  }
  return stripped.toString()
}

private fun isExecutableNotFound(error: Throwable): Boolean {
  return generateSequence(error) { it.cause }
    .any { cause ->
      when (cause) {
        is NoSuchFileException -> true
        is IOException -> {
          val message = cause.message ?: return@any false
          message.contains("error=2") ||
          message.contains("no such file or directory", ignoreCase = true) ||
          message.contains("cannot find the file", ignoreCase = true)
        }
        else -> false
      }
    }
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

private fun String.trimToNull(): String? = trim().takeIf { it.isNotEmpty() }
