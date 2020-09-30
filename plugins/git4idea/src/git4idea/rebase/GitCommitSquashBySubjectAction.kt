// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.vcs.log.VcsShortCommitDetails
import git4idea.i18n.GitBundle

internal class GitCommitSquashBySubjectAction : GitAutoSquashCommitAction() {
  override fun getCommitMessage(commit: VcsShortCommitDetails) = "squash! ${commit.subject}"
  override fun getFailureTitle() = GitBundle.message("rebase.log.create.squash.commit.action.failure.title")
}