// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonToken
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.util.SystemInfo
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import java.io.IOException
import java.io.StringWriter
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class CodexRealTuiHarness(
  private val codexBinary: String,
  tempRoot: Path,
  responsePlans: List<MockResponsesPlan>,
  enableDefaultModeRequestUserInput: Boolean = false,
) : AutoCloseable {
  val projectDir: Path = testCreateProjectDir(tempRoot, "project")
  val codexHome: Path = tempRoot.resolve("codex-home")

  private val server = MockResponsesServer(responsePlans)

  init {
    Files.createDirectories(codexHome)
    Files.writeString(codexHome.resolve("config.toml"), buildConfigToml(enableDefaultModeRequestUserInput))
  }

  fun start(prompt: String): RunningCodexTuiSession {
    val environment = HashMap(System.getenv())
    environment["CODEX_HOME"] = codexHome.toString()
    environment.putIfAbsent("TERM", "xterm-256color")

    val process = PtyProcessBuilder(
      arrayOf(
        codexBinary,
        "--no-alt-screen",
        "-C",
        projectDir.toString(),
        "-c",
        "analytics.enabled=false",
        prompt,
      )
    )
      .setConsole(true)
      .setDirectory(projectDir.toString())
      .setEnvironment(environment)
      .setInitialColumns(120)
      .setInitialRows(40)
      .setRedirectErrorStream(true)
      .start()

    return RunningCodexTuiSession(
      process = process,
      codexHome = codexHome,
      projectDir = projectDir,
      responsesServer = server,
    )
  }

  override fun close() {
    server.close()
  }

  private fun buildConfigToml(enableDefaultModeRequestUserInput: Boolean): String {
    return buildString {
      appendLine("model = \"mock-model\"")
      appendLine("model_provider = \"mock_provider\"")
      appendLine("approval_policy = \"never\"")
      appendLine("sandbox_mode = \"read-only\"")
      appendLine("suppress_unstable_features_warning = true")
      appendLine()
      if (enableDefaultModeRequestUserInput) {
        appendLine("[features]")
        appendLine("default_mode_request_user_input = true")
        appendLine()
      }
      appendLine("[projects]")
      appendLine("${tomlString(projectDir.toString())} = { trust_level = \"trusted\" }")
      appendLine()
      appendLine("[model_providers.mock_provider]")
      appendLine("name = \"Mock provider for test\"")
      appendLine("base_url = ${tomlString(server.baseUri + "/v1")}")
      appendLine("wire_api = \"responses\"")
      appendLine("request_max_retries = 0")
      appendLine("stream_max_retries = 0")
    }
  }

  companion object {
    fun resolveCodexBinary(): String? {
      val configured = System.getenv("CODEX_BIN")?.takeIf { it.isNotBlank() }
      return configured ?: PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS("codex")?.absolutePath
    }

    fun isSupportedPlatform(): Boolean = !SystemInfo.isWindows
  }
}

