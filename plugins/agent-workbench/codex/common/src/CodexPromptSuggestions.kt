// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.openapi.util.NlsSafe
import java.io.StringWriter

@Immutable
data class CodexPromptSuggestionRequest(
  @JvmField val cwd: String?,
  @JvmField val targetMode: String,
  @JvmField val model: String,
  @JvmField val reasoningEffort: String? = null,
  @JvmField val maxCandidates: Int = 3,
  @JvmField val contextItems: List<CodexPromptSuggestionContextItem>,
  @JvmField val seedCandidates: List<CodexPromptSuggestionCandidate> = emptyList(),
)

@Immutable
data class CodexPromptSuggestionContextItem(
  @JvmField val rendererId: String,
  @JvmField val title: String?,
  @JvmField val body: String,
  @JvmField val payload: CodexAppServerValue = CodexAppServerValue.Null,
  @JvmField val itemId: String? = null,
  @JvmField val parentItemId: String? = null,
  @JvmField val source: String = "unknown",
  @JvmField val truncation: CodexPromptSuggestionContextTruncation = CodexPromptSuggestionContextTruncation.none(body.length),
)

@Immutable
data class CodexPromptSuggestionContextTruncation(
  @JvmField val originalChars: Int,
  @JvmField val includedChars: Int,
  @JvmField val reason: String,
) {
  companion object {
    fun none(chars: Int): CodexPromptSuggestionContextTruncation {
      val safeChars = chars.coerceAtLeast(0)
      return CodexPromptSuggestionContextTruncation(
        originalChars = safeChars,
        includedChars = safeChars,
        reason = "none",
      )
    }
  }
}

@Immutable
data class CodexPromptSuggestionCandidate(
  @JvmField val id: String? = null,
  @JvmField val label: @NlsSafe String,
  @JvmField val promptText: @NlsSafe String,
)

sealed interface CodexPromptSuggestionResult {
  @Immutable
  data class PolishedSeeds(
    @JvmField val candidates: List<CodexPromptSuggestionCandidate>,
  ) : CodexPromptSuggestionResult

  @Immutable
  data class GeneratedCandidates(
    @JvmField val candidates: List<CodexPromptSuggestionCandidate>,
  ) : CodexPromptSuggestionResult
}

sealed interface CodexAppServerValue {
  data class Obj(
    @JvmField val fields: Map<String, CodexAppServerValue>,
  ) : CodexAppServerValue

  data class Arr(
    @JvmField val items: List<CodexAppServerValue>,
  ) : CodexAppServerValue

  data class Str(
    @JvmField val value: String,
  ) : CodexAppServerValue

  data class Num(
    @JvmField val value: String,
  ) : CodexAppServerValue

  data class Bool(
    @JvmField val value: Boolean,
  ) : CodexAppServerValue

  data object Null : CodexAppServerValue
}

private val PROMPT_SUGGESTION_JSON_FACTORY = JsonFactory()

