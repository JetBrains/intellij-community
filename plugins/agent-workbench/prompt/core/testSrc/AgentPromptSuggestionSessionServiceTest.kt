// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class AgentPromptSuggestionSessionServiceTest {
  @Test
  fun roundedUnselectedEditorContextReusesSameRetainedSession() {
    val scope = serviceScope()
    val generatorCallCount = AtomicInteger(0)
    try {
      val service = AgentPromptSuggestionSessionService(
        serviceScope = scope,
        generatorProvider = {
          AgentPromptSuggestionGenerator {
            val call = generatorCallCount.incrementAndGet()
            flowOf(AgentPromptSuggestionUpdate(listOf(candidate(id = "call-$call"))))
          }
        },
        idleRetentionMs = 50L,
      )

      val firstSubscription = checkNotNull(
        service.attach(
          request(
            label = "first",
            contextItems = editorContextItems(
              symbolName = "main",
              selection = false,
              snippetTitle = "Lines 10-16",
              snippetBody = "val answer = 42",
              startLine = 10,
              endLine = 16,
            ),
          )
        )
      )
      waitForCondition { firstSubscription.currentCandidates.singleOrNull()?.id == "call-1" }
      firstSubscription.close()

      Thread.sleep(20)

      val secondSubscription = checkNotNull(
        service.attach(
          request(
            label = "first",
            contextItems = editorContextItems(
              symbolName = "main",
              selection = false,
              snippetTitle = "Lines 40-46",
              snippetBody = "return helper()",
              startLine = 40,
              endLine = 46,
              truncation = AgentPromptContextTruncation(
                originalChars = 500,
                includedChars = 120,
                reason = AgentPromptContextTruncationReason.SOURCE_LIMIT,
              ),
            ),
          )
        )
      )

      assertThat(generatorCallCount.get()).isEqualTo(1)
      assertThat(secondSubscription.currentCandidates.single().id).isEqualTo("call-1")

      secondSubscription.close()
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun closingLastSubscriptionCancelsInFlightGenerationImmediately() {
    val scope = serviceScope()
    val firstCancelled = CompletableDeferred<Unit>()
    val generatorCallCount = AtomicInteger(0)
    try {
      val service = AgentPromptSuggestionSessionService(
        serviceScope = scope,
        generatorProvider = {
          AgentPromptSuggestionGenerator {
            val call = generatorCallCount.incrementAndGet()
            flow {
              emit(AgentPromptSuggestionUpdate(listOf(candidate(id = "call-$call"))))
              try {
                awaitCancellation()
              }
              finally {
                if (call == 1) {
                  firstCancelled.complete(Unit)
                }
              }
            }
          }
        },
        idleRetentionMs = 5_000L,
      )

      val firstSubscription = checkNotNull(service.attach(request(label = "first")))
      waitForCondition { firstSubscription.currentCandidates.map(AgentPromptSuggestionCandidate::id) == listOf("call-1") }
      firstSubscription.close()

      waitForCondition(timeoutMs = 500) { firstCancelled.isCompleted }
      val secondSubscription = checkNotNull(service.attach(request(label = "first")))
      waitForCondition { secondSubscription.currentCandidates.map(AgentPromptSuggestionCandidate::id) == listOf("call-2") }

      assertThat(generatorCallCount.get()).isEqualTo(2)
      assertThat(secondSubscription.currentCandidates.map(AgentPromptSuggestionCandidate::id)).containsExactly("call-2")

      secondSubscription.close()
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun completedRequestIsReusedWithinRetentionWindow() {
    val scope = serviceScope()
    val generatorCallCount = AtomicInteger(0)
    try {
      val service = AgentPromptSuggestionSessionService(
        serviceScope = scope,
        generatorProvider = {
          AgentPromptSuggestionGenerator {
            generatorCallCount.incrementAndGet()
            flowOf(AgentPromptSuggestionUpdate(listOf(candidate(id = "tests.fix", provenance = AgentPromptSuggestionProvenance.AI_POLISHED))))
          }
        },
        idleRetentionMs = 50L,
      )

      val firstSubscription = checkNotNull(service.attach(request(label = "first")))
      waitForCondition { firstSubscription.currentCandidates.map(AgentPromptSuggestionCandidate::id) == listOf("tests.fix") }
      firstSubscription.close()

      Thread.sleep(20)
      val secondSubscription = checkNotNull(service.attach(request(label = "first")))

      assertThat(generatorCallCount.get()).isEqualTo(1)
      assertThat(secondSubscription.currentCandidates.single().provenance).isEqualTo(AgentPromptSuggestionProvenance.AI_POLISHED)

      secondSubscription.close()
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun requestIsEvictedAfterIdleTimeout() {
    val scope = serviceScope()
    val generatorCallCount = AtomicInteger(0)
    try {
      val service = AgentPromptSuggestionSessionService(
        serviceScope = scope,
        generatorProvider = {
          AgentPromptSuggestionGenerator {
            val call = generatorCallCount.incrementAndGet()
            flowOf(AgentPromptSuggestionUpdate(listOf(candidate(id = "call-$call"))))
          }
        },
        idleRetentionMs = 50L,
      )

      val firstSubscription = checkNotNull(service.attach(request(label = "first")))
      waitForCondition { firstSubscription.currentCandidates.singleOrNull()?.id == "call-1" }
      firstSubscription.close()

      Thread.sleep(120)

      val secondSubscription = checkNotNull(service.attach(request(label = "first")))
      waitForCondition { secondSubscription.currentCandidates.singleOrNull()?.id == "call-2" }

      assertThat(generatorCallCount.get()).isEqualTo(2)

      secondSubscription.close()
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun differentRequestReplacesRetainedSessionImmediately() {
    val scope = serviceScope()
    val firstStarted = CompletableDeferred<Unit>()
    val firstCancelled = CompletableDeferred<Unit>()
    val generatorCallCount = AtomicInteger(0)
    try {
      val service = AgentPromptSuggestionSessionService(
        serviceScope = scope,
        generatorProvider = {
          AgentPromptSuggestionGenerator { request ->
            generatorCallCount.incrementAndGet()
            flow {
              if (request.contextItems.single().title == "first") {
                firstStarted.complete(Unit)
                try {
                  awaitCancellation()
                }
                finally {
                  firstCancelled.complete(Unit)
                }
              }
              else {
                emit(AgentPromptSuggestionUpdate(listOf(candidate(id = "fresh"))))
              }
            }
          }
        },
        idleRetentionMs = 50L,
      )

      val firstSubscription = checkNotNull(service.attach(request(label = "first")))
      waitForCondition { firstStarted.isCompleted }

      val secondSubscription = checkNotNull(service.attach(request(label = "second")))
      waitForCondition { firstCancelled.isCompleted }
      waitForCondition { secondSubscription.currentCandidates.map(AgentPromptSuggestionCandidate::id) == listOf("fresh") }

      assertThat(generatorCallCount.get()).isEqualTo(2)

      firstSubscription.close()
      secondSubscription.close()
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun reopeningOriginalContextAfterIntermediateSwitchStartsOnlyFreshGenerations() {
    val scope = serviceScope()
    val startedLabels = mutableListOf<String>()
    val cancelledLabels = mutableListOf<String>()
    val generatorCallCount = AtomicInteger(0)
    try {
      val service = AgentPromptSuggestionSessionService(
        serviceScope = scope,
        generatorProvider = {
          AgentPromptSuggestionGenerator { request ->
            val label = request.contextItems.single().title ?: "missing"
            val call = generatorCallCount.incrementAndGet()
            flow {
              synchronized(startedLabels) {
                startedLabels += label
              }
              emit(AgentPromptSuggestionUpdate(listOf(candidate(id = "call-$call"))))
              try {
                awaitCancellation()
              }
              finally {
                synchronized(cancelledLabels) {
                  cancelledLabels += label
                }
              }
            }
          }
        },
        idleRetentionMs = 50L,
      )

      val firstSubscription = checkNotNull(service.attach(request(label = "first")))
      waitForCondition { firstSubscription.currentCandidates.map(AgentPromptSuggestionCandidate::id) == listOf("call-1") }
      firstSubscription.close()
      waitForCondition(timeoutMs = 500) { synchronized(cancelledLabels) { cancelledLabels.count { it == "first" } } == 1 }

      val secondSubscription = checkNotNull(service.attach(request(label = "second")))
      waitForCondition { secondSubscription.currentCandidates.map(AgentPromptSuggestionCandidate::id) == listOf("call-2") }
      secondSubscription.close()
      waitForCondition(timeoutMs = 500) { synchronized(cancelledLabels) { cancelledLabels.count { it == "second" } } == 1 }

      val thirdSubscription = checkNotNull(service.attach(request(label = "first")))
      waitForCondition { thirdSubscription.currentCandidates.map(AgentPromptSuggestionCandidate::id) == listOf("call-3") }

      assertThat(generatorCallCount.get()).isEqualTo(3)
      assertThat(startedLabels).containsExactly("first", "second", "first")

      thirdSubscription.close()
    }
    finally {
      scope.cancel()
    }
  }

  @Test
  fun roundedUnselectedEditorSnippetKeyIgnoresCaretWindowDetails() {
    val first = computePromptSuggestionRequestKey(
      request(
        label = "first",
        contextItems = editorContextItems(
          symbolName = "main",
          selection = false,
          snippetTitle = "Lines 10-16",
          snippetBody = "val answer = 42",
          startLine = 10,
          endLine = 16,
        ),
      )
    )
    val second = computePromptSuggestionRequestKey(
      request(
        label = "first",
        contextItems = editorContextItems(
          symbolName = "main",
          selection = false,
          snippetTitle = "Lines 40-46",
          snippetBody = "return helper()",
          startLine = 40,
          endLine = 46,
          truncation = AgentPromptContextTruncation(
            originalChars = 500,
            includedChars = 120,
            reason = AgentPromptContextTruncationReason.SOURCE_LIMIT,
          ),
        ),
      )
    )

    assertThat(first).isEqualTo(second)
  }

  @Test
  fun editorSymbolChangeChangesRoundedSuggestionKey() {
    val first = computePromptSuggestionRequestKey(
      request(
        label = "first",
        contextItems = editorContextItems(
          symbolName = "main",
          selection = false,
          snippetTitle = "Lines 10-16",
          snippetBody = "val answer = 42",
          startLine = 10,
          endLine = 16,
        ),
      )
    )
    val second = computePromptSuggestionRequestKey(
      request(
        label = "first",
        contextItems = editorContextItems(
          symbolName = "helper",
          selection = false,
          snippetTitle = "Lines 40-46",
          snippetBody = "return helper()",
          startLine = 40,
          endLine = 46,
        ),
      )
    )

    assertThat(first).isNotEqualTo(second)
  }

  @Test
  fun selectedEditorSnippetChangeStillChangesSuggestionKey() {
    val first = computePromptSuggestionRequestKey(
      request(
        label = "first",
        contextItems = editorContextItems(
          symbolName = "main",
          selection = true,
          snippetTitle = "Selection",
          snippetBody = "val answer = 42",
          startLine = 10,
          endLine = 16,
        ),
      )
    )
    val second = computePromptSuggestionRequestKey(
      request(
        label = "first",
        contextItems = editorContextItems(
          symbolName = "main",
          selection = true,
          snippetTitle = "Selection",
          snippetBody = "return helper()",
          startLine = 10,
          endLine = 16,
        ),
      )
    )

    assertThat(first).isNotEqualTo(second)
  }

  @Test
  fun nonEditorContextStillUsesExactSuggestionKey() {
    val first = computePromptSuggestionRequestKey(
      request(
        label = "first",
        contextItems = listOf(
          contextItem(
            rendererId = AgentPromptContextRendererIds.PATHS,
            title = "paths",
            body = "/work/project/src/App.kt",
            source = "manual",
          )
        ),
      )
    )
    val second = computePromptSuggestionRequestKey(
      request(
        label = "first",
        contextItems = listOf(
          contextItem(
            rendererId = AgentPromptContextRendererIds.PATHS,
            title = "paths",
            body = "/work/project/src/Other.kt",
            source = "manual",
          )
        ),
      )
    )

    assertThat(first).isNotEqualTo(second)
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

  private fun editorContextItems(
    symbolName: String?,
    selection: Boolean,
    snippetTitle: String,
    snippetBody: String,
    startLine: Int,
    endLine: Int,
    truncation: AgentPromptContextTruncation = AgentPromptContextTruncation.none(snippetBody.length),
  ): List<AgentPromptContextItem> {
    val filePath = "/work/project/src/App.kt"
    return buildList {
      add(
        AgentPromptContextItem(
          rendererId = AgentPromptContextRendererIds.FILE,
          title = "File",
          body = filePath,
          payload = AgentPromptPayload.obj("path" to AgentPromptPayload.str(filePath)),
          itemId = "editor.file",
          source = "editor",
          truncation = AgentPromptContextTruncation.none(filePath.length),
        )
      )
      if (symbolName != null) {
        add(
          AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.SYMBOL,
            title = "Symbol",
            body = symbolName,
            payload = AgentPromptPayload.obj("symbol" to AgentPromptPayload.str(symbolName)),
            itemId = "editor.symbol",
            parentItemId = "editor.file",
            source = "editor",
            truncation = AgentPromptContextTruncation.none(symbolName.length),
          )
        )
      }
      add(
        AgentPromptContextItem(
          rendererId = AgentPromptContextRendererIds.SNIPPET,
          title = snippetTitle,
          body = snippetBody,
          payload = AgentPromptPayload.obj(
            "startLine" to AgentPromptPayload.num(startLine),
            "endLine" to AgentPromptPayload.num(endLine),
            "selection" to AgentPromptPayload.bool(selection),
            "language" to AgentPromptPayload.str("kotlin"),
          ),
          itemId = "editor.snippet",
          parentItemId = "editor.file",
          source = "editor",
          truncation = truncation,
        )
      )
    }
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
  private fun serviceScope(): CoroutineScope {
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
}
