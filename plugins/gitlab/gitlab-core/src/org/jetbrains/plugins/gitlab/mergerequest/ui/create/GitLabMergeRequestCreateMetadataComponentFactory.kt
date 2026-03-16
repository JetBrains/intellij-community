// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.create

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.LabeledListComponentsFactory
import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.codereview.list.search.ShowDirection
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.IncrementallyComputedValue
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabLabel
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.model.GitLabMergeRequestCreateViewModel
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestChoosersUtil
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

internal object GitLabMergeRequestCreateMetadataComponentFactory {
  fun createReviewersListPanelHandle(
    vm: GitLabMergeRequestCreateViewModel,
  ): Pair<JComponent, JComponent> {
    val label = LabeledListComponentsFactory.createLabelPanel(
      vm.reviewers.mapState { it.isEmpty() },
      GitLabBundle.message("merge.request.create.no.reviewers"),
      GitLabBundle.message("merge.request.create.reviewers")
    )

    val list = LabeledListComponentsFactory.createListPanel(
      vm.reviewers,
      { comp, _ -> chooseReviewers(comp, vm, vm.avatarIconProvider) },
      { vm.clearReviewers() },
      { UserLabel(it, vm.avatarIconProvider) }
    )

    return label to list
  }

  private suspend fun chooseReviewers(
    parentComponent: JComponent,
    vm: GitLabMergeRequestCreateViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  ) {
    val allowsMultiple = vm.allowsMultipleReviewers.value
    val currentReviewers = vm.reviewers.value
    val potentialReviewers = vm.projectMembers
    val newReviewers = chooseUsers(parentComponent, allowsMultiple, currentReviewers, potentialReviewers, avatarIconsProvider)
    vm.setReviewers(newReviewers)
  }

  fun createAssigneesListPanelHandle(
    vm: GitLabMergeRequestCreateViewModel,
  ): Pair<JComponent, JComponent> {
    val label = LabeledListComponentsFactory.createLabelPanel(
      vm.assignees.mapState { it.isEmpty() },
      GitLabBundle.message("merge.request.create.no.assignees"),
      GitLabBundle.message("merge.request.create.assignees")
    )

    val list = LabeledListComponentsFactory.createListPanel(
      vm.assignees,
      { comp, _ -> chooseAssignees(comp, vm, vm.avatarIconProvider) },
      { vm.clearAssignees() },
      { UserLabel(it, vm.avatarIconProvider) }
    )

    return label to list
  }

  private suspend fun chooseAssignees(
    parentComponent: JComponent,
    vm: GitLabMergeRequestCreateViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  ) {
    val allowsMultiple = vm.allowsMultipleAssignees.value
    val currentAssignees = vm.assignees.value
    val potentialAssignees = vm.projectMembers
    val newAssignees = chooseUsers(parentComponent, allowsMultiple, currentAssignees, potentialAssignees, avatarIconsProvider)
    vm.setAssignees(newAssignees)
  }

  private suspend fun chooseUsers(
    parentComponent: JComponent,
    allowsMultiple: Boolean,
    currentAssignees: List<GitLabUserDTO>,
    potentialAssignees: StateFlow<IncrementallyComputedValue<List<GitLabUserDTO>>>,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  ): List<GitLabUserDTO> {
    val point = RelativePoint.getNorthEastOf(parentComponent)
    val newList = if (allowsMultiple) {
      GitLabMergeRequestChoosersUtil.chooseUsers(point, currentAssignees, potentialAssignees, avatarIconsProvider, ShowDirection.ABOVE)
    }
    else {
      val assignee = GitLabMergeRequestChoosersUtil.chooseUser(point, potentialAssignees, avatarIconsProvider, ShowDirection.ABOVE)
      listOfNotNull(assignee)
    }
    return newList
  }

  fun createLabelsListPanelHandle(
    vm: GitLabMergeRequestCreateViewModel,
  ): Pair<JComponent, JComponent> {
    val label = LabeledListComponentsFactory.createLabelPanel(
      vm.labels.mapState { it.isEmpty() },
      GitLabBundle.message("merge.request.create.no.labels"),
      GitLabBundle.message("merge.request.create.labels")
    )

    val list = LabeledListComponentsFactory.createListPanel(
      vm.labels,
      { comp, _ -> chooseLabels(comp, vm) },
      { vm.clearLabels() },
      { LabelLabel(it) }
    )

    return label to list
  }

  private suspend fun chooseLabels(
    parentComponent: JComponent,
    vm: GitLabMergeRequestCreateViewModel,
  ) {
    val point = RelativePoint.getNorthEastOf(parentComponent)
    val currentLabels = vm.labels.value
    val potentialLabels = vm.projectLabels
    val newLabels = GitLabMergeRequestChoosersUtil.chooseLabels(point, currentLabels, potentialLabels, ShowDirection.ABOVE)
    vm.setLabels(newLabels)
  }
}

@Suppress("FunctionName")
private fun UserLabel(user: GitLabUserDTO, avatarIconsProvider: IconsProvider<GitLabUserDTO>) =
  JLabel(user.name, avatarIconsProvider.getIcon(user, Avatar.Sizes.BASE), SwingConstants.LEFT).apply {
    border = JBUI.Borders.empty(0, UIUtil.DEFAULT_HGAP / 2)
  }

@Suppress("FunctionName")
private fun LabelLabel(label: GitLabLabel): JComponent {
  val background = CollaborationToolsUIUtil.getLabelBackground(label.colorHex)
  val foreground = CollaborationToolsUIUtil.getLabelForeground(background)
  return CollaborationToolsUIUtil.createTagLabel(label.title, foreground, background, compact = false).apply {
    border = JBUI.Borders.empty(0, UIUtil.DEFAULT_HGAP / 2)
  }
}
