// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.agent.workbench.codex.common.CodexAppServerClient
import com.intellij.agent.workbench.codex.common.CodexAppServerNotificationRouting
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionRequest
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionResult
import com.intellij.agent.workbench.codex.sessions.registerShutdownOnCancellation
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Service(Service.Level.APP)
class CodexPromptSuggestionAppServerService private constructor(
  serviceScope: CoroutineScope,
  private val delegate: PromptSuggestionDelegate,
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    delegate = AppServerPromptSuggestionDelegate(serviceScope),
  )

  internal constructor(
    serviceScope: CoroutineScope,
    suggestWithClient: suspend (CodexPromptSuggestionRequest) -> CodexPromptSuggestionResult?,
    shutdownClient: () -> Unit = {},
  ) : this(
    serviceScope = serviceScope,
    delegate = FunctionPromptSuggestionDelegate(
      suggestWithClient = suggestWithClient,
      shutdownClient = shutdownClient,
    ),
  )

  private val suggestionMutex = Mutex()

  init {
    registerShutdownOnCancellation(serviceScope) { delegate.shutdown() }
  }

  internal suspend fun suggestPrompt(request: CodexPromptSuggestionRequest): CodexPromptSuggestionResult? {
    return suggestionMutex.withLock {
      currentCoroutineContext().ensureActive()
      delegate.suggestPrompt(request)
    }
  }
}

private interface PromptSuggestionDelegate {
  suspend fun suggestPrompt(request: CodexPromptSuggestionRequest): CodexPromptSuggestionResult?

  fun shutdown()
}

private class AppServerPromptSuggestionDelegate(serviceScope: CoroutineScope) : PromptSuggestionDelegate {
  val client = CodexAppServerClient(
    coroutineScope = serviceScope,
    notificationRouting = CodexAppServerNotificationRouting.PARSED_ONLY,
  )

  override suspend fun suggestPrompt(request: CodexPromptSuggestionRequest): CodexPromptSuggestionResult? {
    return client.suggestPrompt(request)
  }

  override fun shutdown() {
    client.shutdown()
  }
}

private class FunctionPromptSuggestionDelegate(
  private val suggestWithClient: suspend (CodexPromptSuggestionRequest) -> CodexPromptSuggestionResult?,
  private val shutdownClient: () -> Unit,
) : PromptSuggestionDelegate {
  override suspend fun suggestPrompt(request: CodexPromptSuggestionRequest): CodexPromptSuggestionResult? {
    return suggestWithClient(request)
  }

  override fun shutdown() {
    shutdownClient()
  }
}
