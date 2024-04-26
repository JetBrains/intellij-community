// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.util.ChangesSelection
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.findCumulativeChange

interface GitLabMergeRequestNotePositionMapping {
  class Actual(val change: ChangesSelection.Precise) : GitLabMergeRequestNotePositionMapping
  class Outdated(val change: ChangesSelection.Precise) : GitLabMergeRequestNotePositionMapping
  object Obsolete : GitLabMergeRequestNotePositionMapping
  class Error(val error: Throwable) : GitLabMergeRequestNotePositionMapping

  companion object {
    private val LOG = logger<GitLabMergeRequestNotePositionMapping>()

    fun map(mrChanges: GitBranchComparisonResult, position: GitLabNotePosition): GitLabMergeRequestNotePositionMapping {
      val textLocation = position.getLocation(Side.LEFT)

      val changes = if (position.parentSha == mrChanges.mergeBaseSha) {
        // first commit
        if (mrChanges.commits.firstOrNull()?.sha == position.sha) {
          mrChanges.changesByCommits[position.sha] ?: run {
            LOG.debug("Missing commit for $position")
            return Obsolete
          }
        }
        // three-dot
        else {
          if (position.sha != mrChanges.headSha) {
            LOG.debug("Current head differs from $position")
            val change = mrChanges.findCumulativeChange(position.sha, position.filePath)
            if (change != null) {
              val changeSelection = ChangesSelection.Precise(mrChanges.changes, change, textLocation)
              return Outdated(changeSelection)
            }
            else {
              return Obsolete
            }
          }
          mrChanges.changes
        }
      }
      else {
        val commitsFound = mrChanges.commits.withIndex().any { (idx, commit) ->
          commit.sha == position.sha &&
          (commit.parents.contains(position.parentSha) || (idx == 0 && mrChanges.mergeBaseSha == position.parentSha))
        }
        if (!commitsFound) {
          LOG.debug("Missing target or parent commits for $position")
          return Obsolete
        }
        mrChanges.changesByCommits[position.sha] ?: run {
          LOG.debug("Missing commit for $position")
          return Obsolete
        }
      }

      val change = changes.find {
        val pathBefore = it.filePathBefore
        val pathAfter = it.filePathAfter
        pathBefore != null && position.filePathBefore != null && pathBefore.path.endsWith(position.filePathBefore) ||
        pathAfter != null && position.filePathAfter != null && pathAfter.path.endsWith(position.filePathAfter)
      } ?: run {
        LOG.debug("Can't find change for $position")
        return Obsolete
      }
      val changeSelection = ChangesSelection.Precise(changes, change, textLocation)
      return Actual(changeSelection)
    }
  }
}