internal class RunningCodexTuiSession(
  private val process: PtyProcess,
  private val codexHome: Path,
  private val projectDir: Path,
  private val responsesServer: MockResponsesServer,
) : AutoCloseable {
  private val outputLock = ReentrantLock()
  private val output = StringBuilder()
  private val closed = AtomicBoolean(false)
  private val readerThread = thread(start = false, isDaemon = true, name = "codex-real-tui-reader") {
    readOutputLoop()
  }

  init {
    readerThread.start()
  }

  suspend fun awaitThreadId(timeout: Duration = 20.seconds): String {
    return eventually(timeout = timeout) {
      currentRolloutSnapshot()?.threadId
    } ?: error("Timed out waiting for real Codex rollout session id.\n${diagnostics()}")
  }

  fun diagnostics(): String {
    val rollout = currentRolloutSnapshot()
    val rolloutDetails = if (rollout == null) {
      "<no rollout file yet>"
    }
    else {
      buildString {
        appendLine("rollout=${rollout.path}")
        appendLine(rollout.path.readText())
      }
    }
    return buildString {
      appendLine("output:")
      appendLine(outputTail())
      appendLine("requests:")
      responsesServer.requests().forEach(::appendLine)
      appendLine("rollout:")
      append(rolloutDetails)
    }
  }

  fun outputTail(maxChars: Int = 4000): String {
    return outputLock.withLock {
      val text = output.toString()
      if (text.length <= maxChars) text else text.takeLast(maxChars)
    }
  }

  fun isAlive(): Boolean = process.isAlive

  override fun close() {
    if (!closed.compareAndSet(false, true)) {
      return
    }

    repeat(4) {
      if (!process.isAlive) {
        return@repeat
      }
      runCatching {
        process.outputStream.write(CTRL_C)
        process.outputStream.flush()
      }
      Thread.sleep(300)
    }

    waitForExit(5.seconds)
    if (process.isAlive) {
      process.destroy()
      waitForExit(3.seconds)
    }
    if (process.isAlive) {
      process.destroyForcibly()
      waitForExit(2.seconds)
    }
    readerThread.join(1_000)
  }

  private fun waitForExit(timeout: Duration) {
    val deadline = System.nanoTime() + timeout.inWholeNanoseconds
    while (process.isAlive && System.nanoTime() < deadline) {
      Thread.sleep(100)
    }
  }

  private fun currentRolloutSnapshot(): RolloutSnapshot? {
    val sessionsDir = codexHome.resolve("sessions")
    if (!Files.isDirectory(sessionsDir)) {
      return null
    }

    var latestSnapshot: RolloutSnapshot? = null
    Files.walk(sessionsDir).use { paths ->
      paths
        .filter { path -> Files.isRegularFile(path) && path.fileName.toString().startsWith("rollout-") && path.fileName.toString().endsWith(".jsonl") }
        .forEach { path ->
          val snapshot = readRolloutSnapshot(path) ?: return@forEach
          if (snapshot.cwd != projectDir.normalize()) {
            return@forEach
          }

          val latestMtime = latestSnapshot?.let { Files.getLastModifiedTime(it.path).toMillis() } ?: Long.MIN_VALUE
          val currentMtime = runCatching { Files.getLastModifiedTime(snapshot.path).toMillis() }.getOrDefault(Long.MIN_VALUE)
          if (latestSnapshot == null || currentMtime >= latestMtime) {
            latestSnapshot = snapshot
          }
        }
    }
    return latestSnapshot
  }

  private fun readRolloutSnapshot(path: Path): RolloutSnapshot? {
    val lines = runCatching { path.readLines() }.getOrNull() ?: return null
    for (line in lines) {
      val snapshot = runCatching { parseSessionMetaLine(path, line) }.getOrNull() ?: continue
      return snapshot
    }
    return null
  }

  private fun readOutputLoop() {
    val stream = process.inputStream
    val buffer = ByteArray(4096)
    var tail = ByteArray(0)
    try {
      while (true) {
        val read = stream.read(buffer)
        if (read < 0) {
          break
        }
        val chunk = buffer.copyOf(read)
        outputLock.withLock {
          output.append(String(chunk, StandardCharsets.UTF_8))
        }
        if (containsCursorQuery(tail, chunk, read)) {
          runCatching {
            process.outputStream.write(CURSOR_POSITION_RESPONSE)
            process.outputStream.flush()
          }
        }
        tail = combinedTail(tail, chunk, read)
      }
    }
    catch (_: IOException) {
    }
  }

  private fun combinedTail(previousTail: ByteArray, chunk: ByteArray, read: Int): ByteArray {
    val combined = ByteArray(previousTail.size + read)
    System.arraycopy(previousTail, 0, combined, 0, previousTail.size)
    System.arraycopy(chunk, 0, combined, previousTail.size, read)
    val tailSize = (CURSOR_QUERY.size - 1).coerceAtMost(combined.size)
    return combined.copyOfRange(combined.size - tailSize, combined.size)
  }

  private fun containsCursorQuery(previousTail: ByteArray, chunk: ByteArray, read: Int): Boolean {
    val combined = ByteArray(previousTail.size + read)
    System.arraycopy(previousTail, 0, combined, 0, previousTail.size)
    System.arraycopy(chunk, 0, combined, previousTail.size, read)
    if (combined.size < CURSOR_QUERY.size) {
      return false
    }
    val lastIndex = combined.size - CURSOR_QUERY.size
    for (index in 0..lastIndex) {
      if (combined[index] == CURSOR_QUERY[0] &&
          combined[index + 1] == CURSOR_QUERY[1] &&
          combined[index + 2] == CURSOR_QUERY[2] &&
          combined[index + 3] == CURSOR_QUERY[3]) {
        return true
      }
    }
    return false
  }
}

