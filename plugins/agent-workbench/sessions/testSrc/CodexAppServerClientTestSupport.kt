// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.agent.workbench.codex.common.CodexAppServerClient
import com.intellij.agent.workbench.codex.common.CodexAppServerNotificationRouting
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadStatusKind
import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.io.IOException
import java.io.StringWriter
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
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

internal class RealCodexPromptSuggestionHarness(
  @JvmField val client: CodexAppServerClient,
  @JvmField val projectDir: Path,
  @JvmField val responsesServer: MockResponsesServer,
) : AutoCloseable {
  override fun close() {
    client.shutdown()
    responsesServer.close()
  }
}

internal fun createRealPromptSuggestionHarness(
  scope: CoroutineScope,
  tempDir: Path,
  responsePlans: List<MockResponsesPlan>,
  notificationRouting: CodexAppServerNotificationRouting = CodexAppServerNotificationRouting.PARSED_ONLY,
): RealCodexPromptSuggestionHarness {
  return createRealMockResponsesHarness(
    scope = scope,
    tempDir = tempDir,
    responsePlans = responsePlans,
    notificationRouting = notificationRouting,
    requestValidator = ::validateStrictStructuredOutputRequest,
  )
}

internal fun createRealMockResponsesHarness(
  scope: CoroutineScope,
  tempDir: Path,
  responsePlans: List<MockResponsesPlan>,
  notificationRouting: CodexAppServerNotificationRouting = CodexAppServerNotificationRouting.PARSED_ONLY,
  requestValidator: ((String) -> String?)? = null,
): RealCodexPromptSuggestionHarness {
  val codexBinary = resolveCodexBinary()
  assumeTrue(codexBinary != null, "Codex CLI not found. Set CODEX_BIN or ensure codex is on PATH.")

  val projectDir = tempDir.resolve("prompt-suggest-project")
  Files.createDirectories(projectDir)
  val responsesServer = MockResponsesServer(
    responsePlans = responsePlans,
    requestValidator = requestValidator,
  )
  val codexHome = tempDir.resolve("codex-home")
  Files.createDirectories(codexHome)
  writeMockResponsesCodexConfig(
    configPath = codexHome.resolve("config.toml"),
    projectDir = projectDir,
    responsesBaseUrl = responsesServer.baseUri + "/v1",
  )

  return try {
    RealCodexPromptSuggestionHarness(
      client = CodexAppServerClient(
        scope,
        executablePathProvider = { codexBinary!! },
        environmentOverrides = mapOf("CODEX_HOME" to codexHome.toString()),
        notificationRouting = notificationRouting,
      ),
      projectDir = projectDir,
      responsesServer = responsesServer,
    )
  }
  catch (t: Throwable) {
    responsesServer.close()
    throw t
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
      return MockResponsesPlan(
        body = sse(responseCreatedEvent(), assistantMessageEvent(message), responseCompletedEvent()),
        holdOpen = false,
      )
    }

    fun inProgressAssistantMessage(message: String): MockResponsesPlan {
      return MockResponsesPlan(
        body = sse(responseCreatedEvent(), assistantMessageEvent(message)),
        holdOpen = true,
      )
    }
  }
}

