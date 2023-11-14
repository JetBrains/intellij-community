// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.toolwindow.ReviewTabsComponentFactory
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.yield
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.ui.list.GHPRListComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.list.GHPRListPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.selector.GHRepositoryAndAccountSelectorComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateComponentHolder
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowTabViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowViewModel
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import java.awt.BorderLayout
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListModel
import javax.swing.event.ListDataListener

internal class GHPRToolWindowTabComponentFactory(
  private val project: Project,
  private val vm: GHPRToolWindowViewModel
) : ReviewTabsComponentFactory<GHPRToolWindowTabViewModel, GHPRToolWindowProjectViewModel> {

  override fun createEmptyTabContent(cs: CoroutineScope): JComponent {
    val selector = GHRepositoryAndAccountSelectorComponentFactory(project, vm.selectorVm, service<GHAccountManager>()).create(cs)
    return JPanel(BorderLayout()).apply {
      background = UIUtil.getListBackground()
      add(selector, BorderLayout.NORTH)
    }
  }

  override fun createReviewListComponent(cs: CoroutineScope, projectVm: GHPRToolWindowProjectViewModel): JComponent {
    val listVm = projectVm.listVm
    val listModel = cs.scopedDelegatingListModel(listVm.listModel)
    val list = GHPRListComponentFactory(listModel).create(listVm.avatarIconsProvider)

    GHPRStatisticsCollector.logListOpened(project)
    return GHPRListPanelFactory(project, listVm)
      .create(cs, list, listVm.avatarIconsProvider)
  }

  private fun <T> CoroutineScope.scopedDelegatingListModel(delegate: ListModel<T>) =
    object : ListModel<T> by delegate {
      private val listeners = CopyOnWriteArrayList<ListDataListener>()

      init {
        awaitCancellationAndInvoke {
          listeners.forEach {
            delegate.removeListDataListener(it)
          }
        }
      }

      override fun addListDataListener(l: ListDataListener) {
        listeners.add(l)
        delegate.addListDataListener(l)
      }

      override fun removeListDataListener(l: ListDataListener) {
        delegate.addListDataListener(l)
        listeners.remove(l)
      }
    }

  override fun createTabComponent(cs: CoroutineScope,
                                  projectVm: GHPRToolWindowProjectViewModel,
                                  tabVm: GHPRToolWindowTabViewModel): JComponent {
    return when (tabVm) {
      is GHPRToolWindowTabViewModel.PullRequest -> cs.createPullRequestComponent(tabVm)
      is GHPRToolWindowTabViewModel.NewPullRequest -> cs.createNewPullRequestComponent(projectVm, tabVm)
    }
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

  private fun CoroutineScope.createNewPullRequestComponent(projectVm: GHPRToolWindowProjectViewModel,
                                                           tabVm: GHPRToolWindowTabViewModel.NewPullRequest): JComponent {
    val repositoriesManager = project.service<GHHostedRepositoriesManager>()
    val settings = GithubPullRequestsProjectUISettings.getInstance(project)
    return GHPRCreateComponentHolder(ActionManager.getInstance(), project, settings, repositoriesManager, projectVm.dataContext,
                                     projectVm,
                                     nestedDisposable()).component.also { comp ->
      launchNow {
        tabVm.focusRequests.collect {
          yield()
          CollaborationToolsUIUtil.focusPanel(comp)
        }
      }
    }
  }
}