// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.buildAgentThreadIdentity
import com.intellij.agent.workbench.common.extensions.SnapshotExtensionPointCache
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.TerminalTitleListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<AgentChatTerminalTitleThreadRebindController>()

@ApiStatus.Internal
interface AgentChatTerminalTitleThreadRebindContributor {
  val provider: AgentSessionProvider

  fun extractThreadId(applicationTitle: String?): String?
}

private class AgentChatTerminalTitleThreadRebindContributorRegistryLog

private val CONTRIBUTOR_LOG = logger<AgentChatTerminalTitleThreadRebindContributorRegistryLog>()
private val AGENT_CHAT_TERMINAL_TITLE_THREAD_REBIND_CONTRIBUTOR_EP: ExtensionPointName<AgentChatTerminalTitleThreadRebindContributor> =
  ExtensionPointName("com.intellij.agent.workbench.chatTerminalTitleThreadRebindContributor")

private data class AgentChatTerminalTitleThreadRebindContributorSnapshot(
  @JvmField val contributorsByProvider: Map<AgentSessionProvider, AgentChatTerminalTitleThreadRebindContributor>,
) {
  companion object {
    val EMPTY = AgentChatTerminalTitleThreadRebindContributorSnapshot(
      contributorsByProvider = emptyMap(),
    )
  }
}

private val CONTRIBUTOR_SNAPSHOT_CACHE = SnapshotExtensionPointCache(
  log = CONTRIBUTOR_LOG,
  extensionPoint = AGENT_CHAT_TERMINAL_TITLE_THREAD_REBIND_CONTRIBUTOR_EP,
  cacheId = AgentChatTerminalTitleThreadRebindContributorSnapshot::class.java,
  emptySnapshot = AgentChatTerminalTitleThreadRebindContributorSnapshot.EMPTY,
  unavailableMessage = "Agent Chat terminal title thread rebind contributor EP is unavailable in this context",
  buildSnapshot = ::buildAgentChatTerminalTitleThreadRebindContributorSnapshot,
)

@ApiStatus.Internal
interface AgentChatTerminalTitleThreadRebindContributorRegistry {
  fun find(provider: AgentSessionProvider): AgentChatTerminalTitleThreadRebindContributor?
}

private fun buildAgentChatTerminalTitleThreadRebindContributorSnapshot(
  contributors: Iterable<AgentChatTerminalTitleThreadRebindContributor>,
): AgentChatTerminalTitleThreadRebindContributorSnapshot {
  val contributorsByProvider = LinkedHashMap<AgentSessionProvider, AgentChatTerminalTitleThreadRebindContributor>()
  for (contributor in contributors) {
    val previous = contributorsByProvider.putIfAbsent(contributor.provider, contributor)
    if (previous != null && previous !== contributor) {
      CONTRIBUTOR_LOG.warn(
        "Duplicate Agent Chat terminal title thread rebind contributor for ${contributor.provider.value}: " +
        "keeping ${previous::class.java.name}, ignoring ${contributor::class.java.name}",
      )
    }
  }
  return AgentChatTerminalTitleThreadRebindContributorSnapshot(
    contributorsByProvider = contributorsByProvider,
  )
}

private class EpBackedAgentChatTerminalTitleThreadRebindContributorRegistry : AgentChatTerminalTitleThreadRebindContributorRegistry {
  override fun find(provider: AgentSessionProvider): AgentChatTerminalTitleThreadRebindContributor? {
    return snapshotOrEmpty().contributorsByProvider[provider]
  }
}

private fun snapshotOrEmpty(): AgentChatTerminalTitleThreadRebindContributorSnapshot {
  return CONTRIBUTOR_SNAPSHOT_CACHE.getSnapshotOrEmpty()
}

@ApiStatus.Internal
object AgentChatTerminalTitleThreadRebindContributors {
  private val epRegistry: AgentChatTerminalTitleThreadRebindContributorRegistry =
    EpBackedAgentChatTerminalTitleThreadRebindContributorRegistry()

  fun find(provider: AgentSessionProvider): AgentChatTerminalTitleThreadRebindContributor? {
    return epRegistry.find(provider)
  }
}

internal fun createAgentChatTerminalTitleThreadRebindController(
  file: AgentChatVirtualFile,
  tab: AgentChatTerminalTab,
  tabSnapshotWriter: AgentChatTabSnapshotWriter,
): AgentChatDisposableController? {
  val provider = file.provider ?: return null
  if (file.subAgentId != null) {
    return null
  }
  val terminalTitle = tab.terminalView?.title ?: return null
  val contributor = AgentChatTerminalTitleThreadRebindContributors.find(provider) ?: return null
  return AgentChatTerminalTitleThreadRebindController(
    file = file,
    contributor = contributor,
    tabSnapshotWriter = tabSnapshotWriter,
  ).also { controller ->
    controller.attach(terminalTitle = terminalTitle, parentScope = tab.coroutineScope)
  }
}

