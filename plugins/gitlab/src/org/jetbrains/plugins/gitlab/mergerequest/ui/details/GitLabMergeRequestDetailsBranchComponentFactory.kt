// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.hover.addHoverAndPressStateListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JLabelUtil
import com.intellij.util.ui.UIUtil
import git4idea.repo.GitRepository
import icons.CollaborationToolsIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestCheckoutRemoteBranchAction
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestsActionKeys
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsInfoViewModel
import java.awt.BorderLayout
import java.awt.Cursor
import javax.swing.JComponent
import javax.swing.JLabel

internal object GitLabMergeRequestDetailsBranchComponentFactory {
  private const val POPUP_OFFSET = 8
  private const val COMPONENTS_GAP = 4
  private const val BRANCH_HOVER_BORDER = 2

  fun create(project: Project,
             scope: CoroutineScope,
             detailsInfoVm: GitLabMergeRequestDetailsInfoViewModel,
             repository: GitRepository): JComponent {
    val targetBranchComponent = createBranchLabel(scope, detailsInfoVm.targetBranch)
    val sourceBranchComponent = createBranchLabel(scope, detailsInfoVm.sourceBranch).apply {
      border = JBUI.Borders.empty(BRANCH_HOVER_BORDER)
      addHoverAndPressStateListener(comp = this, pressedStateCallback = { branchLabel, isPressed ->
        if (!isPressed) return@addHoverAndPressStateListener
        branchLabel as JComponent
        val popup = branchActionPopup(project, repository, detailsInfoVm.mergeRequest)
        val point = RelativePoint.getSouthWestOf(branchLabel).originalPoint
        popup.component.show(branchLabel, point.x, point.y + POPUP_OFFSET)
      })
    }.let { branchLabel ->
      RoundedPanel(BorderLayout()).apply {
        UIUtil.setCursor(this, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
        border = JBUI.Borders.empty()
        background = UIUtil.getListBackground()
        addHoverAndPressStateListener(comp = this, hoveredStateCallback = { component, isHovered ->
          component.background = if (isHovered) JBUI.CurrentTheme.ActionButton.hoverBackground() else UIUtil.getListBackground()
        })
        add(branchLabel, BorderLayout.CENTER)
      }
    }

    return HorizontalListPanel(COMPONENTS_GAP).apply {
      add(targetBranchComponent)
      add(JLabel(AllIcons.Chooser.Left))
      add(sourceBranchComponent)
    }
  }

  private fun createBranchLabel(scope: CoroutineScope, branchName: Flow<@NlsContexts.Label String>): JBLabel {
    return JBLabel(CollaborationToolsIcons.Review.Branch).apply {
      JLabelUtil.setTrimOverflow(this, true)
      bindTextIn(scope, branchName)
    }.also {
      CollaborationToolsUIUtil.overrideUIDependentProperty(it) {
        foreground = CurrentBranchComponent.TEXT_COLOR
      }
    }
  }

  private fun branchActionPopup(project: Project, repository: GitRepository, mergeRequest: GitLabMergeRequest): ActionPopupMenu {
    val action = GitLabMergeRequestCheckoutRemoteBranchAction()
    val group = DefaultActionGroup(action)
    val popupMenu = ActionManager.getInstance().createActionPopupMenu("github.review.details", group)
    popupMenu.setDataContext {
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(GitLabMergeRequestsActionKeys.GIT_REPOSITORY, repository)
        .add(GitLabMergeRequestsActionKeys.MERGE_REQUEST, mergeRequest)
        .build()
    }

    return popupMenu
  }
}