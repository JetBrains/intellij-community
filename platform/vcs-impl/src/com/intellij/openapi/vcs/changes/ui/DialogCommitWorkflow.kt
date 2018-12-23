// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.changes.LocalChangeList

open class DialogCommitWorkflow(val project: Project,
                                val initiallyIncluded: Collection<*>,
                                val initialChangeList: LocalChangeList? = null,
                                val executors: List<CommitExecutor> = emptyList(),
                                val isDefaultCommitEnabled: Boolean = executors.isEmpty(),
                                val vcsToCommit: AbstractVcs<*>? = null,
                                val affectedVcses: Set<AbstractVcs<*>> = if (vcsToCommit != null) setOf(vcsToCommit) else emptySet(),
                                val isDefaultChangeListFullyIncluded: Boolean = true,
                                val initialCommitMessage: String? = null,
                                val resultHandler: CommitResultHandler? = null) {
  val isPartialCommitEnabled: Boolean = affectedVcses.any { it.arePartialChangelistsSupported() } && (isDefaultCommitEnabled || executors.any { it.supportsPartialCommit() })

  fun showDialog(): Boolean {
    val dialog = CommitChangeListDialog(this)
    return dialog.showAndGet()
  }
}