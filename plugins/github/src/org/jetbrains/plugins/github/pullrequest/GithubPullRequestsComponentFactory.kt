// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.*
import com.intellij.util.ui.JBSwingUtilities
import git4idea.commands.Git
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestsDataContext
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.ui.GithubPullRequestEditorCommentsThreadComponentFactoryImpl
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.*
import org.jetbrains.plugins.github.pullrequest.data.service.*
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchPanel
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchQueryHolder
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchQueryHolderImpl
import org.jetbrains.plugins.github.pullrequest.ui.*
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import org.jetbrains.plugins.github.util.GithubSharedProjectSettings
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.event.ListSelectionEvent


internal class GithubPullRequestsComponentFactory(private val project: Project,
                                                  private val copyPasteManager: CopyPasteManager,
                                                  private val progressManager: ProgressManager,
                                                  private val git: Git,
                                                  private val avatarLoader: CachingGithubUserAvatarLoader,
                                                  private val imageResizer: GithubImageResizer,
                                                  private val actionManager: ActionManager,
                                                  private val autoPopupController: AutoPopupController,
                                                  private val sharedProjectSettings: GithubSharedProjectSettings,
                                                  private val pullRequestUiSettings: GithubPullRequestsProjectUISettings,
                                                  private val fileEditorManager: FileEditorManager) {

  fun createComponent(requestExecutor: GithubApiRequestExecutor,
                      repository: GitRepository, remote: GitRemote,
                      accountDetails: GithubAuthenticatedUser, repoDetails: GithubRepoDetailed,
                      account: GithubAccount): JComponent? {
    val avatarIconsProviderFactory = CachingGithubAvatarIconsProvider.Factory(avatarLoader, imageResizer, requestExecutor)
    return GithubPullRequestsComponent(requestExecutor, avatarIconsProviderFactory, pullRequestUiSettings,
                                       repository, remote,
                                       accountDetails, repoDetails, account)
  }

  inner class GithubPullRequestsComponent(requestExecutor: GithubApiRequestExecutor,
                                          private val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                          pullRequestUiSettings: GithubPullRequestsProjectUISettings,
                                          repository: GitRepository, remote: GitRemote,
                                          accountDetails: GithubAuthenticatedUser, repoDetails: GithubRepoDetailed, account: GithubAccount)
    : OnePixelSplitter("Github.PullRequests.Component", 0.33f), Disposable, DataProvider {

    private val repoDataLoader: GithubPullRequestsRepositoryDataLoader
    private val listModel: CollectionListModel<GHPullRequestShort>
    private val searchHolder: GithubPullRequestSearchQueryHolder
    private val listLoader: GHPRListLoader
    private val listSelectionHolder: GithubPullRequestsListSelectionHolder
    private val dataLoader: GithubPullRequestsDataLoader
    private val securityService: GithubPullRequestsSecurityService
    private val busyStateTracker: GithubPullRequestsBusyStateTracker
    private val metadataService: GithubPullRequestsMetadataService
    private val stateService: GithubPullRequestsStateService

    private val serviceDisposable: Disposable

    init {
      repoDataLoader = GithubPullRequestsRepositoryDataLoaderImpl(progressManager, requestExecutor, account.server, repoDetails.fullPath)
      listModel = CollectionListModel()
      searchHolder = GithubPullRequestSearchQueryHolderImpl()
      listLoader = GHPRListLoaderImpl(progressManager, requestExecutor, account.server, repoDetails.fullPath, listModel, searchHolder)
      listSelectionHolder = GithubPullRequestsListSelectionHolderImpl()
      dataLoader = GithubPullRequestsDataLoaderImpl(project, progressManager, git, requestExecutor, repository, remote,
                                                    account.server, repoDetails.fullPath)

      securityService = GithubPullRequestsSecurityServiceImpl(sharedProjectSettings, accountDetails, repoDetails)
      busyStateTracker = GithubPullRequestsBusyStateTrackerImpl()
      metadataService = GithubPullRequestsMetadataServiceImpl(project, progressManager,
                                                              repoDataLoader, listLoader, dataLoader,
                                                              busyStateTracker,
                                                              requestExecutor, avatarIconsProviderFactory, account.server,
                                                              repoDetails.fullPath)
      stateService = GithubPullRequestsStateServiceImpl(project, progressManager, listLoader, dataLoader, busyStateTracker,
                                                        requestExecutor, account.server, repoDetails.fullPath)

      serviceDisposable = Disposable {
        Disposer.dispose(repoDataLoader)
        Disposer.dispose(listLoader)
        Disposer.dispose(dataLoader)
      }
    }

    private val uiDisposable: Disposable

    init {
      val list = GithubPullRequestsList(copyPasteManager, avatarIconsProviderFactory, listModel)
      list.emptyText.clear()
      installPopup(list)
      installSelectionSaver(list)
      list.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          if (JBSwingUtilities.isLeftMouseButton(e) && e.clickCount >= 2 && ListUtil.isPointOnSelection(list, e.x, e.y)) {
            openTimelineForSelection(list)
            e.consume()
          }
        }
      })
      list.registerKeyboardAction({ openTimelineForSelection(list) }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                                  JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)

      val search = GithubPullRequestSearchPanel(project, autoPopupController, searchHolder).apply {
        border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
      }
      val loaderPanel = GHPRListLoaderPanel(listLoader, dataLoader, list, search)
      firstComponent = loaderPanel

      val diffCommentComponentFactory = GithubPullRequestEditorCommentsThreadComponentFactoryImpl(avatarIconsProviderFactory)
      val changes = GithubPullRequestChangesComponent(project, pullRequestUiSettings, diffCommentComponentFactory).apply {
        diffAction.registerCustomShortcutSet(this@GithubPullRequestsComponent, this@GithubPullRequestsComponent)
      }
      val details = GithubPullRequestDetailsComponent(dataLoader, securityService, busyStateTracker, metadataService, stateService,
                                                      avatarIconsProviderFactory)
      val preview = GithubPullRequestPreviewComponent(changes, details)

      listSelectionHolder.addSelectionChangeListener(preview) {
        preview.dataProvider = listSelectionHolder.selectionNumber?.let(dataLoader::getDataProvider)
      }

      dataLoader.addInvalidationListener(preview) {
        val selection = listSelectionHolder.selectionNumber
        if (selection != null && selection == it) {
          preview.dataProvider = dataLoader.getDataProvider(selection)
        }
      }

      secondComponent = preview
      isFocusCycleRoot = true

      uiDisposable = Disposable {
        Disposer.dispose(list)
        Disposer.dispose(search)
        Disposer.dispose(loaderPanel)

        Disposer.dispose(preview)
        Disposer.dispose(changes)
        Disposer.dispose(details)
      }
    }

    private fun openTimelineForSelection(list: GithubPullRequestsList) {
      val pullRequest = list.selectedValue
      val file = GHPRVirtualFile(dataContext, pullRequest, dataLoader.getDataProvider(pullRequest.number))
      fileEditorManager.openFile(file, true)
    }

    private val dataContext = GithubPullRequestsDataContext(requestExecutor, repoDataLoader, listLoader, listSelectionHolder, dataLoader,
                                                            account.server, repoDetails, accountDetails, repository, remote)

    private fun installPopup(list: GithubPullRequestsList) {
      val popupHandler = object : PopupHandler() {
        override fun invokePopup(comp: Component, x: Int, y: Int) {
          if (ListUtil.isPointOnSelection(list, x, y)) {
            val popupMenu = actionManager
              .createActionPopupMenu("GithubPullRequestListPopup",
                                     actionManager.getAction("Github.PullRequest.ToolWindow.List.Popup") as ActionGroup)
            popupMenu.setTargetComponent(list)
            popupMenu.component.show(comp, x, y)
          }
        }
      }
      list.addMouseListener(popupHandler)
    }

    private fun installSelectionSaver(list: GithubPullRequestsList) {
      var savedSelectionNumber: Long? = null

      list.selectionModel.addListSelectionListener { e: ListSelectionEvent ->
        if (!e.valueIsAdjusting) {
          val selectedIndex = list.selectedIndex
          if (selectedIndex >= 0 && selectedIndex < list.model.size) {
            listSelectionHolder.selectionNumber = list.model.getElementAt(selectedIndex).number
            savedSelectionNumber = null
          }
        }
      }

      list.model.addListDataListener(object : ListDataListener {
        override fun intervalAdded(e: ListDataEvent) {
          if (e.type == ListDataEvent.INTERVAL_ADDED)
            (e.index0..e.index1).find { list.model.getElementAt(it).number == savedSelectionNumber }
              ?.run { ApplicationManager.getApplication().invokeLater { ScrollingUtil.selectItem(list, this) } }
        }

        override fun contentsChanged(e: ListDataEvent) {}
        override fun intervalRemoved(e: ListDataEvent) {
          if (e.type == ListDataEvent.INTERVAL_REMOVED) savedSelectionNumber = listSelectionHolder.selectionNumber
        }
      })
    }

    override fun getData(dataId: String): Any? {
      if (Disposer.isDisposed(this)) return null
      return when {
        GithubPullRequestKeys.DATA_CONTEXT.`is`(dataId) -> dataContext
        else -> null
      }
    }

    override fun dispose() {
      Disposer.dispose(uiDisposable)
      Disposer.dispose(serviceDisposable)
    }
  }
}