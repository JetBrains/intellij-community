// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHProjectRepositoriesManager
import org.jetbrains.plugins.github.util.GithubUrlUtil
import java.util.function.Supplier
import javax.swing.Icon

/**
 * Visible and enabled if there's at least one github repository ([GHProjectRepositoriesManager]).
 * If there's only one repository it will be used for action, otherwise child actions will be created for each repo.
 */
abstract class AbstractGithubUrlGroupingAction(dynamicText: Supplier<String?>, dynamicDescription: Supplier<String?>, icon: Icon?)
  : ActionGroup(dynamicText, dynamicDescription, icon), DumbAware {

  final override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isEnabledAndVisible(e)
  }

  protected open fun isEnabledAndVisible(e: AnActionEvent): Boolean {
    val project = e.getData(CommonDataKeys.PROJECT)
    if (project == null || project.isDefault) return false
    return project.service<GHProjectRepositoriesManager>().knownRepositories.isNotEmpty()
  }

  final override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.getData(CommonDataKeys.PROJECT) ?: return AnAction.EMPTY_ARRAY

    val repositories = project.service<GHProjectRepositoriesManager>().knownRepositories
    return if (repositories.size > 1) {
      repositories.map {
        object : DumbAwareAction(it.remote.remote.name + ": " + GithubUrlUtil.removeProtocolPrefix(it.remote.url)) {
          override fun actionPerformed(e: AnActionEvent) {
            actionPerformed(e, project, it)
          }
        }
      }.toTypedArray()
    }
    else AnAction.EMPTY_ARRAY
  }

  final override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    project.service<GHProjectRepositoriesManager>().knownRepositories.singleOrNull()?.let { actionPerformed(e, project, it) }
  }

  abstract fun actionPerformed(e: AnActionEvent, project: Project, repository: GHGitRepositoryMapping)

  final override fun canBePerformed(context: DataContext): Boolean {
    val project = context.getData(CommonDataKeys.PROJECT) ?: return false
    return project.service<GHProjectRepositoriesManager>().knownRepositories.size == 1
  }

  final override fun isPopup(): Boolean = true
  final override fun disableIfNoVisibleChildren(): Boolean = false
}