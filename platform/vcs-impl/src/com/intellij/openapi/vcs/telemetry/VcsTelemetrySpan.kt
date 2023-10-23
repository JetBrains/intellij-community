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
    BuildingGraph {
      override fun getName() = "vcs-log-building-graph"
    },

    LoadingCommits {
      override fun getName() = "vcs-log-loading-commits"
    },

    CompactingCommits {
      override fun getName() = "vcs-log-compacting-commits"
    },

    JoiningNewCommits {
      override fun getName() = "vcs-log-joining-new-commits"
    },

    Refresh {
      override fun getName() = "vcs-log-refresh"
    },

    FullLogReload {
      override fun getName() = "vcs-log-full-log-reload"
    },

    ReadFullLogFromVcs {
      override fun getName() = "vcs-log-read-full-log-from-vcs"
    },

    ReadFullLogFromVcsForRoot {
      override fun getName() = "vcs-log-read-full-log-from-vcs-for-root"
    },

    GetContainingBranches {
      override fun getName() = "vcs-log-get-containing-branches"
    },

    Initialize {
      override fun getName() = "vcs-log-initialize"
    },

    ReadCurrentUser {
      override fun getName() = "vcs-log-read-current-user"
    },

    MultiRepoJoin {
      override fun getName() = "vcs-log-multi-repo-join"
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
