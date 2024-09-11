// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.tag

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import git4idea.GitTag
import git4idea.branch.GitBrancher
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository

internal class GitCheckoutTagAction() : GitSingleTagAction(GitBundle.messagePointer("branches.checkout")) {

  override fun isEnabledForRef(ref: GitTag, repositories: List<GitRepository>) = !isCurrentRefInAnyRepo(ref, repositories)

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitTag) {
    GitBrancher.getInstance(project).checkout(reference.fullName, false, repositories, null)
  }

}