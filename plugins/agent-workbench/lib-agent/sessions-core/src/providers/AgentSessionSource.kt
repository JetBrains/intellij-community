// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.providers

// @spec community/plugins/agent-workbench/spec/chat/agent-chat-structure-view.spec.md

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.session.AgentSessionCost
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSessionThreadOutline
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * Lightweight description of a provider thread that can replace a still-pending chat tab.
 *
 * Providers return candidates from [AgentSessionRefreshHints] when a newly opened chat tab does not yet know its concrete provider
 * thread id. The refresh coordinator matches candidates by path, provider, recency, and presentation metadata; candidates do not create
 * or update persisted rows by themselves.
 */
@ApiStatus.Internal
data class AgentSessionRebindCandidate(
  /** Concrete provider thread id that can be rebound to a pending tab. */
  @JvmField val threadId: String,
  /** Provider-normalized title for the candidate row or tab. */
  @JvmField val title: String,
  /** Provider update timestamp in epoch milliseconds. */
  @JvmField val updatedAt: Long,
  /** Best current row activity for the candidate. */
  @JvmField val activity: AgentThreadActivity,
)

/**
 * Activity-only update for a known provider thread.
 *
 * This compact form is used by update events and refresh hints when a provider can report status without reloading the full thread row.
 */
@ApiStatus.Internal
data class AgentSessionThreadActivityUpdate(
  /** Row/chrome activity to apply to the thread. */
  @JvmField val activityReport: AgentThreadActivityReport,
  /** Whether [activityReport] should also replace the chrome/summary activity. */
  @JvmField val updatesChromeActivity: Boolean = true,
  /** Provider update timestamp in epoch milliseconds, or `null` when the update is not timestamped. */
  @JvmField val updatedAt: Long? = null,
)

/**
 * Presentation update for a known provider thread.
 *
 * A presentation update may carry title, activity, timestamp, or any combination of them. Consumers merge these updates with existing
 * rows and ignore stale timestamped updates.
 */
@ApiStatus.Internal
data class AgentSessionThreadPresentationUpdate(
  /** Replacement title, or `null` to keep the current title. */
  @JvmField val title: String? = null,
  /** Replacement activity, or `null` to keep the current activity. */
  @JvmField val activityReport: AgentThreadActivityReport? = null,
  /** Whether [activityReport] should also replace the chrome/summary activity. */
  @JvmField val updatesChromeActivity: Boolean = true,
  /** Provider update timestamp in epoch milliseconds, or `null` when the update is not timestamped. */
  @JvmField val updatedAt: Long? = null,
)

/**
 * Provider-specific hints fetched around a refresh.
 *
 * Hints are intentionally non-authoritative: they may rebind pending tabs and patch presentation for already loaded rows, but they do not
 * add persisted rows or evict missing rows. Providers that can only list threads should skip [AgentSessionRefreshHintsSource].
 */
@ApiStatus.Internal
data class AgentSessionRefreshHints(
  /** Candidate concrete threads for pending editor tabs that do not yet have a provider thread id. */
  @JvmField val rebindCandidates: List<AgentSessionRebindCandidate> = emptyList(),
  /** Activity-only patches keyed by concrete thread id or sub-agent id. */
  @JvmField val activityUpdatesByThreadId: Map<String, AgentSessionThreadActivityUpdate> = emptyMap(),
  /** Title/activity/timestamp patches keyed by concrete thread id or sub-agent id. */
  @JvmField val presentationUpdatesByThreadId: Map<String, AgentSessionThreadPresentationUpdate> = emptyMap(),
)

/**
 * Result of creating a new provider thread from a thread-outline item.
 *
 * [thread] describes the newly created thread. [launchSpecOverride] is used when the new thread must be opened with a provider-specific
 * terminal command that differs from the normal resume command.
 */
@ApiStatus.Internal
data class AgentSessionOutlineForkResult(
  @JvmField val thread: AgentSessionThread,
  @JvmField val launchSpecOverride: AgentSessionTerminalLaunchSpec? = null,
)

/** Timestamp marker used when a refresh seed has no known row timestamp. */
@ApiStatus.Internal
const val UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT: Long = -1L

/**
 * Known thread identity passed to providers when fetching refresh hints.
 *
 * [updatedAt] is the row timestamp currently held by Agent Workbench, or [UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT] when unknown.
 * [forceRefresh] is true when the triggering event explicitly named this thread and stale cached hints should not be reused.
 */
