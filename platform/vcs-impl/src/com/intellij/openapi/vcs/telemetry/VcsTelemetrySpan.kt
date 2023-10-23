// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.telemetry

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface VcsTelemetrySpan {

  fun getName(): String

  enum class LogHistory : VcsTelemetrySpan {
    Computing {
      override fun getName() = "file-history-computing"
    },

    CollectingRenames {
      override fun getName() = "file-history-collecting-renames"
    }
  }

  object LogFilter : VcsTelemetrySpan {
    override fun getName() = "vcs-log-filtering"
  }

  enum class LogData : VcsTelemetrySpan {

    // Top-level tasks

    Initializing {
      override fun getName() = "vcs-log-initializing"
    },

    Refreshing {
      override fun getName() = "vcs-log-refreshing"
    },

    LoadingFullLog {
      override fun getName() = "vcs-log-loading-full-log"
    },

    // Reading information from the VcsLogProvider

    ReadingRecentCommits {
      override fun getName() = "vcs-log-reading-recent-commits"
    },

    ReadingRecentCommitsInRoot {
      override fun getName() = "vcs-log-reading-recent-commits-in-root"
    },

    ReadingAllCommits {
      override fun getName() = "vcs-log-reading-all-commits"
    },

    ReadingAllCommitsInRoot {
      override fun getName() = "vcs-log-reading-all-commits-in-root"
    },

    ReadingCurrentUser {
      override fun getName() = "vcs-log-reading-current-user"
    },

    // Building new DataPack

    BuildingGraph {
      override fun getName() = "vcs-log-building-graph"
    },

    CompactingCommits {
      override fun getName() = "vcs-log-compacting-commits"
    },

    JoiningNewAndOldCommits {
      override fun getName() = "vcs-log-joining-new-and-old-commits"
    },

    JoiningMultiRepoCommits {
      override fun getName() = "vcs-log-joining-multi-repo-commits"
    },

    // Other

    GettingContainingBranches {
      override fun getName() = "vcs-log-getting-containing-branches"
    },

    Indexing {
      override fun getName() = "vcs-log-indexing"
    },

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
