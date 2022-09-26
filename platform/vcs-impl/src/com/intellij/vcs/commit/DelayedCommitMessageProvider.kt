// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.CommitMessageProvider
import org.jetbrains.annotations.ApiStatus

/**
 * @see CommitMessageProvider
 */
@ApiStatus.Experimental
interface DelayedCommitMessageProvider {
  companion object {
    private val EP_DELAYED_COMMIT_MESSAGE_PROVIDER =
      ExtensionPointName<DelayedCommitMessageProvider>("com.intellij.vcs.delayedCommitMessageProvider")

    fun init(project: Project,
             commitUi: CommitWorkflowUi,
             initialCommitMessage: String?) = EP_DELAYED_COMMIT_MESSAGE_PROVIDER.forEachExtensionSafe { e ->
      e.init(project, commitUi, initialCommitMessage)
    }
  }

  fun init(project: Project, commitUi: CommitWorkflowUi, initialCommitMessage: String?)
}