internal class MockResponsesPlan private constructor(
  private val body: String,
  private val holdOpen: Boolean,
) {
  private val releaseLatch = CountDownLatch(1)

  fun write(exchange: HttpExchange) {
    exchange.responseHeaders.add("Content-Type", "text/event-stream")
    exchange.responseHeaders.add("Cache-Control", "no-cache")
    exchange.sendResponseHeaders(200, 0)
    exchange.responseBody.use { responseBody ->
      responseBody.write(body.toByteArray(StandardCharsets.UTF_8))
      responseBody.flush()
      if (holdOpen) {
        releaseLatch.await(30, TimeUnit.SECONDS)
      }
    }
  }

  fun release() {
    releaseLatch.countDown()
  }

  companion object {
    fun completedAssistantMessage(message: String): MockResponsesPlan {
      return MockResponsesPlan(body = sse(responseCreatedEvent(), assistantMessageEvent(message), responseCompletedEvent()), holdOpen = false)
    }

    fun inProgressAssistantMessage(message: String): MockResponsesPlan {
      return MockResponsesPlan(body = sse(responseCreatedEvent(), assistantMessageEvent(message)), holdOpen = true)
    }

    fun requestUserInput(callId: String = "call-1"): MockResponsesPlan {
      return MockResponsesPlan(
        body = sse(
          responseCreatedEvent(),
          requestUserInputEvent(callId = callId, arguments = requestUserInputArguments()),
          responseCompletedEvent(),
        ),
        holdOpen = false,
      )
    }
  }
}

internal class MockResponsesServer(responsePlans: List<MockResponsesPlan>) : AutoCloseable {
  private val allPlans = responsePlans.toList()
  private val pendingPlans = ArrayDeque(responsePlans)
  private val requests = CopyOnWriteArrayList<String>()
  private val executor = Executors.newCachedThreadPool(daemonThreadFactory())
  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

  val baseUri: String
    get() = "http://127.0.0.1:${server.address.port}"

