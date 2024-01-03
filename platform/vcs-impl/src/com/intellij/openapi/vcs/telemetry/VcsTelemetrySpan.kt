// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.telemetry

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface VcsTelemetrySpan {

  fun getName(): String

  enum class LogHistory : VcsTelemetrySpan {
    /**
     * Computing new [com.intellij.vcs.log.visible.VisiblePack] with file history using either indexes or [com.intellij.vcs.log.VcsLogFileHistoryHandler].
     * If the latter is used, a portion of commits that was already produced by the handler is used for the computation,
     * while the handler is working in another thread.
     */
    Computing {
      override fun getName() = "file-history-computing"
    },

    /**
     * Collecting renames for file history built from the index.
     */
    CollectingRenames {
      override fun getName() = "file-history-collecting-renames"
    },

    /**
     * Collecting revisions from the [com.intellij.vcs.log.VcsLogFileHistoryHandler].
     */
    CollectingRevisionsFromHandler {
      override fun getName(): String = "file-history-collecting-revisions-from-handler"
    }
  }

  object LogFilter : VcsTelemetrySpan {
    override fun getName() = "vcs-log-filtering"
  }

  enum class LogData : VcsTelemetrySpan {

    // Top-level tasks

    /**
     * Initializing VCS Log by reading initial portion of commits and references.
     */
    Initializing {
      override fun getName() = "vcs-log-initializing"
    },

    /**
     * Refreshing VCS Log when repositories change (on commit, rebase, checkout branch, etc.).
     */
    Refreshing {
      override fun getName() = "vcs-log-refreshing"
    },

    /**
     * Partial refresh of the VCS Log.
     * @see Refreshing
     */
    PartialRefreshing {
      override fun getName() = "vcs-log-partial-refreshing"
    },

    /**
     * Loading full VCS Log (all commits and references).
     */
    LoadingFullLog {
      override fun getName() = "vcs-log-loading-full-log"
    },

    // Reading information from the VcsLogProvider

    /**
     * Reading a small number of last commits and references from [com.intellij.vcs.log.VcsLogProvider] for all roots.
     */
    ReadingRecentCommits {
      override fun getName() = "vcs-log-reading-recent-commits"
    },

    /**
     * Reading a small number of last commits and references from [com.intellij.vcs.log.VcsLogProvider] per each root.
     */
    ReadingRecentCommitsInRoot {
      override fun getName() = "vcs-log-reading-recent-commits-in-root"
    },

    /**
     * Reading all commits and references from [com.intellij.vcs.log.VcsLogProvider] for all roots.
     */
    ReadingAllCommits {
      override fun getName() = "vcs-log-reading-all-commits"
    },

    /**
     * Reading all commits and references from [com.intellij.vcs.log.VcsLogProvider] per each root.
     */
    ReadingAllCommitsInRoot {
      override fun getName() = "vcs-log-reading-all-commits-in-root"
    },

    /**
     * Reading current user from [com.intellij.vcs.log.VcsLogProvider].
     */
    ReadingCurrentUser {
      override fun getName() = "vcs-log-reading-current-user"
    },

    // Building new DataPack

    /**
     * Building a [com.intellij.vcs.log.graph.PermanentGraph] for the list of commits.
     */
    BuildingGraph {
      override fun getName() = "vcs-log-building-graph"
    },

    /**
     * Converting [com.intellij.vcs.log.TimedVcsCommit] instances received from [com.intellij.vcs.log.VcsLogProvider]
     * to [com.intellij.vcs.log.graph.GraphCommit] instances using [com.intellij.vcs.log.data.VcsLogStorage] for converting hashes to integers.
     *
     * Only reported during [Refreshing] and [Initializing].
     */
    CompactingCommits {
      override fun getName() = "vcs-log-compacting-commits"
    },

    /**
     * Combining new commits, received during [Refreshing], with previously loaded commits, to get a single commit list.
     */
    JoiningNewAndOldCommits {
      override fun getName() = "vcs-log-joining-new-and-old-commits"
    },

    /**
     * Combining commits from multiple repositories to a single commit list.
     */
    JoiningMultiRepoCommits {
      override fun getName() = "vcs-log-joining-multi-repo-commits"
    },

    // Other

    /**
     * Getting a list of containing branches for a commit.
     */
    GettingContainingBranches {
      override fun getName() = "vcs-log-getting-containing-branches"
    },

  }

  enum class LogIndex : VcsTelemetrySpan {
    Indexing {
      override fun getName() = "vcs-log-indexing"
    },

    StoreDetailIndex {
      override fun getName() = "vcs-store-detail-index"
    }

  }

  enum class Shelve : VcsTelemetrySpan {
    TotalShelving {
      override fun getName() = "shelf-total-shelving"
    },

    StoringBaseRevision {
      override fun getName() = "shelf-storing-base-revisions"
    },

    StoringPathFile {
      override fun getName() = "shelf-saving-patch-file"
    },

    BatchShelving {
      override fun getName() = "shelf-batch-shelving"
    },

    PreloadingBaseRevisions {
      override fun getName() = "shelf-preloading-base-revisions"
    },

    BuildingPatches {
      override fun getName() = "shelf-building-patches"
    },

    RollbackAfterShelve {
      override fun getName() = "shelf-rollback-after-shelve"
    }
  }

  enum class ChangesView : VcsTelemetrySpan {
    ChangesViewRefreshBackground {
      override fun getName() = "changes-view-refresh-background"
    },

    ChangesViewRefreshEdt {
      override fun getName() = "changes-view-refresh-edt"
    }
  }
}
