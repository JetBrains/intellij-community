// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.testrunner.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorPhase
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.agent.workbench.prompt.core.dataContextOrNull
import com.intellij.agent.workbench.prompt.testrunner.AgentPromptTestRunnerBundle
import com.intellij.agent.workbench.prompt.testrunner.computeTestStatusCounts
import com.intellij.agent.workbench.prompt.testrunner.formatTestReference
import com.intellij.agent.workbench.prompt.testrunner.normalizeTestStatus
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor

private const val MAX_INCLUDED_SELECTION_TESTS = 5
private const val MAX_ASSERTION_HINT_CHARS = 180
private const val MAX_CONSOLE_OUTPUT_CHARS = 4_000
private val WHITESPACE_REGEX = Regex("\\s+")

private data class SelectedTestContext(
  @JvmField val name: String,
  @JvmField val locationUrl: String?,
  @JvmField val reference: String,
  @JvmField val status: String,
  @JvmField val assertionMessage: String?,
  @JvmField val isDefect: Boolean,
)

private data class ConsoleOutputExcerpt(
  @JvmField val text: String,
  @JvmField val fromSelection: Boolean,
  @JvmField val originalChars: Int,
  @JvmField val includedChars: Int,
)

internal class AgentPromptTestSelectionContextContributor : AgentPromptContextContributorBridge {
  override val phase: AgentPromptContextContributorPhase
    get() = AgentPromptContextContributorPhase.INVOCATION

  override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
    val dataContext = invocationData.dataContextOrNull() ?: return emptyList()
    val selectedTests = extractSelectedTests(dataContext)
      .map(::toSelectedContext)
    val normalizedSelection = normalizeSelection(selectedTests)
    if (normalizedSelection.isEmpty()) {
      return emptyList()
    }
    if (!isTestOwnedInvocation(dataContext)) {
      return emptyList()
    }
    val consoleOutput = extractConsoleOutput(dataContext)

    val included = normalizedSelection.take(MAX_INCLUDED_SELECTION_TESTS)
    if (included.isEmpty()) {
      return emptyList()
    }

    val fullContent = normalizedSelection.joinToString(separator = "\n", transform = ::renderLine)
    val content = included.joinToString(separator = "\n", transform = ::renderLine)
    if (content.isBlank()) {
      return emptyList()
    }

    val statusCounts = computeTestStatusCounts(normalizedSelection.map { entry -> entry.status })
    val payloadEntries = included.map { entry ->
      val fields = linkedMapOf<String, AgentPromptPayloadValue>(
        "name" to AgentPromptPayload.str(entry.name),
        "status" to AgentPromptPayload.str(entry.status),
        "reference" to AgentPromptPayload.str(entry.reference),
      )
      entry.locationUrl?.let { locationUrl ->
        fields["locationUrl"] = AgentPromptPayload.str(locationUrl)
      }
      entry.assertionMessage?.let { assertionMessage ->
        fields["assertionMessage"] = AgentPromptPayload.str(assertionMessage)
      }
      AgentPromptPayloadValue.Obj(fields)
    }
    val payloadFields = linkedMapOf(
      "entries" to AgentPromptPayloadValue.Arr(payloadEntries),
      "selectedCount" to AgentPromptPayload.num(normalizedSelection.size),
      "candidateCount" to AgentPromptPayload.num(normalizedSelection.size),
      "includedCount" to AgentPromptPayload.num(included.size),
      "statusCounts" to toPayloadStatusCounts(statusCounts),
    )
    consoleOutput?.let { excerpt ->
      payloadFields["consoleOutput"] = AgentPromptPayload.str(excerpt.text)
      payloadFields["consoleOutputFromSelection"] = AgentPromptPayload.bool(excerpt.fromSelection)
    }
    val payload = AgentPromptPayloadValue.Obj(payloadFields)
    val outputWasTruncated = consoleOutput?.let { excerpt ->
      excerpt.originalChars > excerpt.includedChars
    } == true
    val summaryWasTruncated = normalizedSelection.size > included.size