@ApiStatus.Internal
data class AgentSessionRefreshThreadSeed(
  @JvmField val threadId: String,
  @JvmField val updatedAt: Long = UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT,
  @JvmField val forceRefresh: Boolean = false,
)

/** Converts thread ids to refresh seeds with unknown timestamps. */
@ApiStatus.Internal
fun Collection<String>.toAgentSessionRefreshThreadSeeds(): Set<AgentSessionRefreshThreadSeed> {
  return asSequence()
    .map { threadId -> AgentSessionRefreshThreadSeed(threadId = threadId) }
    .toCollection(LinkedHashSet())
}

/** Describes how a provider update event should be consumed by the refresh scheduler. */
@ApiStatus.Internal
enum class AgentSessionSourceUpdate {
  /** Thread rows may have been added, removed, or changed; an authoritative provider refresh may be required. */
  THREADS_CHANGED,
  /** Only auxiliary hints or presentation may have changed; consumers should avoid a full row refresh when possible. */
  HINTS_CHANGED,
}

/**
 * Event emitted by [AgentSessionUpdateSource] or [AgentSessionActiveThreadUpdateSource].
 *
 * Scope fields narrow the affected paths and thread ids. A `null` scope means all loaded paths or all relevant threads may be affected;
 * an empty scope is normalized away by consumers. Project-file evidence lets the scheduler trigger VFS refreshes only when a provider has
 * observed possible changes to project files.
 */
