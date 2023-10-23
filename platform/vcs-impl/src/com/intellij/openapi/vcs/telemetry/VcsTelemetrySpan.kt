// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.telemetry

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface VcsTelemetrySpan {

  fun getName(): String

  enum class LogHistory : VcsTelemetrySpan {
    Computing {
      override fun getName() = "computing history"
    },

    CollectingRenames {
      override fun getName() = "collecting renames"
    }
  }

  object LogFilter : VcsTelemetrySpan {
    private const val NAME = "filter"
    override fun getName() = NAME
  }

  enum class LogData : VcsTelemetrySpan {
    BuildingGraph {
      override fun getName() = "building graph"
    },

    LoadingCommits {
      override fun getName() = "loading commits"
    },

    CompactingCommits {
      override fun getName() = "compacting commits"
    },

    JoiningNewCommits {
      override fun getName() = "joining new commits"
    },

    Refresh {
      override fun getName() = "refresh"
    },

    FullLogReload {
      override fun getName() = "full log reload"
    },

    ReadFullLogFromVcs {
      override fun getName() = "read full log from VCS"
    },

    ReadFullLogFromVcsForRoot {
      override fun getName() = "read full log from VCS for root"
    },

    GetContainingBranches {
      override fun getName() = "get containing branches"
    },

    Initialize {
      override fun getName() = "initialize"
    },

    ReadCurrentUser {
      override fun getName() = "readCurrentUser"
    },

    MultiRepoJoin {
      override fun getName() = "multi-repo join"
    },

    Indexing {
      override fun getName() = "vcs-log-indexing"
    },

  }

  enum class Shelve : VcsTelemetrySpan {
    TotalShelving {
      override fun getName() = "total shelving"
    },

    StoringBaseRevision {
      override fun getName() = "storing base revisions"
    },

    StoringPathFile {
      override fun getName() = "saving patch file"
    },

    BatchShelving {
      override fun getName() = "batch shelving"
    },

    PreloadingBaseRevisions {
      override fun getName() = "preloading base revisions"
    },

    BuildingPatches {
      override fun getName() = "building patches"
    },

    RollbackAfterShelve {
      override fun getName() = "rollback after shelve"
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