    return listOf(
      AgentPromptContextItem(
        rendererId = AgentPromptContextRendererIds.TEST_FAILURES,
        title = AgentPromptTestRunnerBundle.message("context.tests.title"),
        body = content,
        payload = payload,
        itemId = "testRunner.selection",
        source = "testRunner",
        truncation = AgentPromptContextTruncation(
          originalChars = fullContent.length + (consoleOutput?.originalChars ?: 0),
          includedChars = content.length + (consoleOutput?.includedChars ?: 0),
          reason = if (summaryWasTruncated || outputWasTruncated) {
            AgentPromptContextTruncationReason.SOURCE_LIMIT
          }
          else {
            AgentPromptContextTruncationReason.NONE
          },
        ),
      )
    )
  }

  private fun extractSelectedTests(dataContext: DataContext): List<AbstractTestProxy> {
    val fromArray = AbstractTestProxy.DATA_KEYS
      .getData(dataContext)
      ?.toList()
      .orEmpty()
    if (fromArray.isNotEmpty()) {
      return fromArray
    }

    return AbstractTestProxy.DATA_KEY
      .getData(dataContext)
      ?.let(::listOf)
      .orEmpty()
  }

  private fun isTestOwnedInvocation(dataContext: DataContext): Boolean {
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return true
    return ConsoleViewUtil.isConsoleViewEditor(editor)
  }

  private fun extractConsoleOutput(dataContext: DataContext): ConsoleOutputExcerpt? {
    val editor = consoleEditor(dataContext) ?: return null
    val documentChars = editor.document.immutableCharSequence
    val selectionModel = editor.selectionModel
    if (selectionModel.hasSelection()) {
      buildConsoleOutputExcerpt(
        rawText = documentChars,
        startOffset = selectionModel.selectionStart,
        endOffsetExclusive = selectionModel.selectionEnd,
        fromSelection = true,
      )?.let { excerpt ->
        return excerpt
      }
    }

    return buildConsoleOutputExcerpt(
      rawText = documentChars,
      startOffset = 0,
      endOffsetExclusive = documentChars.length,
      fromSelection = false,
    )
  }

  private fun consoleEditor(dataContext: DataContext): Editor? {
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return null
    return editor.takeIf { candidate -> ConsoleViewUtil.isConsoleViewEditor(candidate) }
  }

  private fun toSelectedContext(testProxy: AbstractTestProxy): SelectedTestContext {
    val locationUrl = testProxy.locationUrl
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
    val normalizedName = testProxy.name
                           .trim()
                           .takeIf { it.isNotEmpty() }
                         ?: locationUrl
                         ?: "<unnamed test>"
    return SelectedTestContext(
      name = normalizedName,
      locationUrl = locationUrl,
      reference = formatTestReference(name = normalizedName, locationUrl = locationUrl),
      status = resolveStatus(testProxy),
      assertionMessage = extractAssertionMessageHint(testProxy),
      isDefect = testProxy.isDefect,
    )
  }

  private fun resolveStatus(testProxy: AbstractTestProxy): String {
    return normalizeTestStatus(
      when {
        testProxy.isDefect -> "failed"
        testProxy.isIgnored -> "ignored"
        testProxy.isPassed -> "passed"
        testProxy.isInProgress -> "inProgress"
        else -> "unknown"
      }
    )
  }

  private fun extractAssertionMessageHint(testProxy: AbstractTestProxy): String? {
    val messageHint = sanitizeHint(testProxy.errorMessage)
    if (messageHint != null) {
      return messageHint
    }

    val firstStacktraceLine = testProxy.stacktrace
      ?.lineSequence()
      ?.map { it.trim() }
      ?.firstOrNull { it.isNotEmpty() }
    return sanitizeHint(firstStacktraceLine)
  }

  private fun sanitizeHint(rawValue: String?): String? {
    val normalized = rawValue
      ?.lineSequence()
      ?.map { it.trim() }
      ?.filter { it.isNotEmpty() }
      ?.joinToString(separator = " ")
      ?.replace(WHITESPACE_REGEX, " ")
      ?.trim()
      .orEmpty()
    if (normalized.isEmpty()) {
      return null
    }
    return if (normalized.length <= MAX_ASSERTION_HINT_CHARS) {
      normalized
    }
    else {
      normalized.take(MAX_ASSERTION_HINT_CHARS)
    }
  }

  private fun normalizeSelection(selection: List<SelectedTestContext>): List<SelectedTestContext> {
    if (selection.isEmpty()) {
      return emptyList()
    }

    val unique = LinkedHashMap<String, SelectedTestContext>()
    selection.forEach { entry ->
      val key = buildDedupKey(entry)
      val existing = unique[key]
      if (existing == null || shouldReplace(existing, entry)) {
        unique[key] = entry
      }
    }
    return unique.values.toList()
  }

  private fun buildDedupKey(entry: SelectedTestContext): String {
    return entry.locationUrl.orEmpty() + "|" + entry.name
  }

  private fun shouldReplace(existing: SelectedTestContext, candidate: SelectedTestContext): Boolean {
    if (existing.locationUrl == null && candidate.locationUrl != null) {
      return true
    }
    if (existing.assertionMessage == null && candidate.assertionMessage != null) {
      return true
    }
    if (!existing.isDefect && candidate.isDefect) {
      return true
    }
    return false
  }
}

