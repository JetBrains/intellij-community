// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.prompt.suggestions

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

import com.intellij.agent.workbench.codex.common.currentToken
import com.intellij.agent.workbench.codex.common.forEachObjectField
import com.intellij.agent.workbench.codex.common.readStringOrNull
import com.intellij.agent.workbench.codex.common.writeBooleanField
import com.intellij.agent.workbench.codex.common.writeFieldName
import com.intellij.agent.workbench.codex.common.writeNumberField
import com.intellij.agent.workbench.codex.common.writeObject
import com.intellij.agent.workbench.codex.common.writeObjectField
import com.intellij.agent.workbench.codex.common.writeStringArrayField
import com.intellij.agent.workbench.codex.common.writeStringField
import com.intellij.agent.workbench.json.createJsonGenerator
import com.intellij.agent.workbench.json.createJsonParser
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionAiCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionAiResult
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import java.io.StringWriter

internal data class CodexPromptSuggestionRequest(
  @JvmField val cwd: String?,
  @JvmField val targetMode: String,
  @JvmField val model: String,
  @JvmField val reasoningEffort: String? = null,
  @JvmField val maxCandidates: Int = 3,
  @JvmField val contextItems: List<CodexPromptSuggestionContextItem>,
  @JvmField val seedCandidates: List<AgentPromptSuggestionAiCandidate> = emptyList(),
)

internal data class CodexPromptSuggestionContextItem(
  @JvmField val rendererId: String,
  @JvmField val title: String?,
  @JvmField val body: String,
  @JvmField val payload: AgentPromptPayloadValue = AgentPromptPayloadValue.Obj.EMPTY,
  @JvmField val itemId: String? = null,
  @JvmField val parentItemId: String? = null,
  @JvmField val source: String = "unknown",
  @JvmField val truncation: CodexPromptSuggestionContextTruncation = CodexPromptSuggestionContextTruncation.none(body.length),
)

internal data class CodexPromptSuggestionContextTruncation(
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
        appendLine("payloadJson: ${renderPromptPayloadValue(item.payload)}")
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

  generator.writeObject {
    writeStringField("type", "object")
    writeBooleanField("additionalProperties", false)
    writeStringArrayField("required", "kind", "candidates")
    writeObjectField("properties") {
      writeObjectField("kind") {
        writeStringField("type", "string")
        writeStringArrayField("enum", "polishedSeeds", "generatedCandidates")
      }

      writeObjectField("candidates") {
        writeStringField("type", "array")
        writeNumberField("minItems", 0)
        writeNumberField("maxItems", maxCandidates)
        writeObjectField("items") {
          writeStringField("type", "object")
          writeBooleanField("additionalProperties", false)
          writeStringArrayField("required", "id", "label", "promptText")
          writeObjectField("properties") {
            writeObjectField("id") {
              writeStringArrayField("type", "string", "null")
            }
            writeObjectField("label") {
              writeStringField("type", "string")
            }
            writeObjectField("promptText") {
              writeStringField("type", "string")
            }
          }
        }
      }
    }
  }
}

internal fun parseCodexPromptSuggestionResult(payload: String): AgentPromptSuggestionAiResult? {
  PROMPT_SUGGESTION_JSON_FACTORY.createJsonParser(payload).use { parser ->
    if (parser.nextToken() != JsonToken.START_OBJECT) {
      return null
    }

    var kind: String? = null
    var candidates: List<AgentPromptSuggestionAiCandidate> = emptyList()
    forEachObjectField(parser) { fieldName ->
      when (fieldName) {
        "kind" -> kind = readStringOrNull(parser)
        "candidates" -> candidates = parsePromptSuggestionCandidates(parser)
        else -> parser.skipChildren()
      }
      true
    }
    return when (kind?.trim()) {
      "polishedSeeds" -> AgentPromptSuggestionAiResult.PolishedSeeds(candidates)
      "generatedCandidates" -> AgentPromptSuggestionAiResult.GeneratedCandidates(candidates)
      else -> null
    }
  }
}

private fun parsePromptSuggestionCandidates(parser: JsonParser): List<AgentPromptSuggestionAiCandidate> {
  if (parser.currentToken != JsonToken.START_ARRAY) {
    parser.skipChildren()
    return emptyList()
  }

  val candidates = ArrayList<AgentPromptSuggestionAiCandidate>()
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

private fun parsePromptSuggestionCandidate(parser: JsonParser): AgentPromptSuggestionAiCandidate? {
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
  return AgentPromptSuggestionAiCandidate(
    id = id?.trim()?.takeIf { it.isNotEmpty() },
    label = resolvedLabel,
    promptText = resolvedPromptText,
  )
}

private fun renderPromptPayloadValue(value: AgentPromptPayloadValue): String {
  val writer = StringWriter()
  val generator = PROMPT_SUGGESTION_JSON_FACTORY.createJsonGenerator(writer)
  writePromptPayloadValue(generator, value)
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

private fun writePromptPayloadValue(generator: JsonGenerator, value: AgentPromptPayloadValue) {
  when (value) {
    is AgentPromptPayloadValue.Arr -> {
      generator.writeStartArray()
      value.items.forEach { item -> writePromptPayloadValue(generator, item) }
      generator.writeEndArray()
    }
    is AgentPromptPayloadValue.Bool -> generator.writeBoolean(value.value)
    AgentPromptPayloadValue.Null -> generator.writeNull()
    is AgentPromptPayloadValue.Num -> writeNumericValue(generator, value.value)
    is AgentPromptPayloadValue.Obj -> {
      generator.writeStartObject()
      value.fields.forEach { (name, fieldValue) ->
        generator.writeFieldName(name)
        writePromptPayloadValue(generator, fieldValue)
      }
      generator.writeEndObject()
    }
    is AgentPromptPayloadValue.Str -> generator.writeString(value.value)
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
