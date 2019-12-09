// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.util.GithubGitHelper
import org.jetbrains.plugins.github.util.GithubUrlUtil
import javax.swing.Icon

/**
 * Visible and enabled if there's at least one possible github remote url ([GithubGitHelper]).
 * If there's only one url - it will be used for action, otherwise child actions will be created for each url.
 */
abstract class AbstractGithubUrlGroupingAction(text: String?, description: String?, icon: Icon?)
  : ActionGroup(text, description, icon), DumbAware {

  final override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isEnabledAndVisible(e)
  }

  protected open fun isEnabledAndVisible(e: AnActionEvent): Boolean {
    val project = e.getData(CommonDataKeys.PROJECT)
    if (project == null || project.isDefault) return false

    return service<GithubGitHelper>().havePossibleRemotes(project)
  }

  final override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.getData(CommonDataKeys.PROJECT) ?: return AnAction.EMPTY_ARRAY

    val coordinates = service<GithubGitHelper>().getPossibleRemoteUrlCoordinates(project)

    return if (coordinates.size > 1) {
      coordinates.map {
        object : DumbAwareAction(it.remote.name + ": " + GithubUrlUtil.removeProtocolPrefix(it.url)) {
          override fun actionPerformed(e: AnActionEvent) {
            actionPerformed(e, project, it.repository, it.remote, it.url)
          }
        }
      }.toTypedArray()
    }
    else AnAction.EMPTY_ARRAY
  }

  final override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return

    val coordinates = service<GithubGitHelper>().getPossibleRemoteUrlCoordinates(project)
    coordinates.singleOrNull()?.let { actionPerformed(e, project, it.repository, it.remote, it.url) }
  }

  abstract fun actionPerformed(e: AnActionEvent, project: Project, repository: GitRepository, remote: GitRemote, remoteUrl: String)

  final override fun canBePerformed(context: DataContext): Boolean {
    val project = context.getData(CommonDataKeys.PROJECT) ?: return false

    val coordinates = service<GithubGitHelper>().getPossibleRemoteUrlCoordinates(project)
    return coordinates.size == 1
  }

  final override fun isPopup(): Boolean = true
  final override fun disableIfNoVisibleChildren(): Boolean = false
}