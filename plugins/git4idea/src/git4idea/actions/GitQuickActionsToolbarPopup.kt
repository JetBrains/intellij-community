// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.icons.AllIcons
import com.intellij.ide.navigationToolbar.experimental.NewToolbarPaneListener
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.actions.VcsQuickActionsToolbarPopup
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBInsets
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Git implementation of the quick popup action
 */
internal class GitQuickActionsToolbarPopup : VcsQuickActionsToolbarPopup() {

  init {
    templatePresentation.text = GitBundle.message("action.Vcs.ShowMoreActions.text")
  }

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
    val repo = e.project?.let { GitBranchUtil.getCurrentRepository(it) }
    val noRepo = repo == null

    val presentation = e.presentation
    presentation.icon = if (noRepo) {
      AllIcons.Vcs.BranchNode
    }
    else {
      templatePresentation.icon.toSize(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    }

    presentation.text = if (noRepo) {
      templatePresentation.text + " "
    }
    else {
      ""
    }
  }

  internal class MyGitRepositoryListener(private val project: Project) : VcsRepositoryMappingListener {

    override fun mappingChanged() {
      ApplicationManager.getApplication().invokeLater(Runnable {
        project.messageBus
          .syncPublisher(NewToolbarPaneListener.TOPIC)
          .stateChanged()
      }, project.disposed)
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