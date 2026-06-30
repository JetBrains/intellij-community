// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.buildAgentThreadIdentity
import com.intellij.platform.ai.agent.core.extensions.SnapshotExtensionPointCache
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.util.Disposer
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.TerminalTitleListener
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.util.xmlb.annotations.Attribute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<AgentThreadViewTerminalTitleThreadRebindController>()

@ApiStatus.Internal
data class AgentThreadViewTerminalTitleThreadRebindSignal(
  @JvmField val threadId: String,
  @JvmField val threadTitle: String? = null,
)

@ApiStatus.Internal
interface AgentThreadViewTerminalTitleThreadRebindContributor {
  fun extractThreadId(applicationTitle: String?): String?

  fun extractThreadSignal(applicationTitle: String?): AgentThreadViewTerminalTitleThreadRebindSignal? {
    return extractThreadId(applicationTitle)?.let { threadId -> AgentThreadViewTerminalTitleThreadRebindSignal(threadId = threadId) }
  }
}

private class AgentThreadViewTerminalTitleThreadRebindContributorRegistryLog

private val CONTRIBUTOR_LOG = logger<AgentThreadViewTerminalTitleThreadRebindContributorRegistryLog>()
private val AGENT_THREAD_VIEW_TERMINAL_TITLE_THREAD_REBIND_CONTRIBUTOR_EP: ExtensionPointName<AgentThreadViewTerminalTitleThreadRebindContributorBean> =
  ExtensionPointName("com.intellij.agent.workbench.agentThreadViewTerminalTitleThreadRebindContributor")

class AgentThreadViewTerminalTitleThreadRebindContributorBean : BaseKeyedLazyInstance<AgentThreadViewTerminalTitleThreadRebindContributor>() {
  @Attribute("providerId")
  @JvmField
  @RequiredElement
  var providerId: String = ""

  @Attribute("implementation")
  @JvmField
  @RequiredElement
  var implementation: String = ""

  override fun getImplementationClassName(): String = implementation

  fun providerOrNull(): AgentSessionProvider? {
    val provider = AgentSessionProvider.fromOrNull(providerId)
    if (provider == null) {
      CONTRIBUTOR_LOG.warn("Ignoring Agent Thread View terminal title thread rebind contributor with invalid providerId '$providerId': $implementation")
    }
    return provider
  }
}

private data class AgentThreadViewTerminalTitleThreadRebindContributorSnapshot(
  @JvmField val contributorsByProvider: Map<AgentSessionProvider, AgentThreadViewTerminalTitleThreadRebindContributor>,
) {
  companion object {
    val EMPTY = AgentThreadViewTerminalTitleThreadRebindContributorSnapshot(
      contributorsByProvider = emptyMap(),
    )
  }
}

private val CONTRIBUTOR_SNAPSHOT_CACHE = SnapshotExtensionPointCache(
  log = CONTRIBUTOR_LOG,
  extensionPoint = AGENT_THREAD_VIEW_TERMINAL_TITLE_THREAD_REBIND_CONTRIBUTOR_EP,
  cacheId = AgentThreadViewTerminalTitleThreadRebindContributorSnapshot::class.java,
  emptySnapshot = AgentThreadViewTerminalTitleThreadRebindContributorSnapshot.EMPTY,
  unavailableMessage = "Agent Thread View terminal title thread rebind contributor EP is unavailable in this context",
  buildSnapshot = ::buildAgentThreadViewTerminalTitleThreadRebindContributorSnapshot,
)

@ApiStatus.Internal
interface AgentThreadViewTerminalTitleThreadRebindContributorRegistry {
  fun find(provider: AgentSessionProvider): AgentThreadViewTerminalTitleThreadRebindContributor?
}

