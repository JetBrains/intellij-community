// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.actions.VcsQuickActionsToolbarPopup
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBInsets
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryManager
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Git implementation of the quick popup action
 */
@Service
class GitQuickActionsToolbarService {
  var gitMappingUpdated = false

  companion object {
    fun getInstance(project: Project): GitQuickActionsToolbarService = project.getService(GitQuickActionsToolbarService::class.java)
  }
}

internal class GitQuickActionsToolbarPopup : VcsQuickActionsToolbarPopup() {

  private inner class MyActionButtonWithText(
    action: AnAction,
    presentation: Presentation,
    place: String,
  ) : ActionButtonWithText(action, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {

    override fun getInactiveTextColor(): Color = foreground

    override fun getInsets(): Insets = JBInsets(0, 0, 0, 0)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return MyActionButtonWithText(this, presentation, place)
  }

  override fun update(e: AnActionEvent) {
    e.project ?: return
    val presentation = e.presentation
    val instance = GitQuickActionsToolbarService.getInstance(e.project!!)
    if (!instance.gitMappingUpdated) {
      presentation.isEnabledAndVisible = false
      return
    }
    else {
      presentation.isEnabledAndVisible = true
    }

    val repo = GitRepositoryManager.getInstance(e.project!!).repositories.isNotEmpty()

    presentation.icon = if (repo) {
      AllIcons.Actions.More.toSize(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    }
    else {
      AllIcons.Vcs.BranchNode
    }

    presentation.text = if (repo) {
      ""
    }
    else {
      GitBundle.message("toolbar.vcs.show.more.actions") + " "
    }
  }

  class MyGitRepositoryListener(val project: Project) : VcsRepositoryMappingListener {
    override fun mappingChanged() {
      GitQuickActionsToolbarService.getInstance(project).gitMappingUpdated = true
    }
  }

  private fun Icon.toSize(dimension: Dimension): Icon {
    return if (iconWidth < dimension.width) {
      IconUtil.toSize(
        this,
        dimension.width,
        dimension.height,
      )
    }
    else {
      this
    }
  }
}