internal fun buildPromptSuggestionTurnInput(request: CodexPromptSuggestionRequest): String {
  return buildString {
    appendLine("You generate short Agent Workbench prompt suggestions from visible IDE context.")
    appendLine("Return only JSON that matches the provided schema.")
    appendLine()
    appendLine("Rules:")
    appendLine("- Stay grounded in the provided context. Do not invent files, tests, symbols, failures, or goals.")
    appendLine("- Every candidate object must include id, label, and promptText.")
    appendLine("- Keep each label short and scannable.")
    appendLine("- Keep each promptText concise, concrete, and ready to send to Codex as a user prompt.")
    appendLine("- If fallback seed candidates are present and still fit the context, prefer polishing them.")
    appendLine("- Return kind=polishedSeeds only when you polish every provided seed, preserve the same order, and preserve each seed id exactly.")
    appendLine("- Otherwise return kind=generatedCandidates with at most ${request.maxCandidates.coerceAtLeast(1)} grounded candidates and set every generated candidate id to null.")
    appendLine("- When fallback seeds are weak, generic, or absent, generate better grounded candidates instead of forcing a polish result.")
    appendLine()
    appendLine("Target mode: ${request.targetMode}")
    appendLine()
    appendLine("Visible context items (${request.contextItems.size}):")
    if (request.contextItems.isEmpty()) {
      appendLine("none")
    }
    else {
      request.contextItems.forEachIndexed { index, item ->
        appendLine("[${index + 1}]")
        appendLine("rendererId: ${item.rendererId}")
        item.title?.let { appendLine("title: $it") }
        appendLine("source: ${item.source}")
        item.itemId?.let { appendLine("itemId: $it") }
        item.parentItemId?.let { appendLine("parentItemId: $it") }
        appendLine(
          "truncation: reason=${item.truncation.reason}, includedChars=${item.truncation.includedChars}, originalChars=${item.truncation.originalChars}"
        )
        appendLine("body:")
        appendIndentedBlock(item.body)
        appendLine("payloadJson: ${renderAppServerValue(item.payload)}")
        appendLine()
      }
    }

    appendLine("Fallback seed candidates (${request.seedCandidates.size}):")
    if (request.seedCandidates.isEmpty()) {
      appendLine("none")
    }
    else {
      request.seedCandidates.forEachIndexed { index, candidate ->
        appendLine("[${index + 1}]")
        candidate.id?.let { appendLine("id: $it") }
        appendLine("label: ${candidate.label}")
        appendLine("promptText: ${candidate.promptText}")
        appendLine()
      }
    }
  }.trim()
}

internal fun writePromptSuggestionOutputSchema(generator: JsonGenerator, request: CodexPromptSuggestionRequest) {
  val maxCandidates = maxOf(request.maxCandidates.coerceAtLeast(1), request.seedCandidates.size)

  generator.writeStartObject()
  generator.writeStringField("type", "object")
  generator.writeBooleanField("additionalProperties", false)
  generator.writeFieldName("required")
  generator.writeStartArray()
  generator.writeString("kind")
  generator.writeString("candidates")
  generator.writeEndArray()
  generator.writeFieldName("properties")
  generator.writeStartObject()

  generator.writeFieldName("kind")
  generator.writeStartObject()
  generator.writeStringField("type", "string")
  generator.writeFieldName("enum")
  generator.writeStartArray()
  generator.writeString("polishedSeeds")
  generator.writeString("generatedCandidates")
  generator.writeEndArray()
  generator.writeEndObject()

  generator.writeFieldName("candidates")
  generator.writeStartObject()
  generator.writeStringField("type", "array")
  generator.writeNumberField("minItems", 0)
  generator.writeNumberField("maxItems", maxCandidates)
  generator.writeFieldName("items")
  generator.writeStartObject()
  generator.writeStringField("type", "object")
  generator.writeBooleanField("additionalProperties", false)
  generator.writeFieldName("required")
  generator.writeStartArray()
  generator.writeString("id")
  generator.writeString("label")
  generator.writeString("promptText")
  generator.writeEndArray()
  generator.writeFieldName("properties")
  generator.writeStartObject()

  generator.writeFieldName("id")
  generator.writeStartObject()
  generator.writeFieldName("type")
  generator.writeStartArray()
  generator.writeString("string")
  generator.writeString("null")
  generator.writeEndArray()
  generator.writeEndObject()

  generator.writeFieldName("label")
  generator.writeStartObject()
  generator.writeStringField("type", "string")
  generator.writeEndObject()

  generator.writeFieldName("promptText")
  generator.writeStartObject()
  generator.writeStringField("type", "string")
  generator.writeEndObject()

  generator.writeEndObject()
  generator.writeEndObject()
  generator.writeEndObject()

  generator.writeEndObject()
  generator.writeEndObject()
}

