// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

data class GitCommitRequirements(private val includeRootChanges: Boolean = true,
                                 val diffRenameLimit: DiffRenameLimit = DiffRenameLimit.GitConfig,
                                 val diffInMergeCommits: DiffInMergeCommits = DiffInMergeCommits.COMBINED_DIFF) {

  fun configParameters(): List<String> {
    val result = mutableListOf<String>()
    when (diffRenameLimit) {
      DiffRenameLimit.Infinity -> result.add(renameLimit(0))
      is DiffRenameLimit.Value -> result.add(renameLimit(diffRenameLimit.limit))
      DiffRenameLimit.NoRenames -> result.add("diff.renames=false")
      DiffRenameLimit.GitConfig -> {}
    }

    if (!includeRootChanges) {
      result.add("log.showRoot=false")
    }
    return result
  }

  fun commandParameters(): List<String> {
    val result = mutableListOf<String>()
    if (diffRenameLimit != DiffRenameLimit.NoRenames) {
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

  sealed class DiffRenameLimit {
    /**
     * Use zero value
     */
    object Infinity : DiffRenameLimit()

    /**
     * Use specified value
     */
    class Value(val limit: Int) : DiffRenameLimit()

    /**
     * Use value set in users git.config
     */
    object GitConfig : DiffRenameLimit()

    /**
     * Disable renames detection
     */
    object NoRenames : DiffRenameLimit()
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
