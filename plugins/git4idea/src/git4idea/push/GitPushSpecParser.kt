
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.openapi.diagnostic.thisLogger
import git4idea.GitBranch
import git4idea.GitUtil
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository

internal object GitPushSpecParser {
  private val LOG = thisLogger()

  @JvmStatic
  fun getTargetRef(
    repository: GitRepository,
    sourceBranchName: String,
    specs: List<String>,
  ): String? {
    // pushing to several pushSpecs is not supported => looking for the first one which is valid & matches the current branch
    for (spec in specs) {
      val target = getTarget(spec, sourceBranchName)
      if (target == null) {
        LOG.info(
          "Push spec [$spec] in ${repository.root} is invalid or doesn't match source branch $sourceBranchName"
        )
      }
      else {
        return target
      }
    }
    return null
  }

  private fun getTarget(spec: String, sourceBranch: String): String? {
    val parts = spec.split(':').takeIf { it.size == 2 } ?: return null

    val specSource = parts[0].trim().removePrefix("+")
    val specTarget = parts[1].trim()

    if (!isStarPositionValid(specSource, specTarget)) {
      return null
    }

    val strippedSpecSource = GitBranchUtil.stripRefsPrefix(specSource)
    val strippedSourceBranch = GitBranchUtil.stripRefsPrefix(sourceBranch)
    val fullSourceBranch = GitBranch.REFS_HEADS_PREFIX + strippedSourceBranch

    if (strippedSpecSource == GitUtil.HEAD ||
        specSource == fullSourceBranch ||
        specSource == strippedSourceBranch
    ) {
      return specTarget
    }

    if (specSource.endsWith("*")) {
      val sourceWoStar = specSource.substring(0, specSource.length - 1)
      if (fullSourceBranch.startsWith(sourceWoStar)) {
        val starMeaning = fullSourceBranch.substring(sourceWoStar.length)
        return specTarget.replace("*", starMeaning)
      }
    }
    return null
  }

  private fun isStarPositionValid(source: String, target: String): Boolean {
    val sourceStar = source.indexOf('*')
    val targetStar = target.indexOf('*')
    return (sourceStar < 0 && targetStar < 0) || (sourceStar == source.length - 1 && targetStar == target.length - 1)
  }
}