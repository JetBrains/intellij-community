// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.codeInsight.AutoPopupController
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.*
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.UIUtil
import git4idea.GitCommit
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionDataContext
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewThreadsProviderImpl
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadComponentFactoryImpl
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContextRepository
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchPanel
import org.jetbrains.plugins.github.pullrequest.ui.*
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesBrowser
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesModel
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.GithubPullRequestDetailsPanel
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import org.jetbrains.plugins.github.util.GithubImageResizer
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import java.awt.BorderLayout
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.event.ListSelectionEvent

@Service
internal class GHPRComponentFactory(private val project: Project) {

  private val progressManager = ProgressManager.getInstance()
  private val actionManager = ActionManager.getInstance()
  private val copyPasteManager = CopyPasteManager.getInstance()
  private val avatarLoader = CachingGithubUserAvatarLoader.getInstance()
  private val imageResizer = GithubImageResizer.getInstance()

  private val autoPopupController = AutoPopupController.getInstance(project)
  private val projectUiSettings = GithubPullRequestsProjectUISettings.getInstance(project)
  private val dataContextRepository = GHPullRequestsDataContextRepository.getInstance(project)

  @CalledInAwt
  fun createComponent(remoteUrl: GitRemoteUrlCoordinates, account: GithubAccount, requestExecutor: GithubApiRequestExecutor,
                      parentDisposable: Disposable): JComponent {

    val contextValue = object : LazyCancellableBackgroundProcessValue<GHPullRequestsDataContext>(progressManager) {
      override fun compute(indicator: ProgressIndicator) =
        dataContextRepository.getContext(indicator, account, requestExecutor, remoteUrl).also {
          Disposer.register(parentDisposable, it)
        }
    }
    Disposer.register(parentDisposable, Disposable { contextValue.drop() })

    val uiDisposable = Disposer.newDisposable()
    Disposer.register(parentDisposable, uiDisposable)

    val loadingModel = GHCompletableFutureLoadingModel<GHPullRequestsDataContext>()
    val contentContainer = JBPanelWithEmptyText(null).apply {
      background = UIUtil.getListBackground()
    }
    loadingModel.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
      override fun onLoadingCompleted() {
        val dataContext = loadingModel.result
        if (dataContext != null) {
          val content = createContent(dataContext, uiDisposable)
          with(contentContainer) {
            layout = BorderLayout()
            add(content, BorderLayout.CENTER)
            validate()
            repaint()
          }
        }
      }
    })
    loadingModel.future = contextValue.value

    return GHLoadingPanel(loadingModel, contentContainer, uiDisposable,
                          GHLoadingPanel.EmptyTextBundle.Simple("", "Can't load data from GitHub")).apply {
      resetHandler = ActionListener {
        contextValue.drop()
        loadingModel.future = contextValue.value
      }
    }
  }

  private fun createContent(dataContext: GHPullRequestsDataContext, disposable: Disposable): JComponent {
    val avatarIconsProviderFactory = CachingGithubAvatarIconsProvider.Factory(avatarLoader, imageResizer, dataContext.requestExecutor)
    val listSelectionHolder = GithubPullRequestsListSelectionHolderImpl()
    val actionDataContext = GHPRActionDataContext(dataContext, listSelectionHolder, avatarIconsProviderFactory)

    val list = GithubPullRequestsList(copyPasteManager, avatarIconsProviderFactory, dataContext.listModel).apply {
      emptyText.clear()
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          if (SwingUtilities.isLeftMouseButton(e) && e.clickCount >= 2 && ListUtil.isPointOnSelection(this@apply, e.x, e.y)) {
            openTimelineForSelection(dataContext, actionDataContext, this@apply)
            e.consume()
          }
        }
      })
      registerKeyboardAction(
        { openTimelineForSelection(dataContext, actionDataContext, this@apply) },
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    }.also {
      installPopup(it)
      installSelectionSaver(it, listSelectionHolder)
    }

    val search = GithubPullRequestSearchPanel(project, autoPopupController, dataContext.searchHolder).apply {
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
    }
    val loaderPanel = GHPRListLoaderPanel(dataContext.listLoader, dataContext.dataLoader, list, search)

    val dataProviderModel = createDataProviderModel(dataContext, listSelectionHolder, disposable)

    val detailsLoadingModel = createDetailsLoadingModel(dataProviderModel, disposable)
    val detailsModel = createValueModel(detailsLoadingModel)

    val detailsPanel = GithubPullRequestDetailsPanel(project, detailsModel,
                                                     dataContext.securityService,
                                                     dataContext.busyStateTracker,
                                                     dataContext.metadataService,
                                                     dataContext.stateService,
                                                     avatarIconsProviderFactory)
    Disposer.register(disposable, detailsPanel)
    val detailsLoadingPanel = GHLoadingPanel(detailsLoadingModel, detailsPanel, disposable,
                                             GHLoadingPanel.EmptyTextBundle.Simple("Select pull request to view details",
                                                                                   "Can't load details")).apply {
      resetHandler = ActionListener { dataProviderModel.value?.reloadDetails() }
    }

    val changesLoadingModel = createChangesLoadingModel(dataProviderModel, disposable)
    val changesModel = createChangesModel(projectUiSettings, changesLoadingModel, disposable)
    val changesBrowser = GHPRChangesBrowser(changesModel, project)

    val diffCommentComponentFactory = GHPREditorReviewThreadComponentFactoryImpl(avatarIconsProviderFactory)
    dataProviderModel.addValueChangedListener {
      changesBrowser.diffReviewThreadsProvider = dataProviderModel.value?.let {
        GHPRDiffReviewThreadsProviderImpl(it, diffCommentComponentFactory)
      }
    }

    val changesLoadingPanel = GHLoadingPanel(changesLoadingModel, changesBrowser, disposable,
                                             GHLoadingPanel.EmptyTextBundle.Simple("Select pull request to view changes",
                                                                                   "Can't load changes",
                                                                                   "Pull request does not contain any changes")).apply {
      resetHandler = ActionListener { dataProviderModel.value?.reloadCommits() }
    }

    Disposer.register(disposable, Disposable {
      Disposer.dispose(list)
      Disposer.dispose(search)
      Disposer.dispose(loaderPanel)

      Disposer.dispose(detailsPanel)
    })

    return OnePixelSplitter("Github.PullRequests.Component", 0.33f).apply {
      background = UIUtil.getListBackground()
      isOpaque = true
      isFocusCycleRoot = true
      firstComponent = loaderPanel
      secondComponent = OnePixelSplitter("Github.PullRequest.Preview.Component", 0.5f).apply {
        firstComponent = detailsLoadingPanel
        secondComponent = changesLoadingPanel
      }
    }.also {
      changesBrowser.diffAction.registerCustomShortcutSet(it, disposable)
      DataManager.registerDataProvider(it) { dataId ->
        if (Disposer.isDisposed(disposable)) null
        else when {
          GithubPullRequestKeys.ACTION_DATA_CONTEXT.`is`(dataId) -> actionDataContext
          else -> null
        }

      }
    }
  }

  private fun openTimelineForSelection(dataContext: GHPullRequestsDataContext,
                                       actionDataContext: GHPRActionDataContext,
                                       list: GithubPullRequestsList) {
    val pullRequest = list.selectedValue
    val file = GHPRVirtualFile(actionDataContext,
                               pullRequest,
                               dataContext.dataLoader.getDataProvider(pullRequest.number))
    FileEditorManager.getInstance(project).openFile(file, true)
  }

  private fun installPopup(list: GithubPullRequestsList) {
    val popupHandler = object : PopupHandler() {
      override fun invokePopup(comp: java.awt.Component, x: Int, y: Int) {
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

  private fun installSelectionSaver(list: GithubPullRequestsList, listSelectionHolder: GithubPullRequestsListSelectionHolder) {
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

  private fun createChangesModel(projectUiSettings: GithubPullRequestsProjectUISettings,
                                 loadingModel: GHLoadingModel<List<GitCommit>>,
                                 parentDisposable: Disposable): GHPRChangesModel {
    val model = GHPRChangesModelImpl(projectUiSettings.zipChanges)
    loadingModel.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
      override fun onLoadingCompleted() {
        model.commits = loadingModel.result.orEmpty()
      }

      override fun onReset() {
        model.commits = loadingModel.result.orEmpty()
      }
    })
    projectUiSettings.addChangesListener(parentDisposable) {
      model.zipChanges = projectUiSettings.zipChanges
    }
    return model
  }

  private fun createChangesLoadingModel(dataProviderModel: SingleValueModel<GithubPullRequestDataProvider?>,
                                        parentDisposable: Disposable): GHCompletableFutureLoadingModel<List<GitCommit>> {
    val model = GHCompletableFutureLoadingModel<List<GitCommit>>()

    var listenerDisposable: Disposable? = null

    dataProviderModel.addValueChangedListener {
      val provider = dataProviderModel.value
      model.future = provider?.logCommitsRequest

      listenerDisposable = listenerDisposable?.let {
        Disposer.dispose(it)
        null
      }

      if (provider != null) {
        val disposable = Disposer.newDisposable().apply {
          Disposer.register(parentDisposable, this)
        }
        provider.addRequestsChangesListener(disposable, object : GithubPullRequestDataProvider.RequestsChangedListener {
          override fun commitsRequestChanged() {
            model.future = provider.logCommitsRequest
          }
        })

        listenerDisposable = disposable
      }
    }

    return model
  }

  private fun createDetailsLoadingModel(dataProviderModel: SingleValueModel<GithubPullRequestDataProvider?>,
                                        parentDisposable: Disposable): GHCompletableFutureLoadingModel<GHPullRequest> {
    val model = GHCompletableFutureLoadingModel<GHPullRequest>()

    var listenerDisposable: Disposable? = null

    dataProviderModel.addValueChangedListener {
      val provider = dataProviderModel.value
      model.future = provider?.detailsRequest

      listenerDisposable = listenerDisposable?.let {
        Disposer.dispose(it)
        null
      }

      if (provider != null) {
        val disposable = Disposer.newDisposable().apply {
          Disposer.register(parentDisposable, this)
        }
        provider.addRequestsChangesListener(disposable, object : GithubPullRequestDataProvider.RequestsChangedListener {
          override fun detailsRequestChanged() {
            model.future = provider.detailsRequest
          }
        })

        listenerDisposable = disposable
      }
    }

    return model
  }

  private fun <T> createValueModel(loadingModel: GHLoadingModel<T>): SingleValueModel<T?> {
    val model = SingleValueModel<T?>(null)
    loadingModel.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
      override fun onLoadingCompleted() {
        model.value = loadingModel.result
      }

      override fun onReset() {
        model.value = loadingModel.result
      }
    })
    return model
  }

  private fun createDataProviderModel(dataContext: GHPullRequestsDataContext,
                                      listSelectionHolder: GithubPullRequestsListSelectionHolder,
                                      parentDisposable: Disposable): SingleValueModel<GithubPullRequestDataProvider?> {
    val model: SingleValueModel<GithubPullRequestDataProvider?> = SingleValueModel(null)

    fun setNewProvider(provider: GithubPullRequestDataProvider?) {
      val oldValue = model.value
      if (oldValue != null && provider != null && oldValue.number != provider.number) {
        model.value = null
      }
      model.value = provider
    }

    listSelectionHolder.addSelectionChangeListener(parentDisposable) {
      setNewProvider(listSelectionHolder.selectionNumber?.let(dataContext.dataLoader::getDataProvider))
    }

    dataContext.dataLoader.addInvalidationListener(parentDisposable) {
      val selection = listSelectionHolder.selectionNumber
      if (selection != null && selection == it) {
        setNewProvider(dataContext.dataLoader.getDataProvider(selection))
      }
    }

    return model
  }
}