private fun buildAgentThreadViewTerminalTitleThreadRebindContributorSnapshot(
  contributorBeans: Iterable<AgentThreadViewTerminalTitleThreadRebindContributorBean>,
): AgentThreadViewTerminalTitleThreadRebindContributorSnapshot {
  val contributorsByProvider = LinkedHashMap<AgentSessionProvider, AgentThreadViewTerminalTitleThreadRebindContributor>()
  for (contributorBean in contributorBeans) {
    val provider = contributorBean.providerOrNull() ?: continue
    val contributor = contributorBean.instance
    val previous = contributorsByProvider.putIfAbsent(provider, contributor)
    if (previous != null && previous !== contributor) {
      CONTRIBUTOR_LOG.warn(
        "Duplicate Agent Thread View terminal title thread rebind contributor for ${provider.value}: " +
        "keeping ${previous::class.java.name}, ignoring ${contributor::class.java.name}",
      )
    }
  }
  return AgentThreadViewTerminalTitleThreadRebindContributorSnapshot(
    contributorsByProvider = contributorsByProvider,
  )
}

private class EpBackedAgentThreadViewTerminalTitleThreadRebindContributorRegistry : AgentThreadViewTerminalTitleThreadRebindContributorRegistry {
  override fun find(provider: AgentSessionProvider): AgentThreadViewTerminalTitleThreadRebindContributor? {
    return snapshotOrEmpty().contributorsByProvider[provider]
  }
}

private fun snapshotOrEmpty(): AgentThreadViewTerminalTitleThreadRebindContributorSnapshot {
  return CONTRIBUTOR_SNAPSHOT_CACHE.getSnapshotOrEmpty()
}

@ApiStatus.Internal
object AgentThreadViewTerminalTitleThreadRebindContributors {
  private val epRegistry: AgentThreadViewTerminalTitleThreadRebindContributorRegistry =
    EpBackedAgentThreadViewTerminalTitleThreadRebindContributorRegistry()

  fun find(provider: AgentSessionProvider): AgentThreadViewTerminalTitleThreadRebindContributor? {
    return epRegistry.find(provider)
  }
}

internal fun createAgentThreadViewTerminalTitleThreadRebindController(
  file: AgentThreadViewVirtualFile,
  tab: AgentThreadViewTerminalTab,
  tabSnapshotWriter: AgentThreadViewTabSnapshotWriter,
): AgentThreadViewDisposableController? {
  val provider = file.provider ?: return null
  if (file.subAgentId != null) {
    return null
  }
  val terminalTitle = tab.terminalView?.title ?: return null
  val contributor = AgentThreadViewTerminalTitleThreadRebindContributors.find(provider) ?: return null
  return AgentThreadViewTerminalTitleThreadRebindController(
    file = file,
    contributor = contributor,
    tabSnapshotWriter = tabSnapshotWriter,
  ).also { controller ->
    controller.attach(terminalTitle = terminalTitle, parentScope = tab.coroutineScope)
  }
}

internal suspend fun AgentThreadViewTerminalTab.awaitAgentThreadViewTerminalTitleThreadId(
  provider: AgentSessionProvider?,
  expectedThreadId: String,
  timeoutMs: Long,
): AgentThreadViewTerminalInputReadiness {
  val normalizedExpectedThreadId = expectedThreadId.trim().takeIf { it.isNotEmpty() }
                                 ?: return AgentThreadViewTerminalInputReadiness.READY
  val actualProvider = provider ?: return AgentThreadViewTerminalInputReadiness.READY
  val terminalTitle = terminalView?.title ?: return AgentThreadViewTerminalInputReadiness.READY
  val contributor = AgentThreadViewTerminalTitleThreadRebindContributors.find(actualProvider)
                    ?: return AgentThreadViewTerminalInputReadiness.READY
  if (contributor.matchesThreadId(terminalTitle.applicationTitle, normalizedExpectedThreadId)) {
    return AgentThreadViewTerminalInputReadiness.READY
  }
  val matched = withTimeoutOrNull(timeoutMs.milliseconds) {
    terminalTitle.awaitThreadId(contributor, normalizedExpectedThreadId)
  } ?: false
  return when {
    matched -> AgentThreadViewTerminalInputReadiness.READY
    sessionState.value == TerminalViewSessionState.Terminated -> AgentThreadViewTerminalInputReadiness.TERMINATED
    else -> AgentThreadViewTerminalInputReadiness.TIMEOUT
  }
}

