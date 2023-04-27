// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.ui.toolwindow.refreshReviewListOnSelection
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.ClientProperty
import com.intellij.ui.CollectionListModel
import com.intellij.ui.content.Content
import com.intellij.util.ui.UIUtil
import git4idea.remote.hosting.knownRepositories
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys.PULL_REQUESTS_LIST_CONTROLLER
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.list.GHPRListComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.list.GHPRListPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateComponentHolder
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import javax.swing.JComponent

internal class MultiTabGHPRToolWindowRepositoryContentController(
  private val project: Project,
  private val repositoryManager: GHHostedRepositoriesManager,
  private val projectSettings: GithubPullRequestsProjectUISettings,
  private val dataContext: GHPRDataContext,
  toolwindow: ToolWindow
) : GHPRToolWindowRepositoryContentController {
  private val contentManager = toolwindow.contentManager

  override val repository: GHRepositoryCoordinates = dataContext.repositoryDataService.repositoryCoordinates

  init {
    toolwindow.refreshReviewListOnSelection { content ->
      val dataContext = DataManager.getInstance().getDataContext(content.component)
      val tabsContentSelector = PULL_REQUESTS_LIST_CONTROLLER.getData(dataContext) ?: return@refreshReviewListOnSelection
      tabsContentSelector.refreshList()
    }
  }

  override fun createPullRequest(requestFocus: Boolean) {
    val content = contentManager.contents.find {
      it.type == TabType.New
    } ?: createAndAddNewPRContent()
    contentManager.setSelectedContent(content, requestFocus)
  }

  private fun createAndAddNewPRContent(): Content {
    val contentDisposable = Disposer.newDisposable()
    val allRepos = repositoryManager.knownRepositories.map(GHGitRepositoryMapping::repository)
    val title = GithubBundle.message("tab.title.pull.requests.new",
                                     GHUIUtil.getRepositoryDisplayName(allRepos,
                                                                       dataContext.repositoryDataService.repositoryCoordinates))

    val component = GHPRCreateComponentHolder(ActionManager.getInstance(), project, projectSettings, repositoryManager, dataContext,
                                              this,
                                              contentDisposable).component
    val content = contentManager.factory.createContent(component, title, false).apply {
      setDisposer(contentDisposable)
      isCloseable = true
      type = TabType.New
    }
    contentManager.addContent(content)
    return content
  }

  override fun resetNewPullRequestView() {
    val content = contentManager.contents.find {
      it.type == TabType.New
    } ?: return
    contentManager.removeContent(content, true)
  }

  override fun viewList(requestFocus: Boolean) {
    val content = contentManager.contents.find {
      it.type == TabType.List
    } ?: createAndAddListContent()
    contentManager.setSelectedContent(content, requestFocus)
  }

  private fun createAndAddListContent(): Content {
    val contentDisposable = Disposer.newDisposable()
    val allRepos = repositoryManager.knownRepositories.map(GHGitRepositoryMapping::repository)
    val title = GHUIUtil.getRepositoryDisplayName(allRepos, dataContext.repositoryDataService.repositoryCoordinates)

    val component = createListPanel(contentDisposable)
    val content = contentManager.factory.createContent(component, title, false).apply {
      isPinned = true
      isCloseable = false
      setDisposer(contentDisposable)
      type = TabType.List
    }
    contentManager.addContent(content)
    return content
  }

  private fun createListPanel(disposable: Disposable): JComponent {
    val listLoader = dataContext.listLoader
    val listModel = CollectionListModel(listLoader.loadedData)
    listLoader.addDataListener(disposable, object : GHListLoader.ListDataListener {
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
                                disposable)
      .create(list, dataContext.avatarIconsProvider)
  }

  override fun viewPullRequest(id: GHPRIdentifier, requestFocus: Boolean): GHPRCommitBrowserComponentController? {
    val content = contentManager.contents.find {
      (it.type as? TabType.PR)?.id == id
    } ?: createAndAddPRContent(id)
    contentManager.setSelectedContent(content, requestFocus)

    return UIUtil.findComponentOfType(content.component, ChangesTree::class.java)?.let {
      ClientProperty.get(it, GHPRCommitBrowserComponentController.KEY)
    }
  }

  private fun createAndAddPRContent(id: GHPRIdentifier): Content {
    val contentDisposable = Disposer.newDisposable()
    val title = "#${id.number}"
    val component = GHPRViewComponentFactory(ActionManager.getInstance(), project, dataContext, id, contentDisposable).create()
    val content = contentManager.factory.createContent(component, title, false).apply {
      setDisposer(contentDisposable)
      isCloseable = true
      type = TabType.PR(id)
    }
    contentManager.addContent(content)
    return content
  }

  override fun openPullRequestTimeline(id: GHPRIdentifier, requestFocus: Boolean) =
    dataContext.filesManager.createAndOpenTimelineFile(id, requestFocus)

  override fun openPullRequestDiff(id: GHPRIdentifier, requestFocus: Boolean) =
    dataContext.filesManager.createAndOpenDiffFile(id, requestFocus)
}

private var Content.type: TabType
  set(value) = putUserData(TabType.KEY, value)
  get() = getUserData(TabType.KEY)!!

private sealed interface TabType {
  object List : TabType
  object New : TabType
  class PR(val id: GHPRIdentifier) : TabType

  companion object {
    val KEY = Key.create<TabType>("GitHub.PullRequests.ToolWindow.Tab.Type")
  }
}