@ApiStatus.Internal
class AgentSessionSourceUpdateEvent private constructor(
  @JvmField val type: AgentSessionSourceUpdate,
  @JvmField val scopedPaths: Set<String>? = null,
  @JvmField val threadIds: Set<String>? = null,
  @JvmField val activityUpdatesByThreadId: Map<String, AgentSessionThreadActivityUpdate> = emptyMap(),
  @JvmField val presentationUpdatesByThreadId: Map<String, AgentSessionThreadPresentationUpdate> = emptyMap(),
  @JvmField val mayHaveChangedProjectFiles: Boolean = false,
  @JvmField val changedProjectFilePaths: Set<String>? = null,
) {
  companion object {
    /**
     * Creates an event indicating that provider thread rows may have changed.
     *
     * Use this for additions, removals, archive transitions, title changes that need authoritative reload, or status changes that cannot
     * be expressed as hints. [scopedPaths] and [threadIds] should be as narrow as the provider can report reliably.
     */
    fun threadsChanged(
      scopedPaths: Set<String>? = null,
      threadIds: Set<String>? = null,
      activityUpdatesByThreadId: Map<String, AgentSessionThreadActivityUpdate> = emptyMap(),
      presentationUpdatesByThreadId: Map<String, AgentSessionThreadPresentationUpdate> = emptyMap(),
      mayHaveChangedProjectFiles: Boolean = false,
      changedProjectFilePaths: Set<String>? = null,
    ): AgentSessionSourceUpdateEvent {
      return AgentSessionSourceUpdateEvent(
        type = AgentSessionSourceUpdate.THREADS_CHANGED,
        scopedPaths = scopedPaths,
        threadIds = threadIds,
        activityUpdatesByThreadId = activityUpdatesByThreadId,
        presentationUpdatesByThreadId = presentationUpdatesByThreadId,
        mayHaveChangedProjectFiles = mayHaveChangedProjectFiles,
        changedProjectFilePaths = changedProjectFilePaths,
      )
    }

    /**
     * Creates an event indicating that provider hints or presentation changed without requiring a full row snapshot.
     *
     * Consumers may still load a missing thread snapshot when [threadIds] names a row that is not currently present.
     */
    fun hintsChanged(
      scopedPaths: Set<String>? = null,
      threadIds: Set<String>? = null,
      activityUpdatesByThreadId: Map<String, AgentSessionThreadActivityUpdate> = emptyMap(),
      presentationUpdatesByThreadId: Map<String, AgentSessionThreadPresentationUpdate> = emptyMap(),
      mayHaveChangedProjectFiles: Boolean = false,
      changedProjectFilePaths: Set<String>? = null,
    ): AgentSessionSourceUpdateEvent {
      return AgentSessionSourceUpdateEvent(
        type = AgentSessionSourceUpdate.HINTS_CHANGED,
        scopedPaths = scopedPaths,
        threadIds = threadIds,
        activityUpdatesByThreadId = activityUpdatesByThreadId,
        presentationUpdatesByThreadId = presentationUpdatesByThreadId,
        mayHaveChangedProjectFiles = mayHaveChangedProjectFiles,
        changedProjectFilePaths = changedProjectFilePaths,
      )
    }

    /** Creates a hint event carrying activity updates for known thread ids. */
    fun activityChanged(
      scopedPaths: Set<String>? = null,
      threadIds: Set<String>? = null,
      activityUpdatesByThreadId: Map<String, AgentSessionThreadActivityUpdate>,
    ): AgentSessionSourceUpdateEvent {
      return hintsChanged(
        scopedPaths = scopedPaths,
        threadIds = threadIds,
        activityUpdatesByThreadId = activityUpdatesByThreadId,
      )
    }

    /** Creates a hint event carrying title/activity/timestamp presentation updates for known thread ids. */
    fun presentationChanged(
      scopedPaths: Set<String>? = null,
      threadIds: Set<String>? = null,
      presentationUpdatesByThreadId: Map<String, AgentSessionThreadPresentationUpdate>,
    ): AgentSessionSourceUpdateEvent {
      return hintsChanged(
        scopedPaths = scopedPaths,
        threadIds = threadIds,
        presentationUpdatesByThreadId = presentationUpdatesByThreadId,
      )
    }

    /** Creates a row-discovery event, optionally scoped to paths and project-file evidence. */
    fun discoveryChanged(
      scopedPaths: Set<String>? = null,
      mayHaveChangedProjectFiles: Boolean = false,
      changedProjectFilePaths: Set<String>? = null,
    ): AgentSessionSourceUpdateEvent {
      return threadsChanged(
        scopedPaths = scopedPaths,
        mayHaveChangedProjectFiles = mayHaveChangedProjectFiles,
        changedProjectFilePaths = changedProjectFilePaths,
      )
    }

    /**
     * Creates a discovery event that also reports possible project-file changes.
     *
     * [changedProjectFilePaths] should contain absolute project-file paths when the provider can report exact files; `null` means the
     * provider only knows that some project file under the scoped paths may have changed.
     */
    fun projectFilesChanged(
      scopedPaths: Set<String>? = null,
      changedProjectFilePaths: Set<String>? = null,
    ): AgentSessionSourceUpdateEvent {
      return discoveryChanged(
        scopedPaths = scopedPaths,
        mayHaveChangedProjectFiles = true,
        changedProjectFilePaths = changedProjectFilePaths,
      )
    }

  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AgentSessionSourceUpdateEvent) return false

    return type == other.type &&
           scopedPaths == other.scopedPaths &&
           threadIds == other.threadIds &&
           activityUpdatesByThreadId == other.activityUpdatesByThreadId &&
           presentationUpdatesByThreadId == other.presentationUpdatesByThreadId &&
           mayHaveChangedProjectFiles == other.mayHaveChangedProjectFiles &&
           changedProjectFilePaths == other.changedProjectFilePaths
  }

  override fun hashCode(): Int {
    var result = type.hashCode()
    result = 31 * result + scopedPaths.hashCode()
    result = 31 * result + threadIds.hashCode()
    result = 31 * result + activityUpdatesByThreadId.hashCode()
    result = 31 * result + presentationUpdatesByThreadId.hashCode()
    result = 31 * result + mayHaveChangedProjectFiles.hashCode()
    result = 31 * result + changedProjectFilePaths.hashCode()
    return result
  }

  override fun toString(): String {
    return "AgentSessionSourceUpdateEvent(" +
           "type=$type, " +
           "scopedPaths=$scopedPaths, " +
           "threadIds=$threadIds, " +
           "activityUpdatesByThreadId=$activityUpdatesByThreadId, " +
           "presentationUpdatesByThreadId=$presentationUpdatesByThreadId, " +
           "mayHaveChangedProjectFiles=$mayHaveChangedProjectFiles, " +
           "changedProjectFilePaths=$changedProjectFilePaths)"
  }
}

/** Converts an activity-only update to a presentation update. */
@ApiStatus.Internal
fun AgentSessionThreadActivityUpdate.toPresentationUpdate(): AgentSessionThreadPresentationUpdate {
  return AgentSessionThreadPresentationUpdate(
    activityReport = activityReport,
    updatesChromeActivity = updatesChromeActivity,
    updatedAt = updatedAt,
  )
}