internal class MockResponsesServer(
  responsePlans: List<MockResponsesPlan>,
  private val requestValidator: ((String) -> String?)? = null,
) : AutoCloseable {
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
        sendTextResponse(exchange, 405, "Method Not Allowed")
        return@createContext
      }
      sendJsonResponse(exchange, 200, """{"object":"list","data":[{"id":"mock-model","object":"model"}]}""")
    }
    server.createContext("/v1/responses") { exchange ->
      val requestBody = String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
      requests.add(requestBody)
      if (exchange.requestMethod != "POST") {
        sendTextResponse(exchange, 405, "Method Not Allowed")
        return@createContext
      }

      val validationMessage = requestValidator?.invoke(requestBody)
      if (validationMessage != null) {
        sendJsonResponse(exchange, 400, invalidJsonSchemaError(validationMessage))
        return@createContext
      }

      val plan = synchronized(pendingPlans) {
        if (pendingPlans.isEmpty()) null else pendingPlans.removeFirst()
      }
      if (plan == null) {
        sendTextResponse(exchange, 500, "No mock response plan available")
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

  private fun sendJsonResponse(exchange: HttpExchange, code: Int, body: String) {
    exchange.responseHeaders.add("Content-Type", "application/json")
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(code, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
  }

  private fun sendTextResponse(exchange: HttpExchange, code: Int, body: String) {
    exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(code, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
  }
}

private fun validateStrictStructuredOutputRequest(requestBody: String): String? {
  val request = parseJsonValue(requestBody).asJsonObject()
    ?: return "Request body must be a JSON object."
  val schema = request["text"].asJsonObject()
    ?.get("format").asJsonObject()
    ?.get("schema").asJsonObject()
    ?: return "Missing text.format.schema."
  return validateStrictSchemaObject(schema, emptyList())
}

private fun validateStrictSchemaObject(schema: Map<String, Any?>, context: List<String>): String? {
  if (schemaDeclaresType(schema, "object")) {
    val required = schema["required"].asStringList()
    val properties = schema["properties"].asJsonObject().orEmpty()
    if (required == null) {
      val suffix = properties.keys.firstOrNull()?.let { " Missing '$it'." } ?: ""
      return "In context=${formatSchemaContext(context)}, 'required' is required to be supplied and to be an array including every key in properties.$suffix"
    }
    val missingRequiredProperty = properties.keys.firstOrNull { propertyName -> propertyName !in required }
    if (missingRequiredProperty != null) {
      return "In context=${formatSchemaContext(context)}, 'required' is required to be supplied and to be an array including every key in properties. Missing '$missingRequiredProperty'."
    }
    if (schema["additionalProperties"] != false) {
      return "In context=${formatSchemaContext(context)}, 'additionalProperties' is required to be supplied and to be false."
    }

    for ((propertyName, propertySchema) in properties) {
      validateStrictSchemaObject(propertySchema.asJsonObject() ?: continue, context + listOf("properties", propertyName))?.let { return it }
    }
  }

  if (schemaDeclaresType(schema, "array")) {
    validateStrictSchemaObject(schema["items"].asJsonObject() ?: return null, context + "items")?.let { return it }
  }
  return null
}

private fun invalidJsonSchemaError(message: String): String {
  return jsonString { generator ->
    generator.writeStartObject()
    generator.writeStringField("type", "error")
    generator.writeObjectFieldStart("error")
    generator.writeStringField("type", "invalid_request_error")
    generator.writeStringField("code", "invalid_json_schema")
    generator.writeStringField("message", "Invalid schema for response_format 'codex_output_schema': $message")
    generator.writeStringField("param", "text.format.schema")
    generator.writeEndObject()
    generator.writeNumberField("status", 400)
    generator.writeEndObject()
  }
}

private fun schemaDeclaresType(schema: Map<String, Any?>, expectedType: String): Boolean {
  return when (val typeValue = schema["type"]) {
    is String -> typeValue == expectedType
    is List<*> -> typeValue.any { it == expectedType }
    else -> false
  }
}

private fun formatSchemaContext(context: List<String>): String {
  return context.joinToString(prefix = "(", postfix = ")") { segment -> "'$segment'" }
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

private fun parseJsonValue(json: String): Any? {
  JSON_FACTORY.createParser(json).use { parser ->
    val token = parser.nextToken() ?: return null
    return readJsonValue(parser, token)
  }
}

private fun readJsonValue(parser: JsonParser, token: JsonToken): Any? {
  return when (token) {
    JsonToken.START_OBJECT -> {
      val result = LinkedHashMap<String, Any?>()
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        val fieldName = parser.currentName()
        val valueToken = parser.nextToken()
        result[fieldName] = readJsonValue(parser, valueToken)
      }
      result
    }

    JsonToken.START_ARRAY -> {
      val result = ArrayList<Any?>()
      while (true) {
        val itemToken = parser.nextToken() ?: break
        if (itemToken == JsonToken.END_ARRAY) {
          break
        }
        result.add(readJsonValue(parser, itemToken))
      }
      result
    }

    JsonToken.VALUE_STRING -> parser.valueAsString
    JsonToken.VALUE_TRUE, JsonToken.VALUE_FALSE -> parser.booleanValue
    JsonToken.VALUE_NUMBER_INT, JsonToken.VALUE_NUMBER_FLOAT -> parser.numberValue
    JsonToken.VALUE_NULL -> null
    else -> parser.valueAsString
  }
}

@Suppress("UNCHECKED_CAST")
private fun Any?.asJsonObject(): Map<String, Any?>? = this as? Map<String, Any?>

private fun Any?.asStringList(): List<String>? {
  val values = this as? List<*> ?: return null
  val strings = values.filterIsInstance<String>()
  return strings.takeIf { it.size == values.size }
}

private data class SseEvent(
  @JvmField val type: String,
  @JvmField val data: String,
)

private fun daemonThreadFactory(): ThreadFactory {
  return ThreadFactory { runnable ->
    Thread(runnable, "codex-responses-http").apply {
      isDaemon = true
    }
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
        if (thread.readTurns.isNotEmpty()) {
          generator.writeFieldName("readTurns")
          generator.writeStartArray()
          for (turn in thread.readTurns) {
            generator.writeStartObject()
            generator.writeStringField("statusType", turn.statusType)
            if (turn.statusAsObject) {
              generator.writeBooleanField("statusAsObject", true)
            }
            if (turn.itemTypes.isNotEmpty()) {
              generator.writeFieldName("itemTypes")
              generator.writeStartArray()
              turn.itemTypes.forEach(generator::writeString)
              generator.writeEndArray()
            }
            generator.writeEndObject()
          }
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
  @JvmField val id: String,
  @JvmField val title: String? = null,
  @JvmField val preview: String? = null,
  @JvmField val name: String? = null,
  @JvmField val summary: String? = null,
  @JvmField val cwd: String? = null,
  @JvmField val updatedAt: Long? = null,
  @JvmField val createdAt: Long? = null,
  @JvmField val updatedAtField: String = "updated_at",
  @JvmField val createdAtField: String = "created_at",
  @JvmField val sourceKind: String = "cli",
  @JvmField val sourceAsString: Boolean = false,
  @JvmField val sourceSubAgentFieldName: String = "subAgent",
  @JvmField val parentThreadId: String? = null,
  @JvmField val agentNickname: String? = null,
  @JvmField val agentRole: String? = null,
  @JvmField val statusType: String = "idle",
  @JvmField val statusActiveFlagsFieldName: String = "activeFlags",
  @JvmField val activeFlags: List<String> = emptyList(),
  @JvmField val readTurns: List<ThreadTurnSpec> = emptyList(),
  @JvmField val gitBranch: String? = null,
  @JvmField val archived: Boolean = false,
)

internal data class ThreadTurnSpec(
  @JvmField val statusType: String = "completed",
  @JvmField val statusAsObject: Boolean = false,
  @JvmField val itemTypes: List<String> = emptyList(),
)

internal fun createMockClient(
  scope: CoroutineScope,
  tempDir: Path,
  configPath: Path,
  environmentOverrides: Map<String, String> = emptyMap(),
  notificationRouting: CodexAppServerNotificationRouting = CodexAppServerNotificationRouting.BOTH,
): CodexAppServerClient {
  val codexPath = createCodexShim(tempDir, configPath)
  return CodexAppServerClient(
    scope,
    executablePathProvider = { codexPath.toString() },
    environmentOverrides = environmentOverrides,
    notificationRouting = notificationRouting,
  )
}

internal fun createMockCodexShim(tempDir: Path, configPath: Path): Path {
  return createCodexShim(tempDir, configPath)
}

@OptIn(LowLevelLocalMachineAccess::class)
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

  val statusThreads = active + archived + firstPage.threads
  if (backendName == "mock") {
    assertThat(statusThreads)
      .describedAs("$backendName status kinds")
      .allSatisfy { thread ->
        assertThat(thread.statusKind).isEqualTo(CodexThreadStatusKind.IDLE)
        assertThat(thread.activeFlags).isEmpty()
      }
  }
  else {
    assertThat(statusThreads)
      .describedAs("$backendName status fields")
      .allSatisfy { thread ->
        assertThat(thread.statusKind).isNotNull()
        assertThat(thread.activeFlags).doesNotContainNull()
      }
  }

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

private fun writeMockResponsesCodexConfig(configPath: Path, projectDir: Path, responsesBaseUrl: String) {
  val lines = mutableListOf<String>()
  lines.add("model = \"mock-model\"")
  lines.add("model_provider = \"mock_provider\"")
  lines.add("approval_policy = \"never\"")
  lines.add("sandbox_mode = \"read-only\"")
  lines.add("suppress_unstable_features_warning = true")
  lines.add("cli_auth_credentials_store = \"file\"")
  lines.add("")
  lines.add("[projects]")
  lines.add("${tomlString(projectDir.toString())} = { trust_level = \"trusted\" }")
  lines.add("")
  lines.add("[model_providers.mock_provider]")
  lines.add("name = \"Mock provider for test\"")
  lines.add("base_url = ${tomlString(responsesBaseUrl)}")
  lines.add("wire_api = \"responses\"")
  lines.add("request_max_retries = 0")
  lines.add("stream_max_retries = 0")
  configPath.writeText(lines.joinToString("\n"))
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

private val JSON_FACTORY = JsonFactory()
private const val DEFAULT_TEST_MODEL = "gpt-4o-mini"
private const val DEFAULT_TEST_REASONING_EFFORT = "low"
private const val TEST_APP_SERVER_MAIN_CLASS = "com.intellij.agent.workbench.sessions.CodexTestAppServer"
