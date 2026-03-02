// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.testrunner.context

import com.intellij.agent.workbench.prompt.testrunner.AgentPromptTestRunnerBundle
import com.intellij.agent.workbench.prompt.testrunner.computeTestStatusCounts
import com.intellij.agent.workbench.prompt.testrunner.formatTestReference
import com.intellij.agent.workbench.prompt.testrunner.normalizeTestStatus
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextContributorPhase
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncation
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayload
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayloadValue
import com.intellij.execution.testframework.AbstractTestProxy

private const val MAX_INCLUDED_SELECTION_TESTS = 5
private const val MAX_ASSERTION_HINT_CHARS = 180
private val WHITESPACE_REGEX = Regex("\\s+")

private data class SelectedTestContext(
  @JvmField val name: String,
  @JvmField val locationUrl: String?,
  @JvmField val reference: String,
  @JvmField val status: String,
  @JvmField val assertionMessage: String?,
  @JvmField val isDefect: Boolean,
)

internal class AgentPromptTestSelectionContextContributor : AgentPromptContextContributorBridge {
  override val phase: AgentPromptContextContributorPhase
    get() = AgentPromptContextContributorPhase.INVOCATION

  override val order: Int
    get() = 40

  override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
    val selectedTests = extractSelectedTests(invocationData)
      .map(::toSelectedContext)
    val normalizedSelection = normalizeSelection(selectedTests)
    if (normalizedSelection.isEmpty()) {
      return emptyList()
    }

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
    val payload = AgentPromptPayload.obj(
      "entries" to AgentPromptPayloadValue.Arr(payloadEntries),
      "selectedCount" to AgentPromptPayload.num(normalizedSelection.size),
      "candidateCount" to AgentPromptPayload.num(normalizedSelection.size),
      "includedCount" to AgentPromptPayload.num(included.size),
      "statusCounts" to toPayloadStatusCounts(statusCounts),
    )

    return listOf(
      AgentPromptContextItem(
        rendererId = AgentPromptContextRendererIds.TEST_FAILURES,
        title = AgentPromptTestRunnerBundle.message("context.tests.title"),
        body = content,
        payload = payload,
        source = "testRunner",
        truncation = AgentPromptContextTruncation(
          originalChars = fullContent.length,
          includedChars = content.length,
          reason = if (normalizedSelection.size > included.size) {
            AgentPromptContextTruncationReason.SOURCE_LIMIT
          }
          else {
            AgentPromptContextTruncationReason.NONE
          },
        ),
      )
    )
  }

  private fun extractSelectedTests(invocationData: AgentPromptInvocationData): List<AbstractTestProxy> {
    val dataContext = invocationData.dataContextOrNull() ?: return emptyList()
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
