// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.LocalChangeList

internal class SingleChangeListCommitMessagePolicy(
  project: Project,
  ui: SingleChangeListCommitWorkflowUi,
  private val initialCommitMessage: String?,
  initialChangeList: LocalChangeList,
) : ChangeListCommitMessagePolicy(project, ui.commitMessageUi, initialChangeList,) {
  override val delayedMessagesProvidersSupport = null

  override fun getNewCommitMessage(): CommitMessage? {
    return initialCommitMessage?.let { CommitMessage(it) } ?: getCommitMessageForCurrentList()
  }
}