internal class AgentChatTerminalTitleThreadRebindController(
  private val file: AgentChatVirtualFile,
  private val contributor: AgentChatTerminalTitleThreadRebindContributor,
  private val tabSnapshotWriter: AgentChatTabSnapshotWriter,
  private val rebindPendingTabs: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = ::rebindOpenPendingAgentChatTabs,
  private val rebindConcreteTabs: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatConcreteTabRebindRequest>>,
  ) -> AgentChatConcreteTabRebindReport = ::rebindOpenConcreteAgentChatTabs,
  private val notifyRefresh: (AgentSessionProvider, String, String?, AgentThreadActivityReport?) -> Unit = ::notifyAgentChatScopedRefresh,
  private val currentTimeProvider: () -> Long = System::currentTimeMillis,
) : AgentChatDisposableController {
  private var listenerDisposable: Disposable? = null
  private var rebindJob: Job? = null
  private var reboundThreadId: String? = null

  fun attach(terminalTitle: TerminalTitle, parentScope: CoroutineScope) {
    if (listenerDisposable != null) {
      return
    }

    val disposable = Disposer.newDisposable("Agent Chat terminal title thread rebind")
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
    val provider = file.provider ?: return false
    if (provider != contributor.provider || file.subAgentId != null || file.projectPath.isBlank()) {
      return false
    }
    val threadId = contributor.extractThreadId(applicationTitle) ?: return false
    if (reboundThreadId == threadId || rebindJob?.isActive == true) {
      return false
    }

    val projectPath = file.projectPath
    val request = buildRebindRequest(provider = provider, projectPath = projectPath, threadId = threadId) ?: return false

    val job = parentScope.launch {
      val rebindResult = when (request) {
        is AgentChatTerminalTitleRebindRequest.Pending -> rebindPendingTabs(
          provider,
          mapOf(projectPath to listOf(request.request)),
        ).toTerminalTitleRebindResult()

        is AgentChatTerminalTitleRebindRequest.Concrete -> rebindConcreteTabs(
          provider,
          mapOf(projectPath to listOf(request.request)),
        ).toTerminalTitleRebindResult()
      }
      if (rebindResult.reboundBindings > 0) {
        tabSnapshotWriter.upsert(file.toSnapshot())
        synchronized(this@AgentChatTerminalTitleThreadRebindController) {
          reboundThreadId = threadId
        }
      }
      notifyRefresh(provider, projectPath, threadId, null)
      LOG.debug {
        "Agent Chat terminal title rebind requested for provider=${provider.value} path=$projectPath threadId=$threadId " +
        "requestedBindings=${rebindResult.requestedBindings}, reboundBindings=${rebindResult.reboundBindings}, " +
        "outcomes=${rebindResult.outcomesByPath}"
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

  private fun buildRebindRequest(
    provider: AgentSessionProvider,
    projectPath: String,
    threadId: String,
  ): AgentChatTerminalTitleRebindRequest? {
    val target = AgentChatTabRebindTarget(
      projectPath = projectPath,
      provider = provider,
      threadIdentity = buildAgentThreadIdentity(provider.value, threadId),
      threadId = threadId,
      threadTitle = file.threadTitle,
      threadActivity = file.threadActivity,
    )
    if (file.isPendingThread) {
      return AgentChatTerminalTitleRebindRequest.Pending(
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
    return AgentChatTerminalTitleRebindRequest.Concrete(
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

private sealed interface AgentChatTerminalTitleRebindRequest {
  data class Pending(val request: AgentChatPendingTabRebindRequest) : AgentChatTerminalTitleRebindRequest

  data class Concrete(val request: AgentChatConcreteTabRebindRequest) : AgentChatTerminalTitleRebindRequest
}

private data class AgentChatTerminalTitleRebindResult(
  val requestedBindings: Int,
  val reboundBindings: Int,
  val outcomesByPath: Map<String, List<String>>,
)

private fun AgentChatPendingTabRebindReport.toTerminalTitleRebindResult(): AgentChatTerminalTitleRebindResult {
  return AgentChatTerminalTitleRebindResult(
    requestedBindings = requestedBindings,
    reboundBindings = reboundBindings,
    outcomesByPath = outcomesByPath.mapValues { (_, outcomes) -> outcomes.map { it.status.name } },
  )
}

private fun AgentChatConcreteTabRebindReport.toTerminalTitleRebindResult(): AgentChatTerminalTitleRebindResult {
  return AgentChatTerminalTitleRebindResult(
    requestedBindings = requestedBindings,
    reboundBindings = reboundBindings,
    outcomesByPath = outcomesByPath.mapValues { (_, outcomes) -> outcomes.map { it.status.name } },
  )
}
