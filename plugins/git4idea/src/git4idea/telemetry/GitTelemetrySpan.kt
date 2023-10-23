// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.telemetry

import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpan
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface GitTelemetrySpan : VcsTelemetrySpan {
  enum class Repository : GitTelemetrySpan {
    ReadGitRepositoryInfo {
      override fun getName() = "reading Git repo info"
    }
  }

  enum class Operation : GitTelemetrySpan {
    Checkout {
      override fun getName() = "git-checkout"
    }
  }

  enum class Log : GitTelemetrySpan {
    LoadingDetails {
      override fun getName() = "loading details"
    },

    LoadingCommitMetadata {
      override fun getName() = "loading commit metadata"
    }
  }

  enum class LogProvider : GitTelemetrySpan {
    SortingCommits {
      override fun getName() = "sorting commits"
    },

    ValidatingData {
      override fun getName() = "validating data"
    },

    ReadingTags {
      override fun getName() = "reading tags"
    },

    LoadingCommitsOnTaggedBranch {
      override fun getName() = "loading commits on tagged branch"
    },

    ReadBranches {
      override fun getName() = "readBranches"
    }
  }
}
