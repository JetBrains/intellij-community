// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.vcs.commit.AbstractCommitMessagePolicy
import git4idea.repo.GitCommitTemplateTracker

class GitStageCommitMessagePolicy(project: Project) : AbstractCommitMessagePolicy(project) {
  fun getCommitMessage(isAfterCommit: Boolean): String? =
    with(vcsConfiguration) {
      when {
        CLEAR_INITIAL_COMMIT_MESSAGE -> null
        isAfterCommit -> getCommitTemplateMessage() ?: LAST_COMMIT_MESSAGE
        else -> LAST_COMMIT_MESSAGE
      }
    }

  private fun getCommitTemplateMessage(): String? = project.service<GitCommitTemplateTracker>().getTemplateContent()

  fun save(commitMessage: String, saveToHistory: Boolean) {
    if (saveToHistory) vcsConfiguration.saveCommitMessage(commitMessage)
  }
}
