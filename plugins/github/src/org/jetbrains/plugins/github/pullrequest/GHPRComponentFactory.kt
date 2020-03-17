// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.codeInsight.AutoPopupController
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.ui.*
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.action.GHPRFixedActionDataContext
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchPanel
import org.jetbrains.plugins.github.pullrequest.ui.*
import org.jetbrains.plugins.github.pullrequest.ui.changes.*
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDescriptionPanel
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRMetadataPanel
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.*
import java.awt.BorderLayout
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.util.function.Consumer
import javax.swing.JComponent

@Service
internal class GHPRComponentFactory(private val project: Project) {

  private val progressManager = ProgressManager.getInstance()
  private val actionManager = ActionManager.getInstance()
  private val copyPasteManager = CopyPasteManager.getInstance()
  private val avatarLoader = CachingGithubUserAvatarLoader.getInstance()
  private val imageResizer = GithubImageResizer.getInstance()

  private val autoPopupController = AutoPopupController.getInstance(project)
  private val projectUiSettings = GithubPullRequestsProjectUISettings.getInstance(project)
  private val dataContextRepository = GHPRDataContextRepository.getInstance(project)

  @CalledInAwt
  fun createComponent(remoteUrl: GitRemoteUrlCoordinates, account: GithubAccount, requestExecutor: GithubApiRequestExecutor,
                      parentDisposable: Disposable): JComponent {

    val contextDisposable = Disposer.newDisposable()
    val contextValue = LazyCancellableBackgroundProcessValue.create(progressManager) { indicator ->
      dataContextRepository.getContext(indicator, account, requestExecutor, remoteUrl).also {
        Disposer.register(contextDisposable, it)
      }
    }
    Disposer.register(parentDisposable, contextDisposable)
    Disposer.register(parentDisposable, Disposable { contextValue.drop() })

    val uiDisposable = Disposer.newDisposable()
    Disposer.register(parentDisposable, uiDisposable)

    val loadingModel = GHCompletableFutureLoadingModel<GHPRDataContext>(uiDisposable)
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
      errorHandler = GHLoadingErrorHandlerImpl(project, account) {
        contextValue.drop()
        loadingModel.future = contextValue.value
      }
    }
  }

  private fun createContent(dataContext: GHPRDataContext, disposable: Disposable): JComponent {
    val wrapper = Wrapper()
    ContentController(dataContext, wrapper, disposable)
    return wrapper
  }

  private inner class ContentController(private val dataContext: GHPRDataContext,
                                        private val wrapper: Wrapper,
                                        private val parentDisposable: Disposable) {

    private val avatarIconsProviderFactory =
      CachingGithubAvatarIconsProvider.Factory(avatarLoader, imageResizer, dataContext.requestExecutor)

    private val listComponent = createListComponent(dataContext, avatarIconsProviderFactory, parentDisposable)

    private var currentDisposable: Disposable? = null

    init {
      viewList()

      DataManager.registerDataProvider(wrapper) { dataId ->
        when {
          GHPRActionKeys.VIEW_PULL_REQUEST_EXECUTOR.`is`(dataId) -> Consumer<GHPullRequestShort> { viewPullRequest(it) }
          GHPRActionKeys.DATA_CONTEXT.`is`(dataId) -> dataContext
          else -> null
        }
      }
    }

    private fun viewList() {
      currentDisposable?.let { Disposer.dispose(it) }
      wrapper.setContent(listComponent)
      wrapper.repaint()
    }

    private fun viewPullRequest(details: GHPullRequestShort) {
      currentDisposable?.let { Disposer.dispose(it) }
      currentDisposable = Disposer.newDisposable("Pull request component disposable").also {
        Disposer.register(parentDisposable, it)
      }
      val componentDataProvider = dataContext.dataLoader.getDataProvider(details, currentDisposable!!)
      val pullRequestComponent = createPullRequestComponent(dataContext, componentDataProvider,
                                                            ::viewList,
                                                            avatarIconsProviderFactory,
                                                            currentDisposable!!)
      wrapper.setContent(pullRequestComponent)
      wrapper.repaint()
      GithubUIUtil.focusPanel(wrapper)
    }
  }

  private fun createPullRequestComponent(dataContext: GHPRDataContext,
                                         dataProvider: GHPRDataProvider,
                                         returnToListListener: () -> Unit,
                                         avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                         disposable: Disposable): JComponent {

    val actionDataContext = GHPRFixedActionDataContext(dataContext, dataProvider, avatarIconsProviderFactory)

    val detailsLoadingModel = createDetailsLoadingModel(dataProvider, disposable)
    val detailsModel = createValueModel(detailsLoadingModel)

    val returnToListLink = LinkLabel<Any>("Back to List", AllIcons.General.ArrowLeft) { _, _ ->
      returnToListListener()
    }

    val detailsPanel = createDetailsPanel(dataContext, detailsModel, avatarIconsProviderFactory)
    val detailsLoadingPanel = GHLoadingPanel(detailsLoadingModel, detailsPanel, disposable,
                                             GHLoadingPanel.EmptyTextBundle.Simple("Select pull request to view details",
                                                                                   "Can't load details")).apply {
      errorHandler = GHLoadingErrorHandlerImpl(project, dataContext.account) { dataProvider.reloadDetails() }
    }

    val changesModel = GHPRChangesModelImpl(project)
    val diffHelper = GHPRChangesDiffHelperImpl(avatarIconsProviderFactory, dataContext.securityService.currentUser)
    val changesLoadingModel = createChangesLoadingModel(changesModel, diffHelper,
                                                        dataProvider, projectUiSettings, disposable)
    val changesBrowser = GHPRChangesBrowser(changesModel, diffHelper, project)

    val changesLoadingPanel = GHLoadingPanel(changesLoadingModel, changesBrowser, disposable,
                                             GHLoadingPanel.EmptyTextBundle.Simple("Select pull request to view changes",
                                                                                   "Can't load changes",
                                                                                   "Pull request does not contain any changes")).apply {
      errorHandler = GHLoadingErrorHandlerImpl(project, dataContext.account) { dataProvider.reloadChanges() }
    }

    return OnePixelSplitter(true, "Github.PullRequest.Preview.Component", 0.5f).apply {
        firstComponent = BorderLayoutPanel().addToCenter(detailsLoadingPanel).addToTop(returnToListLink).andTransparent()
        secondComponent = changesLoadingPanel
      }.also {
        (actionManager.getAction("Github.PullRequest.Details.Reload") as RefreshAction).registerCustomShortcutSet(it, disposable)
      }
      .also {
        changesBrowser.diffAction.registerCustomShortcutSet(it, disposable)
        DataManager.registerDataProvider(it) { dataId ->
          if (Disposer.isDisposed(disposable)) null
          else when {
            GHPRActionKeys.ACTION_DATA_CONTEXT.`is`(dataId) -> actionDataContext
            else -> null
          }
        }
      }
  }

  private fun createListComponent(dataContext: GHPRDataContext,
                                  avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                  disposable: Disposable): JComponent {
    val list = GHPRList(copyPasteManager, avatarIconsProviderFactory, dataContext.listModel).apply {
      emptyText.clear()
    }.also {
      it.addFocusListener(object : FocusListener {
        override fun focusGained(e: FocusEvent?) {
          if (it.selectedIndex < 0 && !it.isEmpty) it.selectedIndex = 0
        }

        override fun focusLost(e: FocusEvent?) {}
      })

      installPopup(it)
      val shortcuts = CompositeShortcutSet(CommonShortcuts.ENTER, CommonShortcuts.DOUBLE_CLICK_1)
      EmptyAction.registerWithShortcutSet("Github.PullRequest.Show", shortcuts, it)
      ListSpeedSearch(it) { item -> item.title }
    }

    val search = GithubPullRequestSearchPanel(project, autoPopupController, dataContext.searchHolder).apply {
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
    }

    val listReloadAction = actionManager.getAction("Github.PullRequest.List.Reload") as RefreshAction
    val loaderPanel = GHPRListLoaderPanel(dataContext.listLoader, listReloadAction, list, search).apply {
      errorHandler = GHLoadingErrorHandlerImpl(project, dataContext.account) {
        dataContext.listLoader.reset()
      }
    }.also {
      listReloadAction.registerCustomShortcutSet(it, disposable)

      DataManager.registerDataProvider(it) { dataId ->
        if (GHPRActionKeys.SELECTED_PULL_REQUEST.`is`(dataId)) {
          if (list.isSelectionEmpty) null else list.selectedValue
        }
        else null
      }
    }

    Disposer.register(disposable, Disposable {
      Disposer.dispose(list)
      Disposer.dispose(search)
      Disposer.dispose(loaderPanel)
    })

    return loaderPanel
  }

  private fun createDetailsPanel(dataContext: GHPRDataContext,
                                 detailsModel: SingleValueModel<GHPullRequest?>,
                                 avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory): JBPanelWithEmptyText {

    val metaPanel = GHPRMetadataPanel(project, detailsModel,
                                      dataContext.securityService,
                                      dataContext.metadataService,
                                      avatarIconsProviderFactory).apply {
      border = JBUI.Borders.empty(4, 8, 4, 8)
    }

    val descriptionPanel = GHPRDescriptionPanel(detailsModel).apply {
      border = JBUI.Borders.empty(4, 8, 8, 8)
    }

    val scrollablePanel = ScrollablePanel(VerticalFlowLayout(0, 0)).apply {
      isOpaque = false
      add(metaPanel)
      add(descriptionPanel)
    }
    val scrollPane = ScrollPaneFactory.createScrollPane(scrollablePanel, true).apply {
      viewport.isOpaque = false
      isOpaque = false
    }.also {
      val actionGroup = actionManager.getAction("Github.PullRequest.Details.Popup") as ActionGroup
      PopupHandler.installPopupHandler(it, actionGroup, ActionPlaces.UNKNOWN, actionManager)
    }

    scrollPane.isVisible = detailsModel.value != null

    detailsModel.addValueChangedListener {
      scrollPane.isVisible = detailsModel.value != null
    }

    val panel = JBPanelWithEmptyText(BorderLayout()).apply {
      isOpaque = false

      add(scrollPane, BorderLayout.CENTER)
    }
    detailsModel.addValueChangedListener {
      panel.validate()
    }
    return panel
  }

  private fun installPopup(list: GHPRList) {
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

  private fun createChangesLoadingModel(changesModel: GHPRChangesModel,
                                        diffHelper: GHPRChangesDiffHelper,
                                        dataProvider: GHPRDataProvider,
                                        uiSettings: GithubPullRequestsProjectUISettings,
                                        disposable: Disposable): GHPRChangesLoadingModel {
    val model = GHPRChangesLoadingModel(changesModel, diffHelper, uiSettings.zipChanges).also {
      it.dataProvider = dataProvider
    }
    projectUiSettings.addChangesListener(disposable) { model.zipChanges = projectUiSettings.zipChanges }
    Disposer.register(disposable, Disposable { model.dataProvider = null })
    return model
  }

  private fun createDetailsLoadingModel(dataProvider: GHPRDataProvider,
                                        parentDisposable: Disposable): GHCompletableFutureLoadingModel<GHPullRequest> {
    val model = GHCompletableFutureLoadingModel<GHPullRequest>(parentDisposable).apply {
      future = dataProvider.detailsRequest
    }
    dataProvider.addRequestsChangesListener(parentDisposable, object : GHPRDataProvider.RequestsChangedListener {
      override fun detailsRequestChanged() {
        model.future = dataProvider.detailsRequest
      }
    })

    return model
  }

  private fun <T> createValueModel(loadingModel: GHSimpleLoadingModel<T>): SingleValueModel<T?> {
    val model = SingleValueModel(loadingModel.result)
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
}
