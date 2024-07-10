
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.openapi.diagnostic.thisLogger
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitUtil
import git4idea.repo.GitRepository

internal object GitPushSpecParser {
  private val LOG = thisLogger()

  /**
   * Returns either full match or firs wildcard match of [sourceBranch] in [specs],
   * as pushing to several targets is not supported
   */
  @JvmStatic
  fun getTargetRef(repository: GitRepository, sourceBranch: GitLocalBranch, specs: List<String>): String? {
    var targetRef: TargetRef? = null
    for (spec in specs) {
      val target = getTarget(spec, sourceBranch)
      when (target?.wildcardMatch) {
        true -> if (targetRef == null) {
          targetRef = target
        }
        false -> return target.ref
        null -> LOG.info(
          "Push spec [$spec] in ${repository.root} is invalid or doesn't match source branch ${sourceBranch.name}"
        )
      }
    }
    return targetRef?.ref
  }

  /**
   * @see <a href="https://git-scm.com/book/en/v2/Git-Internals-The-Refspec#Pushing_Refspecs">Pushing refspecs</a>
   */
  private fun getTarget(spec: String, sourceBranch: GitLocalBranch): TargetRef? {
    val parts = spec.split(':').takeIf { it.size == 2 } ?: return null

    val specSource = parts[0].trim().removePrefix("+")
    val specTarget = parts[1].trim()

    val wildcardType = getWildcardType(specSource, specTarget)
    return when (wildcardType) {
      WildcardType.NONE -> {
        if (exactMatchWithSpec(branch = sourceBranch, specSource = specSource)) TargetRef(specTarget, wildcardMatch = false)
        else null
      }
      WildcardType.END -> {
        val trimmedSourceSpec = specSource.trimEnd('*')
        // Git doesn't match wildcards in short refs
        if (sourceBranch.fullName.startsWith(trimmedSourceSpec)) {
          val starMeaning = sourceBranch.fullName.substring(trimmedSourceSpec.length)
          TargetRef(specTarget.replace("*", starMeaning), wildcardMatch = true)
        }
        else null
      }
      WildcardType.INVALID -> null
    }
  }

  private fun exactMatchWithSpec(branch: GitLocalBranch, specSource: String): Boolean =
    specSource == GitUtil.HEAD || branch.name == specSource.removePrefix(GitBranch.REFS_HEADS_PREFIX)

  private fun getWildcardType(source: String, target: String): WildcardType {
    val sourceStar = source.indexOf('*')
    val targetStar = target.indexOf('*')
    return when {
      sourceStar < 0 && targetStar < 0 -> WildcardType.NONE
      sourceStar == source.length - 1 && targetStar == target.length - 1 -> WildcardType.END
      else -> WildcardType.INVALID
    }
  }

  private data class TargetRef(val ref: String, val wildcardMatch: Boolean)

  private enum class WildcardType {
    NONE, END, INVALID
  }
}