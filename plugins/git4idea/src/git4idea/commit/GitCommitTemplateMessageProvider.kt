// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commit

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.CommitMessageProvider
import git4idea.repo.GitCommitTemplateTracker

internal class GitCommitTemplateMessageProvider: CommitMessageProvider {
  override fun getCommitMessage(forChangelist: LocalChangeList, project: Project): String? {
    return project.service<GitCommitTemplateTracker>().getTemplateContent()
  }
}