internal fun parseCodexPromptSuggestionResult(payload: String): CodexPromptSuggestionResult? {
  PROMPT_SUGGESTION_JSON_FACTORY.createParser(payload).use { parser ->
    if (parser.nextToken() != JsonToken.START_OBJECT) {
      return null
    }

    var kind: String? = null
    var candidates: List<CodexPromptSuggestionCandidate> = emptyList()
    forEachObjectField(parser) { fieldName ->
      when (fieldName) {
        "kind" -> kind = readStringOrNull(parser)
        "candidates" -> candidates = parsePromptSuggestionCandidates(parser)
        else -> parser.skipChildren()
      }
      true
    }
    return when (kind?.trim()) {
      "polishedSeeds" -> CodexPromptSuggestionResult.PolishedSeeds(candidates)
      "generatedCandidates" -> CodexPromptSuggestionResult.GeneratedCandidates(candidates)
      else -> null
    }
  }
}

private fun parsePromptSuggestionCandidates(parser: JsonParser): List<CodexPromptSuggestionCandidate> {
  if (parser.currentToken != JsonToken.START_ARRAY) {
    parser.skipChildren()
    return emptyList()
  }

  val candidates = ArrayList<CodexPromptSuggestionCandidate>()
  while (true) {
    val token = parser.nextToken() ?: break
    if (token == JsonToken.END_ARRAY) {
      break
    }
    if (token != JsonToken.START_OBJECT) {
      parser.skipChildren()
      continue
    }
    parsePromptSuggestionCandidate(parser)?.let(candidates::add)
  }
  return candidates
}

private fun parsePromptSuggestionCandidate(parser: JsonParser): CodexPromptSuggestionCandidate? {
  var id: String? = null
  var label: String? = null
  var promptText: String? = null
  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "id" -> id = readStringOrNull(parser)
      "label" -> label = readStringOrNull(parser)
      "promptText" -> promptText = readStringOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }
  val resolvedLabel = label?.takeIf { it.isNotBlank() } ?: return null
  val resolvedPromptText = promptText?.takeIf { it.isNotBlank() } ?: return null
  return CodexPromptSuggestionCandidate(
    id = id?.trim()?.takeIf { it.isNotEmpty() },
    label = resolvedLabel,
    promptText = resolvedPromptText,
  )
}

private fun renderAppServerValue(value: CodexAppServerValue): String {
  val writer = StringWriter()
  val generator = PROMPT_SUGGESTION_JSON_FACTORY.createGenerator(writer)
  generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
  writeAppServerValue(generator, value)
  generator.close()
  return writer.toString()
}

private fun StringBuilder.appendIndentedBlock(text: String) {
  text.ifEmpty { "" }
    .lineSequence()
    .forEach { line ->
      append("  ")
      appendLine(line)
    }
}

private fun writeAppServerValue(generator: JsonGenerator, value: CodexAppServerValue) {
  when (value) {
    is CodexAppServerValue.Arr -> {
      generator.writeStartArray()
      value.items.forEach { item -> writeAppServerValue(generator, item) }
      generator.writeEndArray()
    }
    is CodexAppServerValue.Bool -> generator.writeBoolean(value.value)
    is CodexAppServerValue.Null -> generator.writeNull()
    is CodexAppServerValue.Num -> writeNumericValue(generator, value.value)
    is CodexAppServerValue.Obj -> {
      generator.writeStartObject()
      value.fields.forEach { (name, fieldValue) ->
        generator.writeFieldName(name)
        writeAppServerValue(generator, fieldValue)
      }
      generator.writeEndObject()
    }
    is CodexAppServerValue.Str -> generator.writeString(value.value)
  }
}

private fun writeNumericValue(generator: JsonGenerator, value: String) {
  value.toLongOrNull()?.let { numericValue ->
    generator.writeNumber(numericValue)
    return
  }
  value.toDoubleOrNull()?.let { numericValue ->
    generator.writeNumber(numericValue)
    return
  }
  generator.writeString(value)
}
