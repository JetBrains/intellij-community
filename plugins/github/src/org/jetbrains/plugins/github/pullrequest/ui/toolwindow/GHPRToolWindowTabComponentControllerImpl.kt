// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import git4idea.remote.hosting.knownRepositories
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.IJSwingUtilities
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateComponentHolder
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import javax.swing.JComponent

internal class GHPRToolWindowTabComponentControllerImpl(
  private val project: Project,
  private val repositoryManager: GHHostedRepositoriesManager,
  private val projectSettings: GithubPullRequestsProjectUISettings,
  private val dataContext: GHPRDataContext,
  private val wrapper: Wrapper,
  private val parentDisposable: Disposable,
  initialView: GHPRToolWindowViewType,
  private val onTitleChange: (@Nls String) -> Unit
) : GHPRToolWindowTabComponentController {

  private val listComponent by lazy { createListPanel() }
  private val createComponentHolder = ClearableLazyValue.create {
    GHPRCreateComponentHolder(ActionManager.getInstance(), project, projectSettings, repositoryManager, dataContext, this,
                              parentDisposable)
  }

  override lateinit var currentView: GHPRToolWindowViewType
  private var currentDisposable: Disposable? = null
  private var currentPullRequest: GHPRIdentifier? = null

  init {
    when (initialView) {
      GHPRToolWindowViewType.NEW -> createPullRequest(false)
      else -> viewList(false)
    }

    DataManager.registerDataProvider(wrapper) { dataId ->
      when {
        GHPRActionKeys.PULL_REQUESTS_TAB_CONTROLLER.`is`(dataId) -> this
        else -> null
      }
    }
  }

  override fun createPullRequest(requestFocus: Boolean) {
    val allRepos = repositoryManager.knownRepositories.map(GHGitRepositoryMapping::repository)
    onTitleChange(GithubBundle.message("tab.title.pull.requests.new",
                                       GHUIUtil.getRepositoryDisplayName(allRepos,
                                                                         dataContext.repositoryDataService.repositoryCoordinates)))
    currentDisposable?.let { Disposer.dispose(it) }
    currentPullRequest = null
    currentView = GHPRToolWindowViewType.NEW
    wrapper.setContent(createComponentHolder.value.component)
    IJSwingUtilities.updateComponentTreeUI(wrapper)
    if (requestFocus) {
      CollaborationToolsUIUtil.focusPanel(wrapper.targetComponent)
    }
  }

  override fun resetNewPullRequestView() {
    createComponentHolder.value.resetModel()
  }

  override fun viewList(requestFocus: Boolean) {
    val allRepos = repositoryManager.knownRepositories.map(GHGitRepositoryMapping::repository)
    onTitleChange(GithubBundle.message("tab.title.pull.requests.at",
                                       GHUIUtil.getRepositoryDisplayName(allRepos,
                                                                         dataContext.repositoryDataService.repositoryCoordinates)))
    currentDisposable?.let { Disposer.dispose(it) }
    currentPullRequest = null
    currentView = GHPRToolWindowViewType.LIST
    wrapper.setContent(listComponent)
    IJSwingUtilities.updateComponentTreeUI(wrapper)
    if (requestFocus) {
      CollaborationToolsUIUtil.focusPanel(wrapper.targetComponent)
    }
  }

  override fun refreshList() {
    dataContext.listLoader.reset()
    dataContext.repositoryDataService.resetData()
  }

  override fun viewPullRequest(id: GHPRIdentifier, requestFocus: Boolean, onShown: ((GHPRViewComponentController?) -> Unit)?) {
    onTitleChange(GithubBundle.message("pull.request.num", id.number))
    if (currentPullRequest != id) {
      currentDisposable?.let { Disposer.dispose(it) }
      currentDisposable = Disposer.newDisposable("Pull request component disposable").also {
        Disposer.register(parentDisposable, it)
      }
      currentPullRequest = id
      currentView = GHPRToolWindowViewType.DETAILS
      val pullRequestComponent = GHPRViewComponentFactory(ActionManager.getInstance(), project, dataContext, this, id,
                                                          currentDisposable!!)
        .create()
      wrapper.setContent(pullRequestComponent)
      wrapper.repaint()
    }
    if (onShown != null) onShown(UIUtil.getClientProperty(wrapper.targetComponent, GHPRViewComponentController.KEY))
    if (requestFocus) {
      CollaborationToolsUIUtil.focusPanel(wrapper.targetComponent)
    }
  }

  override fun openPullRequestTimeline(id: GHPRIdentifier, requestFocus: Boolean) =
    dataContext.filesManager.createAndOpenTimelineFile(id, requestFocus)

  override fun openPullRequestDiff(id: GHPRIdentifier, requestFocus: Boolean) =
    dataContext.filesManager.createAndOpenDiffFile(id, requestFocus)

  private fun createListPanel(): JComponent {
    val listLoader = dataContext.listLoader
    val listModel = CollectionListModel(listLoader.loadedData)
    listLoader.addDataListener(parentDisposable, object : GHListLoader.ListDataListener {
      override fun onDataAdded(startIdx: Int) {
        val loadedData = listLoader.loadedData
        listModel.add(loadedData.subList(startIdx, loadedData.size))
      }

      override fun onDataUpdated(idx: Int) = listModel.setElementAt(listLoader.loadedData[idx], idx)
      override fun onDataRemoved(data: Any) {
        (data as? GHPullRequestShort)?.let { listModel.remove(it) }
      }

      override fun onAllDataRemoved() = listModel.removeAll()
    })

    val list = GHPRListComponentFactory(listModel).create(dataContext.avatarIconsProvider)

    return GHPRListPanelFactory(project,
                                dataContext.repositoryDataService,
                                dataContext.securityService,
                                dataContext.listLoader,
                                dataContext.listUpdatesChecker,
                                dataContext.securityService.account,
                                parentDisposable)
      .create(list, dataContext.avatarIconsProvider)
  }
}