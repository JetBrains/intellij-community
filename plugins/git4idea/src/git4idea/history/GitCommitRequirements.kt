// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history

import com.intellij.openapi.project.Project
import git4idea.config.GitExecutable
import git4idea.config.GitVersionSpecialty

/**
 * Allows controlling how changes are reported when reading [git4idea.GitCommit] instances from `git log`.
 *
 * @property includeRootChanges whether to include changes for initial commits
 * @property diffRenames        how to detect renames
 * @property diffInMergeCommits how to report changes for merge commits
 *
 * @see git4idea.GitCommit
 * @see GitDetailsCollector
 * @see GitLogUtil.readFullDetailsForHashes
 */
data class GitCommitRequirements(private val includeRootChanges: Boolean = true,
                                 val diffRenames: DiffRenames = DiffRenames.Limit.Default,
                                 val diffInMergeCommits: DiffInMergeCommits = DiffInMergeCommits.COMBINED_DIFF) {

  fun configParameters(): List<String> {
    val result = mutableListOf<String>()
    when (diffRenames) {
      is DiffRenames.Limit -> {
        diffRenames.limit?.let { limit -> result.add(renameLimit(limit)) }
      }
      DiffRenames.NoRenames -> result.add("diff.renames=false")
    }

    if (!includeRootChanges) {
      result.add("log.showRoot=false")
    }
    return result
  }

  fun commandParameters(project: Project, executable: GitExecutable): List<String> {
    val result = mutableListOf<String>()
    if (diffRenames != DiffRenames.NoRenames) {
      if (diffRenames is DiffRenames.Limit && diffRenames.similarityIndexThreshold != null) {
        result.add("-M${diffRenames.similarityIndexThreshold.coerceIn(0, 100)}%")
      }
      else {
        result.add("-M")
      }
    }
    result.addAll(diffInMergeCommits.commandParameters(project, executable))
    return result
  }

  private fun renameLimit(limit: Int): String {
    return "diff.renameLimit=$limit"
  }

  /**
   * Regulates how renames are detected
   *
   * @see <a href="https://git-scm.com/docs/git-config#Documentation/git-config.txt-diffrenameLimit">diff.renameLimit</a>
   * @see <a href="https://book.git-scm.com/docs/git-diff/2.6.7#Documentation/git-diff.txt--Mltngt">git diff -M</a>
   */
  sealed class DiffRenames {
    /**
     * Enable renames detection
     *
     * @property limit                    the number of files to consider in the exhaustive portion of copy/rename detection
     * @property similarityIndexThreshold threshold on the similarity index for rename detection (in percent)
     */
    sealed class Limit(val limit: Int? = null, val similarityIndexThreshold: Int? = null) : DiffRenames() {
      /**
       * Use specified values for rename limit and similarity threshold
       */
      class Value(limit: Int?, similarity: Int? = null) : Limit(limit, similarity)

      /**
       * Use zero value for the diff.renameLimit to always detect inexact renames
       */
      class Infinity(similarity: Int? = null) : Limit(0, similarity)

      /**
       * Detect only exact renames
       */
      data object Exact : Limit(1, 100)

      /**
       * Use diff.renameLimit value set in users git.config and default similarity threshold value
       */
      data object Default : Limit()
    }

    /**
     * Disable renames detection
     */
    data object NoRenames : DiffRenames()
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
      override fun commandParameters(project: Project, executable: GitExecutable): List<String> = emptyList()
    },

    /**
     * Report combined changes (same as `git log -c`)
     */
    COMBINED_DIFF {
      override fun commandParameters(project: Project, executable: GitExecutable): List<String> = listOf("-c")
    },

    /**
     * Report changes to each parent (same as `git log --diff-merges=separate` or `git log -m` in older git versions)
     */
    DIFF_TO_PARENTS {
      override fun commandParameters(project: Project, executable: GitExecutable): List<String> {
        return if (GitVersionSpecialty.DIFF_MERGES_M_USES_DEFAULT_SETTING.existsIn(project, executable)) {
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
      override fun commandParameters(project: Project, executable: GitExecutable): List<String> {
        if (GitVersionSpecialty.DIFF_MERGES_SUPPORTS_FIRST_PARENT.existsIn(project, executable)) {
          return listOf("--diff-merges=first-parent")
        }
        else {
          // if the required option does not exist in this git version, get changes to each parent
          return DIFF_TO_PARENTS.commandParameters(project, executable)
        }
      }
    };

    abstract fun commandParameters(project: Project, executable: GitExecutable): List<String>
  }

  companion object {
    @JvmField
    val DEFAULT = GitCommitRequirements()
  }
}
