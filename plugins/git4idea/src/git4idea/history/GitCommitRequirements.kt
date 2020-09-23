// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.util.registry.Registry

data class GitCommitRequirements(private val includeRootChanges: Boolean = true,
                                 val diffRenameLimit: DiffRenameLimit = DiffRenameLimit.GIT_CONFIG,
                                 val diffInMergeCommits: DiffInMergeCommits = DiffInMergeCommits.COMBINED_DIFF) {

  fun configParameters(): List<String> {
    val result = mutableListOf<String>()
    when (diffRenameLimit) {
      DiffRenameLimit.INFINITY -> result.add(renameLimit(0))
      DiffRenameLimit.REGISTRY -> result.add(renameLimit(Registry.intValue("git.diff.renameLimit")))
      DiffRenameLimit.NO_RENAMES -> result.add("diff.renames=false")
      else -> {
      }
    }

    if (!includeRootChanges) {
      result.add("log.showRoot=false")
    }
    return result
  }

  fun commandParameters(): List<String> {
    val result = mutableListOf<String>()
    if (diffRenameLimit != DiffRenameLimit.NO_RENAMES) {
      result.add("-M")
    }
    when (diffInMergeCommits) {
      DiffInMergeCommits.DIFF_TO_PARENTS -> result.add("-m")
      DiffInMergeCommits.COMBINED_DIFF -> result.add("-c")
      DiffInMergeCommits.NO_DIFF -> {
      }
    }
    return result
  }

  private fun renameLimit(limit: Int): String {
    return "diff.renameLimit=$limit"
  }

  enum class DiffRenameLimit {
    /**
     * Use zero value
     */
    INFINITY,
    /**
     * Use value set in registry (usually 1000)
     */
    REGISTRY,
    /**
     * Use value set in users git.config
     */
    GIT_CONFIG,
    /**
     * Disable renames detection
     */
    NO_RENAMES
  }

  enum class DiffInMergeCommits {
    /**
     * Do not report changes for merge commits
     */
    NO_DIFF,
    /**
     * Report combined changes (same as `git log -c`)
     */
    COMBINED_DIFF,
    /**
     * Report changes to all of the parents (same as `git log -m`)
     */
    DIFF_TO_PARENTS
  }

  companion object {
    @JvmField
    val DEFAULT = GitCommitRequirements()
  }
}
