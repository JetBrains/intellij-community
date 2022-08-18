// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.actions.VcsQuickActionsToolbarPopup
import com.intellij.util.IconUtil
import git4idea.GitVcs
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Git implementation of the quick popup action
 */
@Service
class GitQuickActionsToolbarService {
  var gitMappingInitialized = false
    private set

  fun initializationComplete() {
    gitMappingInitialized = true
  }

  companion object {
    fun getInstance(project: Project): GitQuickActionsToolbarService = project.getService(GitQuickActionsToolbarService::class.java)
  }
}

internal class GitQuickActionsToolbarPopup : VcsQuickActionsToolbarPopup() {

  override fun getName(project: Project): String {
    return GitVcs.getInstance(project).name
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return MyActionButtonWithText(this, presentation.apply { text = "" }, place)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val presentation = e.presentation

    if (!updateVcs(project, e)) return
    val instance = GitQuickActionsToolbarService.getInstance(project!!)
    if (!instance.gitMappingInitialized) {
      presentation.isEnabledAndVisible = false
      return
    }

    presentation.isEnabledAndVisible = true
    presentation.icon = AllIcons.Actions.More.toSize(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  }

  class MyGitRepositoryListener(val project: Project) : VcsRepositoryMappingListener {
    override fun mappingChanged() {
      GitQuickActionsToolbarService.getInstance(project).initializationComplete()
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