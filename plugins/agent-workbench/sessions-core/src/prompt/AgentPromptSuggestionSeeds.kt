// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.prompt

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

object AgentPromptSuggestionSeeds {
  fun buildDefaultSuggestions(contextItems: List<AgentPromptContextItem>): List<AgentPromptSuggestionCandidate> {
    val testsContext = contextItems.firstOrNull { it.rendererId == AgentPromptContextRendererIds.TEST_FAILURES }
    if (testsContext != null) {
      return if (extractFailedTestCount(testsContext) > 0) {
        listOf(
          suggestion("tests.fix"),
          suggestion("tests.explain"),
          suggestion("tests.stabilize"),
        )
      }
      else {
        listOf(
          suggestion("tests.coverage"),
          suggestion("tests.review"),
          suggestion("tests.extend"),
        )
      }
    }

    if (contextItems.any { it.rendererId == AgentPromptContextRendererIds.VCS_COMMITS }) {
      return listOf(
        suggestion("vcs.review"),
        suggestion("vcs.summary"),
        suggestion("vcs.trace"),
      )
    }

    if (contextItems.any(::isEditorContext)) {
      return listOf(
        suggestion("editor.explain"),
        suggestion("editor.refactor"),
        suggestion("editor.review"),
      )
    }

    if (contextItems.any { it.rendererId == AgentPromptContextRendererIds.PATHS }) {
      return listOf(
        suggestion("paths.plan"),
        suggestion("paths.summary"),
        suggestion("paths.impact"),
      )
    }

    return emptyList()
  }

  private fun isEditorContext(item: AgentPromptContextItem): Boolean {
    return item.rendererId == AgentPromptContextRendererIds.SNIPPET ||
           item.rendererId == AgentPromptContextRendererIds.FILE ||
           item.rendererId == AgentPromptContextRendererIds.SYMBOL
  }

  private fun extractFailedTestCount(item: AgentPromptContextItem): Int {
    val payloadFields = item.payload.objOrNull()?.fields.orEmpty()
    parseCount(payloadFields["statusCounts"]?.objOrNull()?.fields?.get("failed"))?.let { failedCount ->
      return failedCount
    }
    return item.body.lineSequence().count { line ->
      line.trimStart().startsWith("failed:", ignoreCase = true)
    }
  }

  private fun parseCount(value: AgentPromptPayloadValue?): Int? {
    return when (value) {
      is AgentPromptPayloadValue.Num -> value.value.toIntOrNull()
      is AgentPromptPayloadValue.Str -> value.value.toIntOrNull()
      else -> null
    }
  }

  private fun suggestion(id: String): AgentPromptSuggestionCandidate {
    return AgentPromptSuggestionCandidate(
      id = id,
      label = AgentPromptSuggestionBundle.message("popup.suggestion.$id.label"),
      promptText = AgentPromptSuggestionBundle.message("popup.suggestion.$id.prompt"),
    )
  }
}