/**
 * Merges two presentation updates for the same thread id.
 *
 * Stale timestamped updates are ignored. Non-null incoming fields replace existing fields, while chrome-activity behavior is merged so a
 * later row-only update does not accidentally erase a chrome activity supplied by an earlier update.
 */
@ApiStatus.Internal
fun mergeAgentSessionThreadPresentationUpdates(
  existing: AgentSessionThreadPresentationUpdate,
  incoming: AgentSessionThreadPresentationUpdate,
): AgentSessionThreadPresentationUpdate {
  val existingUpdatedAt = existing.updatedAt
  val incomingUpdatedAt = incoming.updatedAt
  if (existingUpdatedAt != null && incomingUpdatedAt != null && incomingUpdatedAt < existingUpdatedAt) {
    return existing
  }
  val updatedAt = when {
    existingUpdatedAt == null -> incomingUpdatedAt
    incomingUpdatedAt == null -> existingUpdatedAt
    else -> maxOf(existingUpdatedAt, incomingUpdatedAt)
  }
  val updatesChromeActivity = incoming.updatesChromeActivity || existing.updatesChromeActivity
  val activityReport = when {
    incoming.activityReport == null -> existing.activityReport
    existing.activityReport == null -> incoming.activityReport
    else -> incoming.activityReport.copy(
      chromeActivity = if (incoming.updatesChromeActivity) incoming.activityReport.chromeActivity else existing.activityReport.chromeActivity,
    )
  }
  return AgentSessionThreadPresentationUpdate(
    title = incoming.title ?: existing.title,
    activityReport = activityReport,
    updatesChromeActivity = updatesChromeActivity,
    updatedAt = updatedAt,
  )
}

/** Request passed to [AgentSessionRefreshSource] for provider-scoped refreshes. */
@ApiStatus.Internal
data class AgentSessionSourceRefreshRequest(
  /** Normalized Agent Workbench project/worktree paths that need provider rows. */
  @JvmField val paths: List<String>,
  /** Concrete thread ids named by the triggering update, or empty for a path-level refresh. */
  @JvmField val threadIds: Set<String> = emptySet(),
  /** Source update that triggered this refresh. */
  @JvmField val updateEvent: AgentSessionSourceUpdateEvent,
) {
  /** True when [threadIds] requests a partial thread-level refresh. */
  val isThreadScoped: Boolean
    get() = threadIds.isNotEmpty()
}

/**
 * Result returned by [AgentSessionRefreshSource].
 *
 * [completeThreadsByPath] is authoritative for a path and replaces all active rows for the provider there. [partialThreadsByPath] updates
 * only returned thread ids and must not evict other rows. [removedThreadIdsByPath] removes rows during a partial refresh. [failuresByPath]
 * reports path-local failures while allowing other paths in the same request to succeed.
 */
@ApiStatus.Internal
data class AgentSessionSourceRefreshResult(
  @JvmField val completeThreadsByPath: Map<String, List<AgentSessionThread>> = emptyMap(),
  @JvmField val partialThreadsByPath: Map<String, List<AgentSessionThread>> = emptyMap(),
  @JvmField val removedThreadIdsByPath: Map<String, Set<String>> = emptyMap(),
  @JvmField val failuresByPath: Map<String, Throwable> = emptyMap(),
)

/** Returns true when an update event is not scoped to paths or thread ids. */
@ApiStatus.Internal
fun AgentSessionSourceUpdateEvent.isUnscoped(): Boolean {
  return scopedPaths == null && threadIds == null
}

/** Formats update scope for debug logging without exposing concrete paths or thread ids. */
@ApiStatus.Internal
fun AgentSessionSourceUpdateEvent.describeScope(): String {
  val scopedPaths = scopedPaths
  val threadIds = threadIds
  val scope = when {
    scopedPaths == null && threadIds == null -> "scope=all"
    scopedPaths != null && threadIds != null -> "scope=paths:${scopedPaths.size},threadIds:${threadIds.size}"
    scopedPaths != null -> "scope=paths:${scopedPaths.size}"
    else -> "scope=threadIds:${threadIds?.size ?: 0}"
  }
  return changedProjectFilePaths?.let { paths -> "$scope,changedProjectFiles:${paths.size}" } ?: scope
}

