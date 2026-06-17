// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.chat

import com.intellij.agent.workbench.chat.AgentChatBundle
import com.intellij.agent.workbench.chat.AgentChatSemanticRegion
import com.intellij.agent.workbench.chat.AgentChatSemanticRegionDetector
import com.intellij.agent.workbench.chat.AgentChatSemanticRegionKind
import com.intellij.agent.workbench.chat.lineText
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOutputModelSnapshot

internal object CodexSemanticRegionDetector : AgentChatSemanticRegionDetector {
  override fun detect(snapshot: TerminalOutputModelSnapshot): List<AgentChatSemanticRegion> {
    if (snapshot.lineCount == 0) {
      return emptyList()
    }

    val firstScanLine = if (snapshot.lineCount > CODEX_TUI_PLAN_SCAN_LIMIT_LINES) {
      snapshot.lastLineIndex - (CODEX_TUI_PLAN_SCAN_LIMIT_LINES - 1).toLong()
    }
    else {
      snapshot.firstLineIndex
    }

    val regions = mutableListOf<AgentChatSemanticRegion>()
    val sameHashCounts = mutableMapOf<String, Int>()
    var line = firstScanLine
    while (line <= snapshot.lastLineIndex) {
      val trimmed = snapshot.lineText(line).trim()
      val kind = when (trimmed) {
        CODEX_TUI_PROPOSED_PLAN_HEADER -> AgentChatSemanticRegionKind.PROPOSED_PLAN
        CODEX_TUI_UPDATED_PLAN_HEADER -> AgentChatSemanticRegionKind.UPDATED_PLAN
        else -> null
      }
      if (kind == null) {
        line += 1L
        continue
      }

      val region = parseCodexPlanSection(snapshot, line, kind, sameHashCounts)
      if (region == null) {
        line += 1L
        continue
      }

      regions += region.first
      line = region.second + 1L
    }
    return regions
  }
}

private fun parseCodexPlanSection(
  snapshot: TerminalOutputModelSnapshot,
  headerLine: TerminalLineIndex,
  kind: AgentChatSemanticRegionKind,
  sameHashCounts: MutableMap<String, Int>,
): Pair<AgentChatSemanticRegion, TerminalLineIndex>? {
  val summaryCandidates = mutableListOf<String>()
  var lastContentLine = headerLine
  var line = headerLine + 1L
  var sawContent = false
  var trailingBlankCount = 0
  while (line <= snapshot.lastLineIndex) {
    val text = snapshot.lineText(line)
    val trimmed = text.trim()
    if (isCodexTuiSectionHeader(text)) {
      break
    }
    if (trimmed.isNotEmpty() && !text.startsWith(' ')) {
      break
    }
    if (trimmed.isEmpty()) {
      trailingBlankCount++
      if (sawContent && trailingBlankCount >= 2) {
        break
      }
      line += 1L
      continue
    }
    trailingBlankCount = 0
    sawContent = true
    summaryCandidates += text
    lastContentLine = line
    line += 1L
  }

  if (!sawContent) {
    return null
  }

  val startOffset = (snapshot.getStartOfLine(headerLine) - snapshot.startOffset).toInt()
  val endOffset = (snapshot.getEndOfLine(lastContentLine, includeEOL = true) - snapshot.startOffset).toInt()
  if (endOffset <= startOffset) {
    return null
  }

  val text = snapshot.getText(snapshot.startOffset + startOffset.toLong(), snapshot.startOffset + endOffset.toLong()).toString()
  val contentHash = text.hashCode().toString(16)
  val ordinal = (sameHashCounts[contentHash] ?: 0) + 1
  sameHashCounts[contentHash] = ordinal
  return AgentChatSemanticRegion(
    id = "$contentHash:$ordinal",
    kind = kind,
    summary = summarizePlanSection(summaryCandidates, kind),
    startOffset = startOffset,
    endOffset = endOffset,
    startLine = (headerLine - snapshot.firstLineIndex).toInt(),
    endLine = (lastContentLine - snapshot.firstLineIndex).toInt(),
  ) to lastContentLine
}

private fun isCodexTuiSectionHeader(lineText: String): Boolean {
  return lineText.startsWith("\u2022 ") && lineText.length > 2 && lineText[2].isLetter()
}

private fun summarizePlanSection(lines: List<String>, kind: AgentChatSemanticRegionKind): String {
  val summary = lines.asSequence()
    .map(String::trim)
    .filter { it.isNotEmpty() && it != "```" }
    .map { line -> CODEX_TUI_MARKDOWN_PREFIX_REGEX.replace(line, "") }
    .map { line -> CODEX_TUI_PLAN_STEP_PREFIX_REGEX.replace(line, "") }
    .firstOrNull()
    ?.let(::truncateSummary)
  val fallbackKey = when (kind) {
    AgentChatSemanticRegionKind.PROPOSED_PLAN -> "chat.semantic.region.proposed.plan"
    AgentChatSemanticRegionKind.UPDATED_PLAN -> "chat.semantic.region.updated.plan"
  }
  return summary ?: AgentChatBundle.message(fallbackKey)
}

private fun truncateSummary(text: String): String {
  return if (text.length <= CODEX_TUI_PLAN_SUMMARY_MAX_LENGTH) {
    text
  }
  else {
    text.take(CODEX_TUI_PLAN_SUMMARY_MAX_LENGTH - 3) + "..."
  }
}

private const val CODEX_TUI_PLAN_SCAN_LIMIT_LINES: Int = 2_000
private const val CODEX_TUI_PLAN_SUMMARY_MAX_LENGTH: Int = 80
private const val CODEX_TUI_PROPOSED_PLAN_HEADER = "\u2022 Proposed Plan"
private const val CODEX_TUI_UPDATED_PLAN_HEADER = "\u2022 Updated Plan"
private val CODEX_TUI_MARKDOWN_PREFIX_REGEX = Regex("^(#+|[-*+]|\\d+[.)]) +")
private val CODEX_TUI_PLAN_STEP_PREFIX_REGEX = Regex("^([\u2514\u2714\u2612\u25a1] +)+")
