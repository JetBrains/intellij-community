// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionProvenance
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionSubscription
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class AgentPromptSuggestionControllerTest {
  @Test
  fun reloadSuggestionsPublishesSubscriptionCandidates() {
    val observed = CopyOnWriteArrayList<List<AgentPromptSuggestionCandidate>>()
    val scope = controllerScope()
    val subscription = FakeSubscription(listOf(candidate(id = "tests.fix")))
    try {
      val controller = AgentPromptSuggestionController(
        popupScope = scope,
        subscriptionProvider = { subscription },
        onSuggestionsUpdated = observed::add,
      )

      controller.reloadSuggestions(request(label = "first"))

      waitForCondition { observed.lastOrNull()?.map(AgentPromptSuggestionCandidate::id) == listOf("tests.fix") }
      assertThat(observed.last().map(AgentPromptSuggestionCandidate::id)).containsExactly("tests.fix")
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun reloadSuggestionsAppliesLaterUpdateFromSameSubscription() {
    val observed = CopyOnWriteArrayList<List<AgentPromptSuggestionCandidate>>()
    val scope = controllerScope()
    val subscription = FakeSubscription(listOf(candidate(id = "tests.fix", promptText = "template")))
    try {
      val controller = AgentPromptSuggestionController(
        popupScope = scope,
        subscriptionProvider = { subscription },
        onSuggestionsUpdated = observed::add,
      )

      controller.reloadSuggestions(request(label = "first"))

      waitForCondition { observed.lastOrNull()?.singleOrNull()?.promptText == "template" }
      subscription.emit(
        candidate(
          id = "tests.fix",
          promptText = "polished",
          provenance = AgentPromptSuggestionProvenance.AI_POLISHED,
        )
      )
      waitForCondition { observed.lastOrNull()?.singleOrNull()?.promptText == "polished" }

      assertThat(observed.last().single().provenance).isEqualTo(AgentPromptSuggestionProvenance.AI_POLISHED)
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun duplicateInFlightRequestDoesNotAttachAnotherSubscription() {
    val observed = CopyOnWriteArrayList<List<AgentPromptSuggestionCandidate>>()
    val scope = controllerScope()
    val subscription = FakeSubscription()
    val subscriptionCallCount = AtomicInteger(0)
    try {
      val controller = AgentPromptSuggestionController(
        popupScope = scope,
        subscriptionProvider = {
          subscriptionCallCount.incrementAndGet()
          subscription
        },
        onSuggestionsUpdated = observed::add,
      )

      controller.reloadSuggestions(request(label = "first"))
      controller.reloadSuggestions(request(label = "first"))

      assertThat(subscriptionCallCount.get()).isEqualTo(1)

      subscription.emit(candidate(id = "tests.fix"))
      waitForCondition { observed.lastOrNull()?.map(AgentPromptSuggestionCandidate::id) == listOf("tests.fix") }
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun duplicateRenderedRequestDoesNotAttachAnotherSubscription() {
    val observed = CopyOnWriteArrayList<List<AgentPromptSuggestionCandidate>>()
    val scope = controllerScope()
    val subscription = FakeSubscription(listOf(candidate(id = "tests.fix")))
    val subscriptionCallCount = AtomicInteger(0)
    try {
      val controller = AgentPromptSuggestionController(
        popupScope = scope,
        subscriptionProvider = {
          subscriptionCallCount.incrementAndGet()
          subscription
        },
        onSuggestionsUpdated = observed::add,
      )

      controller.reloadSuggestions(request(label = "first", projectPath = "/work/project/"))
      waitForCondition { observed.lastOrNull()?.map(AgentPromptSuggestionCandidate::id) == listOf("tests.fix") }
      val updateCount = observed.size

      controller.reloadSuggestions(request(label = "first", projectPath = "/work/project"))
      Thread.sleep(50)

      assertThat(subscriptionCallCount.get()).isEqualTo(1)
      assertThat(observed).hasSize(updateCount)
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun differentRequestClosesPreviousSubscriptionAndIgnoresLaterUpdates() {
    val observed = CopyOnWriteArrayList<List<AgentPromptSuggestionCandidate>>()
    val scope = controllerScope()
    val firstSubscription = FakeSubscription(listOf(candidate(id = "template")))
    val secondSubscription = FakeSubscription(listOf(candidate(id = "fresh")))
    try {
      val controller = AgentPromptSuggestionController(
        popupScope = scope,
        subscriptionProvider = { request ->
          when (request.contextItems.single().title) {
            "first" -> firstSubscription
            else -> secondSubscription
          }
        },
        onSuggestionsUpdated = observed::add,
      )

      controller.reloadSuggestions(request(label = "first"))
      waitForCondition { observed.lastOrNull()?.map(AgentPromptSuggestionCandidate::id) == listOf("template") }

      controller.reloadSuggestions(request(label = "second"))
      waitForCondition { observed.lastOrNull()?.map(AgentPromptSuggestionCandidate::id) == listOf("fresh") }

      firstSubscription.emit(candidate(id = "stale"))
      Thread.sleep(50)

      assertThat(firstSubscription.closeCount).isEqualTo(1)
      assertThat(observed.last().map(AgentPromptSuggestionCandidate::id)).containsExactly("fresh")
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun targetModeChangeStartsNewSubscription() {
    val observed = CopyOnWriteArrayList<List<AgentPromptSuggestionCandidate>>()
    val scope = controllerScope()
    val subscriptionCallCount = AtomicInteger(0)
    try {
      val controller = AgentPromptSuggestionController(
        popupScope = scope,
        subscriptionProvider = { request ->
          subscriptionCallCount.incrementAndGet()
          FakeSubscription(listOf(candidate(id = request.targetModeId)))
        },
        onSuggestionsUpdated = observed::add,
      )

      controller.reloadSuggestions(request(label = "first", targetModeId = "NEW_TASK"))
      waitForCondition { observed.lastOrNull()?.map(AgentPromptSuggestionCandidate::id) == listOf("NEW_TASK") }

      controller.reloadSuggestions(request(label = "first", targetModeId = "EXISTING_TASK"))
      waitForCondition { observed.lastOrNull()?.map(AgentPromptSuggestionCandidate::id) == listOf("EXISTING_TASK") }

      assertThat(subscriptionCallCount.get()).isEqualTo(2)
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun projectPathChangeStartsNewSubscription() {
    val observed = CopyOnWriteArrayList<List<AgentPromptSuggestionCandidate>>()
    val scope = controllerScope()
    val subscriptionCallCount = AtomicInteger(0)
    try {
      val controller = AgentPromptSuggestionController(
        popupScope = scope,
        subscriptionProvider = { request ->
          subscriptionCallCount.incrementAndGet()
          FakeSubscription(listOf(candidate(id = request.projectPath.orEmpty())))
        },
        onSuggestionsUpdated = observed::add,
      )

      controller.reloadSuggestions(request(label = "first", projectPath = "/work/project"))
      waitForCondition { observed.lastOrNull()?.singleOrNull()?.id == "/work/project" }

      controller.reloadSuggestions(request(label = "first", projectPath = "/work/other"))
      waitForCondition { observed.lastOrNull()?.singleOrNull()?.id == "/work/other" }

      assertThat(subscriptionCallCount.get()).isEqualTo(2)
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun manualContextChangeStartsNewSubscription() {
    val observed = CopyOnWriteArrayList<List<AgentPromptSuggestionCandidate>>()
    val scope = controllerScope()
    val subscriptionCallCount = AtomicInteger(0)
    try {
      val controller = AgentPromptSuggestionController(
        popupScope = scope,
        subscriptionProvider = { request ->
          subscriptionCallCount.incrementAndGet()
          FakeSubscription(
            listOf(
              candidate(id = request.contextItems.joinToString(separator = ",") { it.rendererId })
            )
          )
        },
        onSuggestionsUpdated = observed::add,
      )

      controller.reloadSuggestions(request(label = "first"))
      waitForCondition { observed.lastOrNull()?.singleOrNull()?.id == AgentPromptContextRendererIds.FILE }

      controller.reloadSuggestions(
        request(
          label = "first",
          contextItems = listOf(
            contextItem(rendererId = AgentPromptContextRendererIds.FILE, title = "first"),
            contextItem(
              rendererId = AgentPromptContextRendererIds.PATHS,
              title = "manual",
              body = "file: /work/project/src/App.kt",
              source = "manual",
            ),
          ),
        )
      )
      waitForCondition { observed.lastOrNull()?.singleOrNull()?.id == "file,paths" }

      assertThat(subscriptionCallCount.get()).isEqualTo(2)
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun clearSuggestionsClosesActiveSubscriptionAndPublishesEmptyState() {
    val observed = CopyOnWriteArrayList<List<AgentPromptSuggestionCandidate>>()
    val scope = controllerScope()
    val subscription = FakeSubscription(listOf(candidate(id = "paths.plan")))
    try {
      val controller = AgentPromptSuggestionController(
        popupScope = scope,
        subscriptionProvider = { subscription },
        onSuggestionsUpdated = observed::add,
      )

      controller.reloadSuggestions(request(label = "first"))
      waitForCondition { observed.lastOrNull()?.map(AgentPromptSuggestionCandidate::id) == listOf("paths.plan") }

      controller.clearSuggestions()

      assertThat(subscription.closeCount).isEqualTo(1)
      assertThat(observed.last()).isEmpty()
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun disposeClosesActiveSubscriptionWithoutClearingSuggestions() {
    val observed = CopyOnWriteArrayList<List<AgentPromptSuggestionCandidate>>()
    val scope = controllerScope()
    val subscription = FakeSubscription(listOf(candidate(id = "paths.plan")))
    try {
      val controller = AgentPromptSuggestionController(
        popupScope = scope,
        subscriptionProvider = { subscription },
        onSuggestionsUpdated = observed::add,
      )

      controller.reloadSuggestions(request(label = "first"))
      waitForCondition { observed.lastOrNull()?.map(AgentPromptSuggestionCandidate::id) == listOf("paths.plan") }
      val updateCount = observed.size

      controller.dispose()
      Thread.sleep(50)

      assertThat(subscription.closeCount).isEqualTo(1)
      assertThat(observed).hasSize(updateCount)
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun missingSubscriptionClearsSuggestions() {
    val observed = CopyOnWriteArrayList<List<AgentPromptSuggestionCandidate>>()
    val scope = controllerScope()
    val available = AtomicInteger(1)
    val firstSubscription = FakeSubscription(listOf(candidate(id = "paths.plan")))
    try {
      val controller = AgentPromptSuggestionController(
        popupScope = scope,
        subscriptionProvider = { request ->
          when {
            request.contextItems.single().title == "first" && available.getAndSet(0) == 1 -> firstSubscription
            else -> null
          }
        },
        onSuggestionsUpdated = observed::add,
      )

      controller.reloadSuggestions(request(label = "first"))
      waitForCondition { observed.lastOrNull()?.map(AgentPromptSuggestionCandidate::id) == listOf("paths.plan") }

      controller.reloadSuggestions(request(label = "second"))

      waitForCondition { observed.lastOrNull()?.isEmpty() == true }
      assertThat(firstSubscription.closeCount).isEqualTo(1)
      assertThat(observed.last()).isEmpty()
    }
    finally {
      scope.cancel()
    }
  }

  private fun request(
    label: String,
    projectPath: String? = null,
    targetModeId: String = "NEW_TASK",
    contextItems: List<AgentPromptContextItem> = listOf(contextItem(rendererId = AgentPromptContextRendererIds.FILE, title = label)),
  ): AgentPromptSuggestionRequest {
    return AgentPromptSuggestionRequest(
      project = ProjectManager.getInstance().defaultProject,
      projectPath = projectPath,
      targetModeId = targetModeId,
      contextItems = contextItems,
    )
  }

  private fun contextItem(
    rendererId: String,
    title: String? = rendererId,
    body: String = "body",
    source: String = "unknown",
  ): AgentPromptContextItem {
    return AgentPromptContextItem(
      rendererId = rendererId,
      title = title,
      body = body,
      source = source,
    )
  }

  private fun candidate(
    id: String,
    promptText: String = id,
    provenance: AgentPromptSuggestionProvenance = AgentPromptSuggestionProvenance.TEMPLATE,
  ): AgentPromptSuggestionCandidate {
    return AgentPromptSuggestionCandidate(
      id = id,
      label = id,
      promptText = promptText,
      provenance = provenance,
    )
  }

  @Suppress("RAW_SCOPE_CREATION")
  private fun controllerScope(): CoroutineScope {
    return CoroutineScope(SupervisorJob() + Dispatchers.Default)
  }

  private fun waitForCondition(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      if (condition()) {
        return
      }
      Thread.sleep(10)
    }
    error("Condition was not satisfied within ${timeoutMs}ms")
  }

  private class FakeSubscription(
    initialCandidates: List<AgentPromptSuggestionCandidate> = emptyList(),
  ) : AgentPromptSuggestionSubscription {
    private val candidates = MutableStateFlow(initialCandidates)
    private val closed = AtomicInteger(0)

    override val currentCandidates: List<AgentPromptSuggestionCandidate>
      get() = candidates.value

    override val updates: Flow<List<AgentPromptSuggestionCandidate>>
      get() = candidates

    override fun close() {
      closed.incrementAndGet()
    }

    val closeCount: Int
      get() = closed.get()

    fun emit(vararg nextCandidates: AgentPromptSuggestionCandidate) {
      candidates.value = nextCandidates.toList()
    }
  }
}
