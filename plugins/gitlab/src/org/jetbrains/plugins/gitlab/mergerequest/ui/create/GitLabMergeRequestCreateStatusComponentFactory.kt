// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.create

import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.icons.AllIcons
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JLabelUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.model.GitLabMergeRequestCreateViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.model.MergeRequestRequirementsErrorType
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent
import javax.swing.JLabel

internal object GitLabMergeRequestCreateStatusComponentFactory {
  private const val STATUS_COMPONENT_BORDER = 5

  fun create(cs: CoroutineScope, createVm: GitLabMergeRequestCreateViewModel): JComponent {
    return VerticalListPanel().apply {
      add(creatingErrorLabel(cs, createVm))
      add(requirementsErrorLabel(cs, createVm))
      add(alreadyExistStatusLabel(cs, createVm))
      add(creatingStatusLabel(cs, createVm))
    }
  }

  private fun creatingErrorLabel(cs: CoroutineScope, createVm: GitLabMergeRequestCreateViewModel): JComponent {
    return JLabel(AllIcons.Ide.FatalError).apply {
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      text = GitLabBundle.message("merge.request.create.error.creating")
      JLabelUtil.setTrimOverflow(this, true)
      bindVisibilityIn(cs, createVm.reviewCreatingError.map { it != null })
    }
  }

  private fun requirementsErrorLabel(cs: CoroutineScope, createVm: GitLabMergeRequestCreateViewModel): JComponent {
    return JLabel().apply {
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = AllIcons.General.Error
      JLabelUtil.setTrimOverflow(this, true)
      val reviewRequirementsErrorFlow = createVm.reviewRequirementsErrorState
      bindVisibilityIn(cs, reviewRequirementsErrorFlow.map { it != null })
      bindTextIn(cs, reviewRequirementsErrorFlow.filterNotNull().map { type ->
        val branchState = createVm.branchState.filterNotNull().first()
        val headBranch = branchState.headBranch
        val baseBranch = branchState.baseBranch
        when (type) {
          MergeRequestRequirementsErrorType.WRONG_DIRECTION -> {
            GitLabBundle.message("merge.request.create.error.direction", headBranch.name, baseBranch.name)
          }
          MergeRequestRequirementsErrorType.NO_CHANGES -> {
            GitLabBundle.message("merge.request.create.error.no.changes", baseBranch.name, headBranch.name)
          }
          MergeRequestRequirementsErrorType.PROTECTED_BRANCH -> {
            GitLabBundle.message("merge.request.create.error.protected.branch")
          }
        }
      })
    }
  }

  private fun alreadyExistStatusLabel(cs: CoroutineScope, createVm: GitLabMergeRequestCreateViewModel): JComponent {
    val iconLabel = JLabel(AllIcons.Ide.FatalError).apply {
      text = GitLabBundle.message("merge.request.create.status.already.exists")
      JLabelUtil.setTrimOverflow(this, true)
    }
    val linkLabel = ActionLink(GitLabBundle.message("merge.request.create.status.already.exists.view")) {
      cs.launch {
        val mrIid = createVm.existingMergeRequest.first() ?: error("Merge Request on current branch was not found")
        createVm.openReviewTabAction(mrIid)
      }
    }

    return HorizontalListPanel(gap = 4).apply {
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      bindVisibilityIn(cs, createVm.existingMergeRequest.map { it != null })
      add(iconLabel)
      add(linkLabel)
    }
  }

  private fun creatingStatusLabel(cs: CoroutineScope, createVm: GitLabMergeRequestCreateViewModel): JComponent {
    return LoadingLabel().apply {
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      val text = combine(createVm.isBusy, createVm.creatingProgressText) { isBusy, progressText ->
        if (!isBusy || progressText == null) return@combine GitLabBundle.message("merge.request.create.progress.text")
        progressText
      }
      bindVisibilityIn(cs, createVm.isBusy)
      bindTextIn(cs, text)
    }
  }
}