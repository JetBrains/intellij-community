// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.toolwindow.ReviewTabsComponentFactory
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.yield
import org.jetbrains.plugins.github.authentication.GHLoginSource
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.list.GHPRListPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.selector.GHRepositoryAndAccountSelectorComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowTabViewModel
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class GHPRToolWindowTabComponentFactory(
  private val project: Project,
  private val vm: GHPRProjectViewModel
) : ReviewTabsComponentFactory<GHPRToolWindowTabViewModel, GHPRToolWindowProjectViewModel> {

  override fun createEmptyTabContent(cs: CoroutineScope): JComponent {
    val selector = GHRepositoryAndAccountSelectorComponentFactory(project, vm.selectorVm, service<GHAccountManager>(), GHLoginSource.PR_TW).create(cs)
    return JPanel(BorderLayout()).apply {
      background = UIUtil.getListBackground()
      add(selector, BorderLayout.NORTH)
    }
  }

  override fun createReviewListComponent(cs: CoroutineScope, projectVm: GHPRToolWindowProjectViewModel): JComponent {
    return GHPRListPanelFactory.create(project, cs, projectVm.dataContext, projectVm.listVm).apply {
      border = JBUI.Borders.emptyTop(8)
    }
  }

  override fun createTabComponent(cs: CoroutineScope, projectVm: GHPRToolWindowProjectViewModel, tabVm: GHPRToolWindowTabViewModel): JComponent =
    when (tabVm) {
      is GHPRToolWindowTabViewModel.PullRequest -> cs.createPullRequestComponent(tabVm)
      is GHPRToolWindowTabViewModel.NewPullRequest -> cs.createNewPullRequestComponent(tabVm)
    }

  private fun CoroutineScope.createPullRequestComponent(tabVm: GHPRToolWindowTabViewModel.PullRequest): JComponent =
    GHPRViewComponentFactory(ActionManager.getInstance(), project, tabVm.infoVm).create(this).also { comp ->
      launchNow {
        tabVm.focusRequests.collect {
          yield()
          CollaborationToolsUIUtil.focusPanel(comp)
        }
      }
    }

  private fun CoroutineScope.createNewPullRequestComponent(tabVm: GHPRToolWindowTabViewModel.NewPullRequest): JComponent =
    GHPRCreateComponentFactory.createIn(this, tabVm.createVm).also { comp ->
      launchNow {
        tabVm.focusRequests.collect {
          yield()
          CollaborationToolsUIUtil.focusPanel(comp)
        }
      }
    }
}