/**
 * Required provider source contract for Agent Workbench session discovery.
 *
 * Provider implementations normally register one provider descriptor and expose one stable source from it. This source owns active thread
 * listing and provider-local session discovery state. Optional behavior belongs in the focused capability interfaces below, implemented
 * on the same source when supported; do not add default no-op methods here.
 *
 * The path accepted by methods in this file is the normalized Agent Workbench project or worktree path. [Project] is supplied only when the
 * path belongs to a currently open IDE project.
 */
@ApiStatus.Internal
interface AgentSessionSource {
  /** Stable provider id for every thread returned by this source. */
  val provider: AgentSessionProvider

  /**
   * Whether normal active-thread loading can report the exact total number of available rows for this provider.
   *
   * Return `false` when discovery is intentionally partial or backend-limited. The tool window then treats visible counts as unknown
   * instead of implying that no more provider rows exist.
   */
  val canReportExactThreadCount: Boolean
    get() = true

  /**
   * Loads active, non-archived threads for [path].
   *
   * Return rows with [AgentSessionThread.provider] equal to [provider], stable concrete ids, provider-normalized titles, and epoch-millis
   * `updatedAt` values. [openProject] is non-null only when the path belongs to a currently open IDE project; implementations may ignore
   * it when provider storage is project-path based. Do not return archived rows here; implement [AgentSessionArchivedSource] instead.
   */
  suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread>
}

/** Capability for providers that can batch-load active threads for several paths. */
@ApiStatus.Internal
interface AgentSessionPrefetchSource : AgentSessionSource {
  /**
   * Prefetches active threads for [paths] in one backend call.
   *
   * Return only paths that were actually loaded. Missing paths fall back to [AgentSessionSource.listThreads]. Returned lists are complete
   * active snapshots for their path and must follow the same row contract as [AgentSessionSource.listThreads].
   */
  suspend fun prefetchThreads(paths: List<String>): Map<String, List<AgentSessionThread>>
}

/** Capability for providers that expose archived threads separately from active threads. */
@ApiStatus.Internal
interface AgentSessionArchivedSource : AgentSessionSource {
  /**
   * Loads archived threads for [path].
   *
   * Return only archived rows. [openProject] has the same meaning as in [AgentSessionSource.listThreads]. Providers that cannot list
   * archived rows cheaply or accurately should not implement this capability.
   */
  suspend fun listArchivedThreads(path: String, openProject: Project?): List<AgentSessionThread>
}

/** Capability for providers that emit background updates for loaded session rows or hints. */
@ApiStatus.Internal
interface AgentSessionUpdateSource : AgentSessionSource {
  /**
   * Stream of provider update events.
   *
   * Implementations should emit only meaningful changes after filtering raw filesystem/backend noise. Events may be scoped to paths,
   * thread ids, or both; precise scopes reduce refresh work and make active chat tabs update faster.
   */
  val updateEvents: Flow<AgentSessionSourceUpdateEvent>
}

/** Capability for providers that can watch one active concrete thread with provider-specific filtering. */
@ApiStatus.Internal
interface AgentSessionActiveThreadUpdateSource : AgentSessionSource {
  /**
   * Returns update events for an actively running [threadId] under [path].
   *
   * This stream is used by active chat tabs and outlines. Implementations should parse raw notifications and emit only meaningful source
   * updates such as activity changes or project-file evidence; unchanged persistence writes should not become refresh signals.
   */
  fun activeThreadUpdateEvents(path: String, threadId: String): Flow<AgentSessionSourceUpdateEvent>
}

/** Capability for providers that can refresh rows more precisely than path-level listing. */
@ApiStatus.Internal
interface AgentSessionRefreshSource : AgentSessionSource {
  /**
   * Refreshes provider rows for [AgentSessionSourceRefreshRequest.paths].
   *
   * Return complete path snapshots when the provider performed an authoritative path listing. Return partial rows and removals for
   * thread-scoped updates. Report path-local failures in [AgentSessionSourceRefreshResult.failuresByPath] instead of failing the whole
   * request when other paths can still succeed.
   */
  suspend fun refreshThreads(request: AgentSessionSourceRefreshRequest): AgentSessionSourceRefreshResult
}

