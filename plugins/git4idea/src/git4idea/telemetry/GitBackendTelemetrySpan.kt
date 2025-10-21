// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.telemetry

import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan
import com.intellij.vcs.git.telemetry.GitTelemetrySpan
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface GitBackendTelemetrySpan : VcsBackendTelemetrySpan, GitTelemetrySpan {
  enum class Repository : GitBackendTelemetrySpan {
    ReadGitRepositoryInfo {
      override fun getName() = "git-reading-repo-info"
    }
  }

  enum class Operation : GitBackendTelemetrySpan {
    Checkout {
      override fun getName() = "git-checkout"
    }
  }

  enum class Log : GitBackendTelemetrySpan {
    LoadingFullCommitDetails {
      override fun getName() = "git-loading-full-commit-details"
    },

    LoadingCommitMetadata {
      override fun getName() = "git-loading-commit-metadata"
    }
  }

  enum class LogProvider : GitBackendTelemetrySpan {
    SortingCommits {
      override fun getName() = "git-log-sorting-commits"
    },

    ValidatingData {
      override fun getName() = "git-log-validating-data"
    },

    ReadingTags {
      override fun getName() = "git-log-reading-tags"
    },

    LoadingCommitsOnTaggedBranch {
      override fun getName() = "git-log-loading-commits-on-tagged-branch"
    },

    ReadingBranches {
      override fun getName() = "git-log-reading-branches"
    }
  }

  enum class Annotations : GitBackendTelemetrySpan {
    OpenAnnotation {
      override fun getName() = "git-open-annotation"
    }
  }
}
