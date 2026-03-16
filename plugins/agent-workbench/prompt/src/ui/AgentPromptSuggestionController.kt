// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptSuggestionCandidate
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptSuggestionRequest
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class AgentPromptSuggestionController(
  private val popupScope: CoroutineScope,
  private val subscriptionProvider: (AgentPromptSuggestionRequest) -> AgentPromptSuggestionSubscription? = { request ->
    request.project.service<AgentPromptSuggestionSessionService>().attach(request)
  },
  private val onSuggestionsUpdated: (List<AgentPromptSuggestionCandidate>) -> Unit,
) {
  private var requestVersion: Long = 0
  private var activeRequestKey: AgentPromptSuggestionRequestKey? = null
  private var activeSubscription: AgentPromptSuggestionSubscription? = null
  private var activeCollectionJob: Job? = null
  private var lastRenderedSuggestions: List<AgentPromptSuggestionCandidate> = emptyList()

  fun dispose() {
    requestVersion += 1
    activeRequestKey = null
    releaseActiveSubscription()
  }

  fun clearSuggestions() {
    requestVersion += 1
    activeRequestKey = null
    releaseActiveSubscription()
    renderSuggestions(emptyList())
  }

  fun reloadSuggestions(request: AgentPromptSuggestionRequest) {
    val requestKey = computePromptSuggestionRequestKey(request)
    if (requestKey == activeRequestKey) {
      return
    }

    val subscription = subscriptionProvider(request)
    if (subscription == null) {
      clearSuggestions()
      return
    }

    val version = requestVersion + 1
    requestVersion = version
    releaseActiveSubscription()
    activeRequestKey = requestKey
    activeSubscription = subscription
    renderSuggestions(subscription.currentCandidates)

    var launchedJob: Job? = null
    launchedJob = popupScope.launch {
      try {
        subscription.updates.collect { candidates ->
          if (version != requestVersion || activeSubscription !== subscription) {
            return@collect
          }
          renderSuggestions(candidates)
        }
      }
      finally {
        if (activeCollectionJob === launchedJob) {
          activeCollectionJob = null
        }
      }
    }
    activeCollectionJob = launchedJob
  }

  private fun releaseActiveSubscription() {
    activeCollectionJob?.cancel()
    activeCollectionJob = null
    activeSubscription?.close()
    activeSubscription = null
  }

  private fun renderSuggestions(candidates: List<AgentPromptSuggestionCandidate>) {
    if (candidates == lastRenderedSuggestions) {
      return
    }
    lastRenderedSuggestions = candidates
    onSuggestionsUpdated(candidates)
  }
}
