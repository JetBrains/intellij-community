// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.telemetry

import com.intellij.platform.vcs.impl.shared.telemetry.VcsTelemetrySpan
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface VcsBackendTelemetrySpan: VcsTelemetrySpan {
  enum class LogHistory(val tag: String) : VcsBackendTelemetrySpan {
    /**
     * Computing new [com.intellij.vcs.log.visible.VisiblePack] with file history using either indexes or [com.intellij.vcs.log.VcsLogFileHistoryHandler].
     * If the latter is used, a portion of commits that was already produced by the handler is used for the computation,
     * while the handler is working in another thread.
     */
    Computing("file-history-computing"),

    /**
     * Collecting renames for file history built from the index.
     */
    CollectingRenames("file-history-collecting-renames"),

    /**
     * Collecting revisions from the [com.intellij.vcs.log.VcsLogFileHistoryHandler].
     */
    CollectingRevisionsFromHandler("file-history-collecting-revisions-from-handler");

    override fun getName(): String = tag
  }

  object LogFilter : VcsBackendTelemetrySpan {
    override fun getName() = "vcs-log-filtering"
  }

  enum class LogData(val tag: String) : VcsBackendTelemetrySpan {
    // Top-level tasks

    /**
     * Initializing VCS Log by reading initial portion of commits and references.
     */
    Initializing("vcs-log-initializing"),

    /**
     * Refreshing VCS Log when repositories change (on commit, rebase, checkout branch, etc.).
     */
    Refreshing("vcs-log-refreshing"),

    /**
     * Partial refresh of the VCS Log.
     * @see Refreshing
     */
    PartialRefreshing("vcs-log-partial-refreshing"),

    /**
     * Loading full VCS Log (all commits and references).
     */
    LoadingFullLog("vcs-log-loading-full-log"),

    // Reading information from the VcsLogProvider

    /**
     * Reading a small number of last commits and references from [com.intellij.vcs.log.VcsLogProvider] for all roots.
     */
    ReadingRecentCommits("vcs-log-reading-recent-commits"),

    /**
     * Reading a small number of last commits and references from [com.intellij.vcs.log.VcsLogProvider] per each root.
     */
    ReadingRecentCommitsInRoot("vcs-log-reading-recent-commits-in-root"),

    /**
     * Reading all commits and references from [com.intellij.vcs.log.VcsLogProvider] for all roots.
     */
    ReadingAllCommits("vcs-log-reading-all-commits"),

    /**
     * Reading all commits and references from [com.intellij.vcs.log.VcsLogProvider] per each root.
     */
    ReadingAllCommitsInRoot("vcs-log-reading-all-commits-in-root"),

    /**
     * Reading current user from [com.intellij.vcs.log.VcsLogProvider].
     */
    ReadingCurrentUser("vcs-log-reading-current-user"),

    // Building new DataPack

    /**
     * Building a [com.intellij.vcs.log.graph.PermanentGraph] for the list of commits.
     */
    BuildingGraph("vcs-log-building-graph"),

    /**
     * Converting [com.intellij.vcs.log.TimedVcsCommit] instances received from [com.intellij.vcs.log.VcsLogProvider]
     * to [com.intellij.vcs.log.graph.GraphCommit] instances using [com.intellij.vcs.log.data.VcsLogStorage] for converting hashes to integers.
     *
     * Only reported during [Refreshing] and [Initializing].
     */
    CompactingCommits("vcs-log-compacting-commits"),

    /**
     * Combining new commits, received during [Refreshing], with previously loaded commits, to get a single commit list.
     */
    JoiningNewAndOldCommits("vcs-log-joining-new-and-old-commits"),

    /**
     * Combining commits from multiple repositories to a single commit list.
     */
    JoiningMultiRepoCommits("vcs-log-joining-multi-repo-commits"),

    // Other

    /**
     * Getting a list of containing branches for a commit.
     */
    GettingContainingBranches("vcs-log-getting-containing-branches");

    override fun getName(): String = tag
  }

  enum class LogIndex(val tag: String) : VcsBackendTelemetrySpan {
    Indexing("vcs-log-indexing"),

    StoreDetailIndex("vcs-store-detail-index");

    override fun getName(): String = tag
  }

  enum class Shelve(val tag: String) : VcsBackendTelemetrySpan {
    TotalShelving("shelf-total-shelving"),

    StoringBaseRevision("shelf-storing-base-revisions"),

    StoringPathFile("shelf-saving-patch-file"),

    BatchShelving("shelf-batch-shelving"),

    PreloadingBaseRevisions("shelf-preloading-base-revisions"),

    BuildingPatches("shelf-building-patches"),

    RollbackAfterShelve("shelf-rollback-after-shelve");

    override fun getName(): String = tag
  }

  enum class ChangesView(val tag: String) : VcsBackendTelemetrySpan {
    ChangesViewRefreshBackground("changes-view-refresh-background"),

    ChangesViewRefreshEdt("changes-view-refresh-edt");

    override fun getName(): String = tag
  }
}
