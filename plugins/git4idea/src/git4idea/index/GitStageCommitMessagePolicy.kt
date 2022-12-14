// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.TextAccessor
import com.intellij.vcs.commit.AbstractCommitMessagePolicy
import git4idea.repo.GitCommitTemplateTracker

class GitStageCommitMessagePolicy(project: Project) : AbstractCommitMessagePolicy(project) {
  fun init(): String? {
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) return null

    return vcsConfiguration.LAST_COMMIT_MESSAGE
  }

  fun onBeforeCommit(commitMessage: String) {
    vcsConfiguration.saveCommitMessage(commitMessage)
  }

  fun onAfterCommit(currentMessage: TextAccessor) {
    if (vcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) {
      currentMessage.text = null
      return
    }

    currentMessage.text = getCommitTemplateMessage()
                          ?: vcsConfiguration.LAST_COMMIT_MESSAGE
  }

  private fun getCommitTemplateMessage(): String? = project.service<GitCommitTemplateTracker>().getTemplateContent()
}
