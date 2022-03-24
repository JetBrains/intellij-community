// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history

import com.intellij.openapi.project.Project
import git4idea.config.GitVersionSpecialty

/**
 * Allows controlling how changes are reported when reading [git4idea.GitCommit] instances from `git log`.
 *
 * @see git4idea.GitCommit
 * @see GitDetailsCollector
 * @see GitLogUtil.readFullDetailsForHashes
 */
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

  fun commandParameters(project: Project): List<String> {
    val result = mutableListOf<String>()
    if (diffRenameLimit != DiffRenameLimit.NoRenames) {
      result.add("-M")
    }
    result.addAll(diffInMergeCommits.commandParameters(project))
    return result
  }

  private fun renameLimit(limit: Int): String {
    return "diff.renameLimit=$limit"
  }

  /**
   * Regulates how renames are detected
   *
   * @see <a href="https://git-scm.com/docs/git-config#Documentation/git-config.txt-diffrenameLimit">diff.renameLimit</a>
   */
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

  /**
   * Regulates how changes are reported for merge commits
   *
   * @see <a href="https://git-scm.com/docs/git-log#_diff_formatting">Diff Formatting</a>
   */
  enum class DiffInMergeCommits {
    /**
     * Do not report changes for merge commits
     */
    NO_DIFF {
      override fun commandParameters(project: Project): List<String> = emptyList()
    },

    /**
     * Report combined changes (same as `git log -c`)
     */
    COMBINED_DIFF {
      override fun commandParameters(project: Project): List<String> = listOf("-c")
    },

    /**
     * Report changes to each parent (same as `git log --diff-merges=separate` or `git log -m` in older git versions)
     */
    DIFF_TO_PARENTS {
      override fun commandParameters(project: Project): List<String> {
        return if (GitVersionSpecialty.DIFF_MERGES_M_USES_DEFAULT_SETTING.existsIn(project)) {
          listOf("--diff-merges=separate")
        }
        else {
          listOf("-m")
        }
      }
    },

    /**
     * Report changes to the first parent only (same as `git log --diff-merges=first-parent`).
     * Works only since git 2.31.0.
     */
    FIRST_PARENT {
      override fun commandParameters(project: Project): List<String> {
        if (GitVersionSpecialty.DIFF_MERGES_SUPPORTS_FIRST_PARENT.existsIn(project)) {
          return listOf("--diff-merges=first-parent")
        }
        else {
          // if the required option does not exist in this git version, get changes to each parent
          return DIFF_TO_PARENTS.commandParameters(project)
        }
      }
    };

    abstract fun commandParameters(project: Project): List<String>
  }

  companion object {
    @JvmField
    val DEFAULT = GitCommitRequirements()
  }
}
