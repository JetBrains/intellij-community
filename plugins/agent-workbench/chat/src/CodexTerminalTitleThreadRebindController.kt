// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.buildAgentThreadIdentity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.TerminalTitleListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

private val LOG = logger<CodexTerminalTitleThreadRebindController>()

internal fun createCodexTerminalTitleThreadRebindController(
  file: AgentChatVirtualFile,
  tab: AgentChatTerminalTab,
  tabSnapshotWriter: AgentChatTabSnapshotWriter,
): AgentChatDisposableController? {
  if (!file.isCodexTopLevelThread()) {
    return null
  }
  val terminalTitle = tab.terminalView?.title ?: return null
  return CodexTerminalTitleThreadRebindController(
    file = file,
    tabSnapshotWriter = tabSnapshotWriter,
  ).also { controller ->
    controller.attach(terminalTitle = terminalTitle, parentScope = tab.coroutineScope)
  }
}

internal class CodexTerminalTitleThreadRebindController(
  private val file: AgentChatVirtualFile,
  private val tabSnapshotWriter: AgentChatTabSnapshotWriter,
  private val rebindPendingTabs: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = ::rebindOpenPendingAgentChatTabs,
  private val rebindConcreteTabs: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatConcreteTabRebindRequest>>,
  ) -> AgentChatConcreteTabRebindReport = ::rebindOpenConcreteAgentChatTabs,
  private val notifyRefresh: (AgentSessionProvider, String, String?, AgentThreadActivity?) -> Unit = ::notifyAgentChatScopedRefresh,
  private val currentTimeProvider: () -> Long = System::currentTimeMillis,
) : AgentChatDisposableController {
  private var listenerDisposable: Disposable? = null
  private var rebindJob: Job? = null
  private var observedThreadId: String? = null

  fun attach(terminalTitle: TerminalTitle, parentScope: CoroutineScope) {
    if (listenerDisposable != null) {
      return
    }

    val disposable = Disposer.newDisposable("Codex terminal title thread rebind")
    listenerDisposable = disposable
    terminalTitle.addTitleListener(
      object : TerminalTitleListener {
        override fun onTitleChanged(terminalTitle: TerminalTitle) {
          bindFromApplicationTitle(terminalTitle.applicationTitle, parentScope)
        }
      },
      disposable,
    )
    bindFromApplicationTitle(terminalTitle.applicationTitle, parentScope)
  }

  @Synchronized
  internal fun bindFromApplicationTitle(applicationTitle: String?, parentScope: CoroutineScope): Boolean {
    val threadId = extractCodexThreadIdFromTerminalTitle(applicationTitle) ?: return false
    if (!file.isCodexTopLevelThread() || file.projectPath.isBlank()) {
      return false
    }
    if (observedThreadId == threadId || rebindJob?.isActive == true) {
      return false
    }

    val projectPath = file.projectPath
    val request = buildRebindRequest(projectPath = projectPath, threadId = threadId) ?: return false

    observedThreadId = threadId
    val job = parentScope.launch {
      val reboundBindings = when (request) {
        is CodexTerminalTitleRebindRequest.Pending -> rebindPendingTabs(
          AgentSessionProvider.CODEX,
          mapOf(projectPath to listOf(request.request)),
        ).reboundBindings

        is CodexTerminalTitleRebindRequest.Concrete -> rebindConcreteTabs(
          AgentSessionProvider.CODEX,
          mapOf(projectPath to listOf(request.request)),
        ).reboundBindings
      }
      if (reboundBindings > 0) {
        tabSnapshotWriter.upsert(file.toSnapshot())
      }
      notifyRefresh(AgentSessionProvider.CODEX, projectPath, threadId, null)
      LOG.debug {
        "Codex terminal title rebind requested for path=$projectPath threadId=$threadId " +
        "reboundBindings=$reboundBindings"
      }
    }
    rebindJob = job
    job.invokeOnCompletion {
      synchronized(this) {
        if (rebindJob === job) {
          rebindJob = null
        }
      }
    }
    return true
  }

  private fun buildRebindRequest(projectPath: String, threadId: String): CodexTerminalTitleRebindRequest? {
    val target = AgentChatTabRebindTarget(
      projectPath = projectPath,
      provider = AgentSessionProvider.CODEX,
      threadIdentity = buildAgentThreadIdentity(AgentSessionProvider.CODEX.value, threadId),
      threadId = threadId,
      threadTitle = file.threadTitle,
      threadActivity = file.threadActivity,
    )
    if (file.isPendingThread) {
      return CodexTerminalTitleRebindRequest.Pending(
        AgentChatPendingTabRebindRequest(
          pendingTabKey = file.tabKey,
          pendingThreadIdentity = file.threadIdentity,
          target = target,
        )
      )
    }

    val newThreadRebindRequestedAtMs = file.newThreadRebindRequestedAtMs ?: return null
    if (!AgentSessionThreadRebindPolicy.isConcreteCodexNewThreadRebindAnchorActive(newThreadRebindRequestedAtMs, currentTimeProvider())) {
      return null
    }
    if (threadId == file.threadId || threadId == file.sessionId) {
      return null
    }
    return CodexTerminalTitleRebindRequest.Concrete(
      AgentChatConcreteTabRebindRequest(
        tabKey = file.tabKey,
        currentThreadIdentity = file.threadIdentity,
        newThreadRebindRequestedAtMs = newThreadRebindRequestedAtMs,
        target = target,
      )
    )
  }

  override fun dispose() {
    rebindJob?.cancel()
    rebindJob = null
    listenerDisposable?.let(Disposer::dispose)
    listenerDisposable = null
  }
}

private sealed interface CodexTerminalTitleRebindRequest {
  data class Pending(val request: AgentChatPendingTabRebindRequest) : CodexTerminalTitleRebindRequest

  data class Concrete(val request: AgentChatConcreteTabRebindRequest) : CodexTerminalTitleRebindRequest
}

internal fun extractCodexThreadIdFromTerminalTitle(applicationTitle: String?): String? {
  val normalizedTitle = applicationTitle?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return CODEX_THREAD_ID_IN_TERMINAL_TITLE_REGEX.find(normalizedTitle)?.value?.lowercase(Locale.ROOT)
}

private fun AgentChatVirtualFile.isCodexTopLevelThread(): Boolean {
  return provider == AgentSessionProvider.CODEX && subAgentId == null
}

private val CODEX_THREAD_ID_IN_TERMINAL_TITLE_REGEX = Regex(
  "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"
)
