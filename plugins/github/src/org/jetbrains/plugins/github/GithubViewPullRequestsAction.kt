// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRToolWindowTabsManager
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import java.util.function.Supplier

class GithubViewPullRequestsAction :
  AbstractGithubUrlGroupingAction(GithubBundle.messagePointer("pull.request.view.list"),
                                  Supplier { null },
                                  AllIcons.Vcs.Vendors.Github) {

  override fun actionPerformed(e: AnActionEvent, project: Project, repository: GHGitRepositoryMapping) =
    project.service<GHPRToolWindowTabsManager>().showTab(repository.repository)
}