/** Capability for providers that can cheaply fetch non-authoritative refresh hints. */
@ApiStatus.Internal
interface AgentSessionRefreshHintsSource : AgentSessionSource {
  /**
   * Loads provider-specific hints for [paths].
   *
   * [refreshThreadSeedsByPath] contains loaded or otherwise interesting thread ids with their current timestamps. Returned hints must not
   * add or remove persisted rows directly; they are consumed for pending-tab rebinding and presentation/status projection.
   */
  suspend fun prefetchRefreshHints(
    paths: List<String>,
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  ): Map<String, AgentSessionRefreshHints>
}

/** Capability for providers that hydrate cost after visible rows are known. */
@ApiStatus.Internal
interface AgentSessionCostSource : AgentSessionSource {
  /**
   * Loads cost for the requested [threads] under [path].
   *
   * Implementations should key the result by concrete thread id and avoid recomputing work already represented by an unchanged
   * `updatedAt`. A missing map entry or `null` value means the cost is unavailable for that thread.
   */
  suspend fun loadThreadCosts(
    path: String,
    threads: List<AgentSessionThread>,
  ): Map<String, AgentSessionCost?>
}

/** Capability for providers that expose a read-only outline of persisted thread history. */
@ApiStatus.Internal
interface AgentSessionThreadOutlineSource : AgentSessionSource {
  /**
   * Loads a read-only, role-aware outline for a persisted thread.
   *
   * Implementations should read provider history without restoring terminals, driving TUIs, or mutating provider state. Return `null`
   * when an outline is unavailable. Return an [AgentSessionThreadOutline] with an empty item list when the provider supports outlines but
   * has no visible entries. Item ids must be stable provider anchors when navigation or fork capabilities use them.
   */
  suspend fun loadThreadOutline(
    path: String,
    threadId: String,
    subAgentId: String? = null,
  ): AgentSessionThreadOutline?
}

/** Capability for providers that can navigate a live provider view to an outline item. */
@ApiStatus.Internal
interface AgentSessionThreadOutlineNavigationSource : AgentSessionThreadOutlineSource {
  /**
   * Returns whether [itemId] has a stable live provider anchor that can be navigated to in the current thread context.
   *
   * This is not a fallback hook for launching a provider, scraping terminal output, or replaying a TUI selection. Providers that only
   * display persisted history should not implement this capability.
   */
  fun canNavigateThreadOutlineItem(
    path: String,
    threadId: String,
    itemId: String,
    subAgentId: String? = null,
    tabKey: String? = null,
  ): Boolean

  /** Navigates a live provider view to [itemId] when [canNavigateThreadOutlineItem] reports support. */
  suspend fun navigateThreadOutlineItem(
    path: String,
    threadId: String,
    itemId: String,
    subAgentId: String? = null,
    tabKey: String? = null,
  ): Boolean
}

/** Capability for providers that can create a new thread from an outline item. */
@ApiStatus.Internal
interface AgentSessionThreadOutlineForkSource : AgentSessionThreadOutlineSource {
  /**
   * Runtime visibility and executable gate for forking from [itemId].
   *
   * Return `true` only when the current provider state can create the fork now. UI surfaces use this single gate for both visibility and
   * enablement, so do not return `true` for conceptual support that may still fail because a live bridge is disconnected.
   */
  fun canForkThreadFromOutlineItem(
    path: String,
    threadId: String,
    itemId: String,
    subAgentId: String? = null,
    tabKey: String? = null,
  ): Boolean

  /**
   * Creates a provider-specific fork from [itemId].
   *
   * Implementations must not mutate the source thread. The returned [AgentSessionOutlineForkResult.thread] describes the new thread that
   * callers can open and refresh after the fork completes. Return `null` if the fork is no longer possible.
   */
  suspend fun forkThreadFromOutlineItem(
    project: Project,
    path: String,
    threadId: String,
    itemId: String,
    subAgentId: String? = null,
    tabKey: String? = null,
  ): AgentSessionOutlineForkResult?
}

/** Capability for providers that track read/unread state relative to open chat tabs. */
@ApiStatus.Internal
interface AgentSessionReadStateSource : AgentSessionSource {
  /** Records that [threadId] is the active thread for this source, or clears it with `null`. */
  fun setActiveThreadId(threadId: String?)

  /** Marks [threadId] as read through [updatedAt] and emits any provider-local hint update needed to clear unread state. */
  fun markThreadAsRead(threadId: String, updatedAt: Long)
}