private suspend fun TerminalTitle.awaitThreadId(
  contributor: AgentThreadViewTerminalTitleThreadRebindContributor,
  expectedThreadId: String,
): Boolean {
  return suspendCancellableCoroutine { continuation ->
    val disposable = Disposer.newDisposable("Agent Thread View terminal title await thread id")

    fun resumeIfMatched(applicationTitle: String?): Boolean {
      if (!contributor.matchesThreadId(applicationTitle, expectedThreadId)) {
        return false
      }
      Disposer.dispose(disposable)
      if (continuation.isActive) {
        continuation.resume(true)
      }
      return true
    }

    addTitleListener(
      object : TerminalTitleListener {
        override fun onTitleChanged(terminalTitle: TerminalTitle) {
          resumeIfMatched(terminalTitle.applicationTitle)
        }
      },
      disposable,
    )
    resumeIfMatched(applicationTitle)
    continuation.invokeOnCancellation {
      Disposer.dispose(disposable)
    }
  }
}

private fun AgentThreadViewTerminalTitleThreadRebindContributor.matchesThreadId(applicationTitle: String?, expectedThreadId: String): Boolean {
  return extractThreadSignal(applicationTitle)?.threadId?.equals(expectedThreadId, ignoreCase = true) == true
}

internal class AgentThreadViewTerminalTitleThreadRebindController(
  private val file: AgentThreadViewVirtualFile,
  private val contributor: AgentThreadViewTerminalTitleThreadRebindContributor,
  private val tabSnapshotWriter: AgentThreadViewTabSnapshotWriter,
  private val rebindPendingTabs: suspend (
    AgentSessionProvider,
    Map<String, List<AgentThreadViewPendingTabRebindRequest>>,
  ) -> AgentThreadViewPendingTabRebindReport = ::rebindOpenPendingAgentThreadViewTabs,
  private val rebindConcreteTabs: suspend (
    AgentSessionProvider,
    Map<String, List<AgentThreadViewConcreteTabRebindRequest>>,
  ) -> AgentThreadViewConcreteTabRebindReport = ::rebindOpenConcreteAgentThreadViewTabs,
  private val notifyRefresh: (AgentSessionProvider, String, String?, String?, AgentThreadActivityReport?) -> Unit =
    { provider, projectPath, threadId, threadTitle, activityReport ->
      notifyAgentThreadViewScopedRefresh(provider, projectPath, threadId, threadTitle, activityReport)
    },
  private val currentTimeProvider: () -> Long = System::currentTimeMillis,
) : AgentThreadViewDisposableController {
  private var listenerDisposable: Disposable? = null
  private var rebindJob: Job? = null
  private var reboundThreadId: String? = null

  fun attach(terminalTitle: TerminalTitle, parentScope: CoroutineScope) {
    if (listenerDisposable != null) {
      return
    }

    val disposable = Disposer.newDisposable("Agent Thread View terminal title thread rebind")
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
    if (file.subAgentId != null || file.projectPath.isBlank()) {
      return false
    }
    val signal = contributor.extractThreadSignal(applicationTitle) ?: return false
    val threadId = signal.threadId
    if (reboundThreadId == threadId || rebindJob?.isActive == true) {
      return false
    }

    val projectPath = file.projectPath
    val request = buildRebindRequest(
      provider = provider,
      projectPath = projectPath,
      threadId = threadId,
      threadTitle = signal.threadTitle,
    ) ?: return false

    val job = parentScope.launch {
      withContext(NonCancellable) {
        val rebindResult = when (request) {
          is AgentThreadViewTerminalTitleRebindRequest.Pending -> rebindPendingTabs(
            provider,
            mapOf(projectPath to listOf(request.request)),
          ).toTerminalTitleRebindResult()

          is AgentThreadViewTerminalTitleRebindRequest.Concrete -> rebindConcreteTabs(
            provider,
            mapOf(projectPath to listOf(request.request)),
          ).toTerminalTitleRebindResult()
        }
        if (rebindResult.reboundBindings > 0) {
          tabSnapshotWriter.upsert(file.toSnapshot())
          synchronized(this@AgentThreadViewTerminalTitleThreadRebindController) {
            reboundThreadId = threadId
          }
        }
        notifyRefresh(provider, projectPath, threadId, signal.threadTitle, null)
        LOG.debug {
          "Agent Thread View terminal title rebind requested for provider=${provider.value} path=$projectPath threadId=$threadId " +
          "requestedBindings=${rebindResult.requestedBindings}, reboundBindings=${rebindResult.reboundBindings}, " +
          "outcomes=${rebindResult.outcomesByPath}"
        }
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
    threadTitle: String?,
  ): AgentThreadViewTerminalTitleRebindRequest? {
    val target = AgentThreadViewTabRebindTarget(
      projectPath = projectPath,
      projectDirectory = file.projectDirectory,
      provider = provider,
      threadIdentity = buildAgentThreadIdentity(provider.value, threadId),
      threadId = threadId,
      threadTitle = threadTitle?.takeIf { it.isNotBlank() } ?: file.threadTitle,
      threadActivity = file.threadActivity,
    )
    if (file.isPendingThread) {
      return AgentThreadViewTerminalTitleRebindRequest.Pending(
        AgentThreadViewPendingTabRebindRequest(
          pendingTabKey = file.tabKey,
          pendingThreadIdentity = file.threadIdentity,
          target = target,
        )
      )
    }

    val newThreadRebindRequestedAtMs = file.newThreadRebindRequestedAtMs ?: return null
    if (!AgentSessionThreadRebindPolicy.isConcreteNewThreadRebindAnchorActive(newThreadRebindRequestedAtMs, currentTimeProvider())) {
      return null
    }
    if (threadId == file.threadId || threadId == file.sessionId) {
      return null
    }
    return AgentThreadViewTerminalTitleRebindRequest.Concrete(
      AgentThreadViewConcreteTabRebindRequest(
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

private sealed interface AgentThreadViewTerminalTitleRebindRequest {
  data class Pending(val request: AgentThreadViewPendingTabRebindRequest) : AgentThreadViewTerminalTitleRebindRequest

  data class Concrete(val request: AgentThreadViewConcreteTabRebindRequest) : AgentThreadViewTerminalTitleRebindRequest
}

private data class AgentThreadViewTerminalTitleRebindResult(
  val requestedBindings: Int,
  val reboundBindings: Int,
  val outcomesByPath: Map<String, List<String>>,
)

private fun AgentThreadViewPendingTabRebindReport.toTerminalTitleRebindResult(): AgentThreadViewTerminalTitleRebindResult {
  return AgentThreadViewTerminalTitleRebindResult(
    requestedBindings = requestedBindings,
    reboundBindings = reboundBindings,
    outcomesByPath = outcomesByPath.mapValues { (_, outcomes) -> outcomes.map { it.status.name } },
  )
}

private fun AgentThreadViewConcreteTabRebindReport.toTerminalTitleRebindResult(): AgentThreadViewTerminalTitleRebindResult {
  return AgentThreadViewTerminalTitleRebindResult(
    requestedBindings = requestedBindings,
    reboundBindings = reboundBindings,
    outcomesByPath = outcomesByPath.mapValues { (_, outcomes) -> outcomes.map { it.status.name } },
  )
}
