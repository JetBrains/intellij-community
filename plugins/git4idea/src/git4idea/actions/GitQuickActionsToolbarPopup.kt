// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.icons.AllIcons
import com.intellij.ide.navigationToolbar.experimental.NewToolbarPaneListener
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vcs.actions.VcsQuickActionsToolbarPopup
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBInsets
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryManager
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import javax.swing.JComponent

/**
 * Git implementation of the quick popup action
 */
internal class GitQuickActionsToolbarPopup : VcsQuickActionsToolbarPopup() {
  private class MyActionButtonWithText(action: AnAction,
                                       presentation: Presentation,
                                       place: String,
                                       minimumSize: Dimension) : ActionButtonWithText(action, presentation, place, minimumSize) {
    public override fun getText(): @NlsActions.ActionText String {
      val iconWithText = myPresentation.getClientProperty(KEY_ICON_WITH_TEXT)
      return if (iconWithText == true) {
        super.getText() + " "
      }
      else {
        ""
      }
    }

    override fun getInactiveTextColor(): Color {
      return foreground
    }

    override fun getInsets(): Insets {
      return JBInsets(0, 0, 0, 0)
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return MyActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  }

  override fun update(e: AnActionEvent) {
    if (e.place != ActionPlaces.MAIN_TOOLBAR) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val project = e.project
    val presentation = e.presentation
    val repo = project?.let { GitBranchUtil.getCurrentRepository(it) }
    if (repo == null) {
      presentation.putClientProperty(KEY_ICON_WITH_TEXT, true)
      presentation.icon = AllIcons.Vcs.BranchNode
    }
    else {
      var icon = AllIcons.Actions.More
      if (icon.iconWidth < ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width) {
        icon = IconUtil.toSize(icon, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width,
                               ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height)
      }
      presentation.putClientProperty(KEY_ICON_WITH_TEXT, false)
      presentation.icon = icon
    }
  }

  companion object {
    private val KEY_ICON_WITH_TEXT = Key.create<Boolean>("KEY_ICON_WITH_TEXT")
  }

  class MyGitRepositoryListener(val project: Project) : VcsRepositoryMappingListener {
    override fun mappingChanged() {
      invokeLater {
        project.messageBus.syncPublisher(NewToolbarPaneListener.TOPIC).stateChanged()
      }
    }
  }
}