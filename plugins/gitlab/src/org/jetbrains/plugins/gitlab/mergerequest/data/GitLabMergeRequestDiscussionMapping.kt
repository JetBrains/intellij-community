// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.changes.Change
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.findCumulativeChange

interface GitLabMergeRequestDiscussionChangeMapping {
  class Actual(val change: Change, val location: DiffLineLocation? = null) : GitLabMergeRequestDiscussionChangeMapping
  class Outdated(val change: Change, val originalLocation: DiffLineLocation? = null) : GitLabMergeRequestDiscussionChangeMapping
  object Obsolete : GitLabMergeRequestDiscussionChangeMapping
  class Error(val error: Throwable) : GitLabMergeRequestDiscussionChangeMapping

  companion object {
    private val LOG = logger<GitLabMergeRequestDiscussionChangeMapping>()

    fun map(mrChanges: GitBranchComparisonResult, position: GitLabDiscussionPosition): GitLabMergeRequestDiscussionChangeMapping {
      val textLocation = (position as? GitLabDiscussionPosition.Text)?.location

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
              return Outdated(change, textLocation)
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
        val beforeRevision = it.beforeRevision
        val afterRevision = it.afterRevision
        beforeRevision != null && position.filePathBefore != null && beforeRevision.file.path.endsWith(position.filePathBefore!!) ||
        afterRevision != null && position.filePathAfter != null && afterRevision.file.path.endsWith(position.filePathAfter!!)
      } ?: run {
        LOG.debug("Can't find change for $position")
        return Obsolete
      }
      return Actual(change, textLocation)
    }
  }
}