  init {
    server.executor = executor
    server.createContext("/v1/models") { exchange ->
      if (exchange.requestMethod != "GET") {
        sendPlainResponse(exchange, 405, "Method Not Allowed")
        return@createContext
      }
      sendModelsResponse(exchange)
    }
    server.createContext("/v1/responses") { exchange ->
      requests.add(String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8))
      if (exchange.requestMethod != "POST") {
        sendPlainResponse(exchange, 405, "Method Not Allowed")
        return@createContext
      }

      val plan = synchronized(pendingPlans) {
        if (pendingPlans.isEmpty()) null else pendingPlans.removeFirst()
      }
      if (plan == null) {
        sendPlainResponse(exchange, 500, "No mock response plan available")
        return@createContext
      }

      try {
        plan.write(exchange)
      }
      catch (_: IOException) {
      }
    }
    server.start()
  }

  fun requests(): List<String> = requests.toList()

  override fun close() {
    allPlans.forEach(MockResponsesPlan::release)
    synchronized(pendingPlans) { pendingPlans.clear() }
    server.stop(0)
    executor.shutdownNow()
  }

  private fun sendModelsResponse(exchange: HttpExchange) {
    exchange.responseHeaders.add("Content-Type", "application/json")
    val bytes = """{"object":"list","data":[{"id":"mock-model","object":"model"}]}""".toByteArray(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(200, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
  }

  private fun sendPlainResponse(exchange: HttpExchange, code: Int, body: String) {
    exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(code, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
  }
}

internal suspend fun <T> eventually(
  timeout: Duration,
  interval: Duration = 100.milliseconds,
  probe: suspend () -> T?,
): T? {
  val deadline = System.nanoTime() + timeout.inWholeNanoseconds
  while (System.nanoTime() < deadline) {
    probe()?.let { return it }
    delay(interval)
  }
  return probe()
}

private data class RolloutSnapshot(
  @JvmField val path: Path,
  @JvmField val threadId: String,
  @JvmField val cwd: Path,
)

private data class SseEvent(
  @JvmField val type: String,
  @JvmField val data: String,
)

private fun parseSessionMetaLine(path: Path, line: String): RolloutSnapshot? {
  JSON_FACTORY.createParser(line).use { parser ->
    if (parser.nextToken() != JsonToken.START_OBJECT) {
      return null
    }

    var recordType: String? = null
    var threadId: String? = null
    var cwd: String? = null
    while (parser.nextToken() != JsonToken.END_OBJECT) {
      val fieldName = parser.currentName()
      val valueToken = parser.nextToken()
      when (fieldName) {
        "type" -> recordType = parser.valueAsString
        "payload" -> {
          if (valueToken != JsonToken.START_OBJECT) {
            parser.skipChildren()
            continue
          }

          while (parser.nextToken() != JsonToken.END_OBJECT) {
            val payloadField = parser.currentName()
            parser.nextToken()
            when (payloadField) {
              "id" -> threadId = parser.valueAsString
              "cwd" -> cwd = parser.valueAsString
              else -> parser.skipChildren()
            }
          }
        }
        else -> parser.skipChildren()
      }
    }

    if (recordType != "session_meta" || threadId.isNullOrBlank() || cwd.isNullOrBlank()) {
      return null
    }
    return RolloutSnapshot(path = path, threadId = threadId, cwd = Path.of(cwd).normalize())
  }
}

private fun responseCreatedEvent(): SseEvent {
  return jsonEvent(type = "response.created") { generator ->
    generator.writeObjectFieldStart("response")
    generator.writeStringField("id", "resp-1")
    generator.writeEndObject()
  }
}

private fun responseCompletedEvent(): SseEvent {
  return jsonEvent(type = "response.completed") { generator ->
    generator.writeObjectFieldStart("response")
    generator.writeStringField("id", "resp-1")
    generator.writeObjectFieldStart("usage")
    generator.writeNumberField("input_tokens", 0)
    generator.writeNullField("input_tokens_details")
    generator.writeNumberField("output_tokens", 0)
    generator.writeNullField("output_tokens_details")
    generator.writeNumberField("total_tokens", 0)
    generator.writeEndObject()
    generator.writeEndObject()
  }
}

private fun assistantMessageEvent(text: String): SseEvent {
  return jsonEvent(type = "response.output_item.done") { generator ->
    generator.writeObjectFieldStart("item")
    generator.writeStringField("type", "message")
    generator.writeStringField("role", "assistant")
    generator.writeStringField("id", "msg-1")
    generator.writeArrayFieldStart("content")
    generator.writeStartObject()
    generator.writeStringField("type", "output_text")
    generator.writeStringField("text", text)
    generator.writeEndObject()
    generator.writeEndArray()
    generator.writeEndObject()
  }
}

private fun requestUserInputEvent(callId: String, arguments: String): SseEvent {
  return jsonEvent(type = "response.output_item.done") { generator ->
    generator.writeObjectFieldStart("item")
    generator.writeStringField("type", "function_call")
    generator.writeStringField("call_id", callId)
    generator.writeStringField("name", "request_user_input")
    generator.writeStringField("arguments", arguments)
    generator.writeEndObject()
  }
}

private fun requestUserInputArguments(): String {
  return jsonString { generator ->
    generator.writeStartObject()
    generator.writeArrayFieldStart("questions")
    generator.writeStartObject()
    generator.writeStringField("id", "confirm_path")
    generator.writeStringField("header", "Confirm")
    generator.writeStringField("question", "Proceed with the plan?")
    generator.writeArrayFieldStart("options")
    generator.writeStartObject()
    generator.writeStringField("label", "Yes (Recommended)")
    generator.writeStringField("description", "Continue the current plan.")
    generator.writeEndObject()
    generator.writeStartObject()
    generator.writeStringField("label", "No")
    generator.writeStringField("description", "Stop and revisit the approach.")
    generator.writeEndObject()
    generator.writeEndArray()
    generator.writeEndObject()
    generator.writeEndArray()
    generator.writeEndObject()
  }
}

private fun sse(vararg events: SseEvent): String {
  return buildString {
    for (event in events) {
      append("event: ")
      append(event.type)
      append('\n')
      append("data: ")
      append(event.data)
      append("\n\n")
    }
  }
}

private fun jsonEvent(type: String, body: (JsonGenerator) -> Unit): SseEvent {
  return SseEvent(type = type, data = jsonString { generator ->
    generator.writeStartObject()
    generator.writeStringField("type", type)
    body(generator)
    generator.writeEndObject()
  })
}

private fun jsonString(write: (JsonGenerator) -> Unit): String {
  val writer = StringWriter()
  JSON_FACTORY.createGenerator(writer).use(write)
  return writer.toString()
}

private fun tomlString(value: String): String {
  return buildString {
    append('"')
    value.forEach { char ->
      when (char) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        else -> append(char)
      }
    }
    append('"')
  }
}

private fun daemonThreadFactory(): ThreadFactory {
  return ThreadFactory { runnable ->
    Thread(runnable, "codex-real-tui-http").apply {
      isDaemon = true
    }
  }
}

private val JSON_FACTORY = JsonFactory()
private val CURSOR_QUERY = byteArrayOf(0x1B, '['.code.toByte(), '6'.code.toByte(), 'n'.code.toByte())
private val CURSOR_POSITION_RESPONSE = "\u001B[1;1R".toByteArray(StandardCharsets.UTF_8)
private val CTRL_C = byteArrayOf(3)
