// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.dvcs.push.VcsPushAction
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.ui.laf.darcula.ui.ToolbarComboWidgetUiSizes
import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.update.CommonUpdateProjectAction
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.RunWidget.toolbarHeight
import com.intellij.util.ui.JBUI.insets
import com.intellij.util.ui.JBUI.size
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitIncomingOutgoingColors
import git4idea.branch.IncomingOutgoingState
import git4idea.branch.calcTooltip
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.toolbar.GitMainToolbar.showIncomingOutgoing
import icons.DvcsImplIcons
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Insets
import javax.swing.JComponent

class GitMainToolbarPushAction : VcsPushAction() {
  init {
    ActionUtil.copyFrom(this, "Vcs.Push")
    templatePresentation.putClientProperty(ActionUtil.COMPONENT_PROVIDER, ButtonWithCustomForeground(this, GitIncomingOutgoingColors.OUTGOING_FOREGROUND))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (!e.presentation.isVisible) return

    val project = e.project
    if (project == null || e.place != ActionPlaces.MAIN_TOOLBAR) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.icon = AllIcons.Vcs.Push
    e.presentation.text = ""
    e.presentation.putClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP, null)
    if (!showIncomingOutgoing) return

    val repository = getCurrentRepository(project, e.dataContext) ?: return
    e.presentation.icon = DvcsImplIcons.OutgoingPush

    val incomingOutgoingState = getIncomingOutgoingState(project, repository) ?: return
    val totalOutgoing = incomingOutgoingState.totalOutgoing()

    if (totalOutgoing != 0) {
      e.presentation.text = totalOutgoing.shrinkTo99()
    }

    val helpTooltip = HelpTooltip()
      .setTitle(GitBundle.message("MainToolbarQuickActions.Git.Push.text"))
      .setShortcut(KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("Vcs.Push")))
      .setDescription(incomingOutgoingState.calcTooltip())
    e.presentation.putClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP, helpTooltip)
  }
}

class GitMainToolbarUpdateProjectAction : CommonUpdateProjectAction() {
  init {
    ActionUtil.copyFrom(this, "Vcs.UpdateProject")
    templatePresentation.putClientProperty(ActionUtil.COMPONENT_PROVIDER, ButtonWithCustomForeground(this, GitIncomingOutgoingColors.INCOMING_FOREGROUND))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (!e.presentation.isVisible) return

    val project = e.project
    if (project == null || e.place != ActionPlaces.MAIN_TOOLBAR) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.icon = AllIcons.Actions.CheckOut
    e.presentation.text = ""
    e.presentation.putClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP, null)
    if (!showIncomingOutgoing) return

    val repository = getCurrentRepository(project, e.dataContext) ?: return
    e.presentation.icon = DvcsImplIcons.IncomingUpdate
    val incomingOutgoingState = getIncomingOutgoingState(project, repository) ?: return
    val totalIncoming = incomingOutgoingState.totalIncoming()

    if (totalIncoming != 0) {
      e.presentation.text = totalIncoming.shrinkTo99()
    }

    val helpTooltip = HelpTooltip()
      .setTitle(GitBundle.message("MainToolbarQuickActions.Git.Update.text"))
      .setShortcut(KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("Vcs.UpdateProject")))
      .setDescription(incomingOutgoingState.calcTooltip())
    e.presentation.putClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP, helpTooltip)
  }
}

private class ButtonWithCustomForeground(
  private val action: AnAction,
  private val fg: Color,
) : CustomComponentAction {
  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    component.foreground = fg
    component.border = JBUI.Borders.empty(1, 0)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : ActionButtonWithText(action, presentation, place, {
      size(16, toolbarHeight())
    }) {
      override fun iconTextSpace(): Int {
        if (this.text.isBlank()) return 0
        return ToolbarComboWidgetUiSizes.gapAfterLeftIcons
      }

      override fun getMargins(): Insets = insets(0, 7)
    }
  }
}

private fun getCurrentRepository(project: Project, dataContext: DataContext): GitRepository? {
  val state = GitToolbarWidgetAction.getWidgetState(project, dataContext)
  if (state !is GitToolbarWidgetAction.GitWidgetState.Repo) return null
  return state.repository
}

private fun getIncomingOutgoingState(project: Project, repository: GitRepository): IncomingOutgoingState? {
  val currentBranch = repository.currentBranch ?: return null
  val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project)
  return incomingOutgoingManager.getIncomingOutgoingState(repository, currentBranch)
}

private fun Int.shrinkTo99(): @NlsSafe String = if (this > 99) "99+" else this.toString()

@ApiStatus.Internal
internal object GitMainToolbar {
  val showIncomingOutgoing: Boolean // todo: registry, setting or something else
    get() = !AppMode.isRemoteDevHost()
}
