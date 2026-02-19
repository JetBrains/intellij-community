// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.ui.toolwindow.ReviewTabsComponentFactory
import com.intellij.collaboration.ui.util.bindChildIn
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginSource
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.GitLabMergeRequestCreateComponentFactory
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.GitLabMergeRequestDetailsComponentFactory
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsPanelFactory
import org.jetbrains.plugins.gitlab.mergerequest.ui.selector.GitLabMergeRequestSelectorsComponentFactory
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabReviewTabViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowConnectedProjectViewModel
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import org.jetbrains.plugins.gitlab.util.GitLabStatistics.ToolWindowOpenTabActionPlace
import org.jetbrains.plugins.gitlab.util.GitLabStatistics.ToolWindowTabType
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class GitLabReviewTabComponentFactory(
  private val project: Project,
  private val vm: GitLabProjectViewModel,
) : ReviewTabsComponentFactory<GitLabReviewTabViewModel, GitLabToolWindowConnectedProjectViewModel> {

  override fun createReviewListComponent(
    cs: CoroutineScope,
    projectVm: GitLabToolWindowConnectedProjectViewModel,
  ): JComponent {
    GitLabStatistics.logTwTabOpened(project, ToolWindowTabType.LIST, ToolWindowOpenTabActionPlace.TOOLWINDOW)
    return GitLabMergeRequestsPanelFactory.create(cs, projectVm.accountVm, projectVm.listVm).apply {
      border = JBUI.Borders.emptyTop(8)
    }
  }

  override fun createTabComponent(
    cs: CoroutineScope,
    projectVm: GitLabToolWindowConnectedProjectViewModel,
    tabVm: GitLabReviewTabViewModel
  ): JComponent {
    return when (tabVm) {
      is GitLabReviewTabViewModel.Details -> {
        createReviewDetailsComponent(cs, projectVm, tabVm.detailsVm).also {
          tabVm.detailsVm.apply {
            refreshData()
          }
        }
      }
      is GitLabReviewTabViewModel.CreateMergeRequest -> {
        GitLabMergeRequestCreateComponentFactory.create(project, cs, tabVm.createVm)
      }
    }
  }

  override fun createEmptyTabContent(cs: CoroutineScope): JComponent {
    GitLabStatistics.logTwTabOpened(project, ToolWindowTabType.SELECTOR, ToolWindowOpenTabActionPlace.TOOLWINDOW)
    return JPanel(BorderLayout()).apply {
      background = UIUtil.getListBackground()
      launchOnShow("SelectorsComponent") {
        bindChildIn(this, vm.selectorVm, BorderLayout.NORTH) { selectorVm ->
          if (selectorVm == null) return@bindChildIn null
          GitLabMergeRequestSelectorsComponentFactory.createSelectorsComponent(this, project, selectorVm, GitLabLoginSource.MR_TW)
        }
      }
    }
  }

  private fun createReviewDetailsComponent(
    cs: CoroutineScope,
    projectVm: GitLabToolWindowConnectedProjectViewModel,
    reviewDetailsVm: GitLabMergeRequestDetailsLoadingViewModel
  ): JComponent {
    val avatarIconsProvider = projectVm.avatarIconProvider
    return GitLabMergeRequestDetailsComponentFactory.createDetailsComponent(
      project, cs, reviewDetailsVm, projectVm.accountVm, avatarIconsProvider
    )
  }
}
