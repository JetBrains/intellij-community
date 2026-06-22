// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.prompt.suggestions

import com.intellij.platform.ai.agent.json.createJsonGenerator
import com.intellij.platform.ai.agent.json.createJsonParser
import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.util.io.NioFiles
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assumptions.assumeTrue
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
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

internal fun createMockPromptSuggestionClient(
  scope: kotlinx.coroutines.CoroutineScope,
  tempDir: Path,
  environmentOverrides: Map<String, String> = emptyMap(),
): CodexPromptSuggestionAppServerClient {
  val codexPath = createPromptSuggestionCodexShim(tempDir)
  return CodexPromptSuggestionAppServerClient(
    coroutineScope = scope,
    executablePathProvider = { codexPath.toString() },
    environmentOverrides = environmentOverrides,
  )
}

internal class RealCodexPromptSuggestionHarness(
  @JvmField val client: CodexPromptSuggestionAppServerClient,
  @JvmField val projectDir: Path,
  @JvmField val responsesServer: MockResponsesServer,
) : AutoCloseable {
  override fun close() {
    client.shutdown()
    responsesServer.close()
  }
}

internal fun createRealPromptSuggestionHarness(
  scope: kotlinx.coroutines.CoroutineScope,
  tempDir: Path,
  responsePlans: List<MockResponsesPlan>,
): RealCodexPromptSuggestionHarness {
  val codexBinary = resolveCodexBinary()
  assumeTrue(codexBinary != null, "Codex CLI not found. Set CODEX_BIN or ensure codex is on PATH.")

  val projectDir = tempDir.resolve("prompt-suggest-project")
  Files.createDirectories(projectDir)
  val responsesServer = MockResponsesServer(
    responsePlans = responsePlans,
    requestValidator = ::validateStrictStructuredOutputRequest,
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
      client = CodexPromptSuggestionAppServerClient(
        coroutineScope = scope,
        executablePathProvider = { codexBinary!! },
        environmentOverrides = mapOf("CODEX_HOME" to codexHome.toString()),
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

private fun createPromptSuggestionCodexShim(tempDir: Path): Path {
  val javaHome = System.getProperty("java.home")
  val javaBin = Path.of(javaHome, "bin", if (isWindows()) "java.exe" else "java")
  val classpath = resolveTestClasspath()
  val argsFile = writeAppServerArgsFile(tempDir, classpath)
  return if (isWindows()) {
    val script = tempDir.resolve("codex.cmd")
    Files.writeString(
      script,
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
    Files.writeString(
      script,
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
  return entries.joinToString(File.pathSeparator) { entry ->
    val path = Path.of(entry)
    if (path.isAbsolute) entry else path.toAbsolutePath().normalize().toString()
  }
}

private fun writeAppServerArgsFile(tempDir: Path, classpath: String): Path {
  val argsFile = tempDir.resolve("codex-prompt-suggestion-app-server.args")
  val args = listOf("-cp", classpath, TEST_APP_SERVER_MAIN_CLASS)
  Files.newBufferedWriter(argsFile, StandardCharsets.UTF_8).use { writer ->
    for (arg in args) {
      writer.write(CommandLineWrapperUtil.quoteArg(arg))
      writer.newLine()
    }
  }
  return argsFile
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
    events.forEach { event ->
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
  JSON_FACTORY.createJsonGenerator(writer).use(write)
  return writer.toString()
}

private fun parseJsonValue(json: String): Any? {
  JSON_FACTORY.createJsonParser(json).use { parser ->
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

    JsonToken.VALUE_STRING -> parser.string
    JsonToken.VALUE_TRUE, JsonToken.VALUE_FALSE -> parser.booleanValue
    JsonToken.VALUE_NUMBER_INT, JsonToken.VALUE_NUMBER_FLOAT -> parser.numberValue
    JsonToken.VALUE_NULL -> null
    else -> parser.string
  }
}

@Suppress("UNCHECKED_CAST")
private fun Any?.asJsonObject(): Map<String, Any?>? = this as? Map<String, Any?>

private fun Any?.asStringList(): List<String>? {
  val values = this as? List<*> ?: return null
  val strings = values.filterIsInstance<String>()
  return strings.takeIf { it.size == values.size }
}

private fun resolveCodexBinary(): String? {
  val configured = System.getenv("CODEX_BIN")?.takeIf { it.isNotBlank() }
  return configured ?: PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS("codex")?.absolutePath
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
  Files.writeString(configPath, lines.joinToString("\n"), StandardCharsets.UTF_8)
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

private fun quoteForShell(value: String): String {
  return "'" + value.replace("'", "'\"'\"'") + "'"
}

private fun isWindows(): Boolean {
  return System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
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

private val JSON_FACTORY = JsonFactory()
private const val TEST_APP_SERVER_MAIN_CLASS = "com.intellij.agent.workbench.codex.prompt.suggestions.CodexPromptSuggestionTestAppServer"