private fun buildConsoleOutputExcerpt(
  rawText: CharSequence,
  startOffset: Int,
  endOffsetExclusive: Int,
  fromSelection: Boolean,
): ConsoleOutputExcerpt? {
  val safeStartOffset = startOffset.coerceIn(0, rawText.length)
  val safeEndOffset = endOffsetExclusive.coerceIn(safeStartOffset, rawText.length)
  val includedText = StringBuilder(MAX_CONSOLE_OUTPUT_CHARS.coerceAtMost(safeEndOffset - safeStartOffset))
  var originalChars = 0
  var hasRetainedLine = false
  var pendingBlankLines = 0

  forEachNormalizedLine(rawText, safeStartOffset, safeEndOffset) { lineStart, lineEnd ->
    if (isBlankLine(rawText, lineStart, lineEnd)) {
      if (hasRetainedLine) {
        pendingBlankLines++
      }
      return@forEachNormalizedLine
    }

    if (hasRetainedLine) {
      repeat(pendingBlankLines + 1) {
        appendIncludedNewline(includedText)
        originalChars++
      }
    }

    appendIncludedRange(includedText, rawText, lineStart, lineEnd)
    originalChars += lineEnd - lineStart
    hasRetainedLine = true
    pendingBlankLines = 0
  }

  if (!hasRetainedLine) {
    return null
  }

  return ConsoleOutputExcerpt(
    text = includedText.toString(),
    fromSelection = fromSelection,
    originalChars = originalChars,
    includedChars = includedText.length,
  )
}

private inline fun forEachNormalizedLine(
  text: CharSequence,
  startOffset: Int,
  endOffsetExclusive: Int,
  action: (lineStart: Int, lineEnd: Int) -> Unit,
) {
  var lineStart = startOffset
  var index = startOffset
  while (index < endOffsetExclusive) {
    when (text[index]) {
      '\n' -> {
        action(lineStart, index)
        index++
        lineStart = index
      }
      '\r' -> {
        action(lineStart, index)
        index++
        if (index < endOffsetExclusive && text[index] == '\n') {
          index++
        }
        lineStart = index
      }
      else -> index++
    }
  }
  action(lineStart, endOffsetExclusive)
}

private fun isBlankLine(text: CharSequence, startOffset: Int, endOffsetExclusive: Int): Boolean {
  for (index in startOffset until endOffsetExclusive) {
    if (!text[index].isWhitespace()) {
      return false
    }
  }
  return true
}

private fun appendIncludedRange(
  target: StringBuilder,
  text: CharSequence,
  startOffset: Int,
  endOffsetExclusive: Int,
) {
  var index = startOffset
  while (index < endOffsetExclusive && target.length < MAX_CONSOLE_OUTPUT_CHARS) {
    target.append(text[index])
    index++
  }
}

private fun appendIncludedNewline(target: StringBuilder) {
  if (target.length < MAX_CONSOLE_OUTPUT_CHARS) {
    target.append('\n')
  }
}

private fun renderLine(entry: SelectedTestContext): String {
  val anchor = entry.reference
  val assertionMessage = entry.assertionMessage ?: return "${entry.status}: $anchor"
  return "${entry.status}: $anchor | assertion: $assertionMessage"
}

private fun toPayloadStatusCounts(statusCounts: Map<String, Int>): AgentPromptPayloadValue.Obj {
  val fields = linkedMapOf<String, AgentPromptPayloadValue>()
  statusCounts.forEach { (status, count) ->
    fields[status] = AgentPromptPayload.num(count)
  }
  return AgentPromptPayloadValue.Obj(fields)
}
