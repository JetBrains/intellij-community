// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.CommitContext

abstract class VcsCommitter(
  project: Project,
  val changes: List<Change>,
  commitMessage: @NlsSafe String,
  val commitContext: CommitContext,
  useCustomPostRefresh: Boolean
) : AbstractCommitter(project, commitMessage, useCustomPostRefresh) {

  private val _feedback = mutableSetOf<String>()
  private val _failedToCommitChanges = mutableListOf<Change>()
  private val _pathsToRefresh = mutableListOf<FilePath>()

  val failedToCommitChanges: List<Change> get() = _failedToCommitChanges.toList()
  val pathsToRefresh: List<FilePath> get() = _pathsToRefresh.toList()
  val feedback: Set<String> get() = _feedback.toSet()

  protected fun vcsCommit(vcs: AbstractVcs, changes: List<Change>) {
    val environment = vcs.checkinEnvironment
    if (environment == null) {
      logger<VcsCommitter>().error("Skipping commit for ${vcs.name} - not implemented")
      return
    }

    _pathsToRefresh.addAll(ChangesUtil.getPaths(changes))
    val exceptions = environment.commit(changes, commitMessage, commitContext, _feedback)
    if (!exceptions.isNullOrEmpty()) {
      exceptions.forEach { addException(it) }
      _failedToCommitChanges.addAll(changes)
    }
  }
}