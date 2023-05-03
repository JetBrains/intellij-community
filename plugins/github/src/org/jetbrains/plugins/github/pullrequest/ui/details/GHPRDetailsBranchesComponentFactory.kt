// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeTooltip
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.ClientProperty
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.hover.addHoverAndPressStateListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JLabelUtil
import com.intellij.util.ui.UIUtil
import git4idea.repo.GitRepository
import icons.CollaborationToolsIcons
import icons.DvcsImplIcons
import org.jetbrains.plugins.github.GithubIcons
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.action.GHPRCheckoutRemoteBranchAction
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesModel
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel

internal object GHPRDetailsBranchesComponentFactory {
  private const val BRANCHES_GAP = 4
  private const val POPUP_OFFSET = 8
  private const val BRANCH_HOVER_BORDER = 2

  fun create(
    project: Project,
    dataProvider: GHPRDataProvider,
    repositoryDataService: GHPRRepositoryDataService,
    model: GHPRBranchesModel
  ): JComponent {
    val arrowLabel = JLabel(AllIcons.Chooser.Left)
    val from = createLabel().apply {
      border = JBUI.Borders.empty(BRANCH_HOVER_BORDER)
      JLabelUtil.setTrimOverflow(this, true)
      addHoverAndPressStateListener(comp = this, pressedStateCallback = { branchLabel, isPressed ->
        if (!isPressed) return@addHoverAndPressStateListener
        branchLabel as JComponent
        val popup = branchActionPopup(project, dataProvider, repositoryDataService.remoteCoordinates.repository)
        val point = RelativePoint.getSouthWestOf(branchLabel).originalPoint
        popup.component.show(branchLabel, point.x, point.y + POPUP_OFFSET)
      })
    }
    val to = createLabel().apply {
      JLabelUtil.setTrimOverflow(this, true)
    }

    Controller(model, from, to)
    val activatableFromBranch = RoundedPanel(BorderLayout()).apply {
      UIUtil.setCursor(this, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
      border = JBUI.Borders.empty()
      background = UIUtil.getListBackground()
      addHoverAndPressStateListener(comp = this, hoveredStateCallback = { component, isHovered ->
        component.background = if (isHovered) JBUI.CurrentTheme.ActionButton.hoverBackground() else UIUtil.getListBackground()
      })
      add(from, BorderLayout.CENTER)
    }

    return HorizontalListPanel(BRANCHES_GAP).apply {
      add(to)
      add(arrowLabel)
      add(activatableFromBranch)
    }
  }

  private fun createLabel() = JBLabel(CollaborationToolsIcons.Review.Branch).also {
    CollaborationToolsUIUtil.overrideUIDependentProperty(it) {
      foreground = CurrentBranchComponent.TEXT_COLOR
    }
  }

  private fun branchActionPopup(project: Project, dataProvider: GHPRDataProvider, repository: GitRepository): ActionPopupMenu {
    val action = GHPRCheckoutRemoteBranchAction()
    val group = DefaultActionGroup(action)
    val popupMenu = ActionManager.getInstance().createActionPopupMenu("github.review.details", group)
    popupMenu.setDataContext {
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(GHPRActionKeys.GIT_REPOSITORY, repository)
        .add(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER, dataProvider)
        .build()
    }

    return popupMenu
  }

  private class Controller(private val model: GHPRBranchesModel,
                           private val from: JLabel,
                           private val to: JLabel) {

    val branchesTooltipFactory = GHPRBranchesTooltipFactory()

    init {
      branchesTooltipFactory.installTooltip(from)

      model.addAndInvokeChangeListener {
        updateBranchLabels()
      }
    }

    private fun updateBranchLabels() {
      val localRepository = model.localRepository
      val localBranch = with(localRepository.branches) { model.localBranch?.run(::findLocalBranch) }
      val remoteBranch = localBranch?.findTrackedBranch(localRepository)
      val currentBranchCheckedOut = localRepository.currentBranchName == localBranch?.name

      to.text = model.baseBranch
      from.text = model.headBranch
      from.icon = when {
        currentBranchCheckedOut -> DvcsImplIcons.CurrentBranchFavoriteLabel
        localBranch != null -> GithubIcons.LocalBranch
        else -> CollaborationToolsIcons.Review.Branch
      }

      branchesTooltipFactory.apply {
        isOnCurrentBranch = currentBranchCheckedOut
        prBranchName = model.headBranch
        localBranchName = localBranch?.name
        remoteBranchName = remoteBranch?.name
      }
    }
  }

  private class GHPRBranchesTooltipFactory(var isOnCurrentBranch: Boolean = false,
                                           var prBranchName: String = "",
                                           var localBranchName: String? = null,
                                           var remoteBranchName: String? = null) {
    fun installTooltip(label: JLabel) {
      label.addMouseMotionListener(object : MouseAdapter() {
        override fun mouseMoved(e: MouseEvent) {
          showTooltip(e)
        }
      })
    }

    private fun showTooltip(e: MouseEvent) {
      val point = e.point
      if (IdeTooltipManager.getInstance().hasCurrent()) {
        IdeTooltipManager.getInstance().hideCurrent(e)
      }

      val tooltip = IdeTooltip(e.component, point, Wrapper(createTooltip())).setPreferredPosition(Balloon.Position.below)
      IdeTooltipManager.getInstance().show(tooltip, false)
    }

    private fun createTooltip(): GHPRBranchesTooltip =
      GHPRBranchesTooltip(arrayListOf<BranchTooltipDescriptor>().apply {
        if (isOnCurrentBranch) add(BranchTooltipDescriptor.head())
        if (localBranchName != null) add(BranchTooltipDescriptor.localBranch(localBranchName!!))
        if (remoteBranchName != null) {
          add(BranchTooltipDescriptor.remoteBranch(remoteBranchName!!))
        }
        else {
          add(BranchTooltipDescriptor.prBranch(prBranchName))
        }
      })
  }
}