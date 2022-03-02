// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.dvcs.push.ui.VcsPushDialog
import com.intellij.dvcs.ui.CustomIconProvider
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.push.GitPushSource
import git4idea.repo.GitRepository
import icons.DvcsImplIcons
import javax.swing.Icon

//TODO: incoming/outgoing
class GitPushBranchAction
  : GitSingleBranchAction(ActionsBundle.messagePointer("action.Vcs.Push.text")) {

  override val disabledForRemote = true

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    if (branch !is GitLocalBranch) return
    VcsPushDialog(project, repositories, repositories, null, GitPushSource.create(branch)).show()
  }
}