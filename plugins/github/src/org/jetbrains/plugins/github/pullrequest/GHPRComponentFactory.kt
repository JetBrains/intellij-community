// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.codeInsight.AutoPopupController
import com.intellij.diff.editor.VCSContentVirtualFile
import com.intellij.ide.DataManager
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorWindowHolder
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.action.GHPRListSelectionActionDataContext
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContextRepository
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchPanel
import org.jetbrains.plugins.github.pullrequest.ui.*
import org.jetbrains.plugins.github.pullrequest.ui.changes.*
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDescriptionPanel
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRMetadataPanel
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.*
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionListener
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JComponent
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

  private var ghprVirtualFile: VCSContentVirtualFile? = null
  private var ghprEditorContent: JComponent? = null

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
          var content = createContent(dataContext, uiDisposable)
          if (Registry.`is`("show.log.as.editor.tab")) {
            content = patchContent(content)
          }

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

  private fun patchContent(content: JComponent): JComponent {
    var patchedContent = content
    val onePixelSplitter = patchedContent as OnePixelSplitter
    val splitter = onePixelSplitter.secondComponent as Splitter
    patchedContent = splitter.secondComponent

    onePixelSplitter.secondComponent = splitter.firstComponent
    installEditor(onePixelSplitter)
    return patchedContent
  }

  private fun installEditor(onePixelSplitter: OnePixelSplitter) {
    ghprEditorContent = onePixelSplitter
    ApplicationManager.getApplication().invokeLater({ tryOpenGHPREditorTab() }, ModalityState.NON_MODAL)
  }

  @CalledInAwt
  private fun getOrCreateGHPRViewFile(): VirtualFile? {
    ApplicationManager.getApplication().assertIsDispatchThread()

    if (ghprEditorContent == null) return null

    if (ghprVirtualFile == null) {
      val content = ghprEditorContent ?: error("editor content should be created by this time")
      ghprVirtualFile = VCSContentVirtualFile(content) { "GitHub Pull Requests" }
      ghprVirtualFile?.putUserData(VCSContentVirtualFile.TabSelector) {
        GithubUIUtil.findAndSelectGitHubContent(project, true)
      }
    }

    Disposer.register(project, Disposable { ghprVirtualFile = null })

    return ghprVirtualFile ?: error("error")
  }
  
  fun tryOpenGHPREditorTab() {
    val file = getOrCreateGHPRViewFile() ?: return

    val editors = FileEditorManager.getInstance(project).openFile(file, true)
    assert(editors.size == 1) { "opened multiple log editors for $file" }
    val editor = editors[0]
    val component = editor.component
    val holder = ComponentUtil.getParentOfType(EditorWindowHolder::class.java as Class<out EditorWindowHolder>, component as Component)
                 ?: return
    val editorWindow = holder.editorWindow
    editorWindow.setFilePinned(file, true)
  }

  private fun createContent(dataContext: GHPullRequestsDataContext, disposable: Disposable): JComponent {
    val avatarIconsProviderFactory = CachingGithubAvatarIconsProvider.Factory(avatarLoader, imageResizer, dataContext.requestExecutor)
    val listSelectionHolder = GithubPullRequestsListSelectionHolderImpl()
    val actionDataContext = GHPRListSelectionActionDataContext(dataContext, listSelectionHolder, avatarIconsProviderFactory)

    val list = createListComponent(dataContext, listSelectionHolder, avatarIconsProviderFactory, disposable)

    val dataProviderModel = createDataProviderModel(dataContext, listSelectionHolder, disposable)

    val detailsLoadingModel = createDetailsLoadingModel(dataProviderModel, disposable)
    val detailsModel = createValueModel(detailsLoadingModel)

    val detailsPanel = createDetailsPanel(dataContext, detailsModel, avatarIconsProviderFactory, disposable)
    val detailsLoadingPanel = GHLoadingPanel(detailsLoadingModel, detailsPanel, disposable,
                                             GHLoadingPanel.EmptyTextBundle.Simple("Select pull request to view details",
                                                                                   "Can't load details")).apply {
      resetHandler = ActionListener { dataProviderModel.value?.reloadDetails() }
    }

    val changesModel = GHPRChangesModelImpl(project)
    val diffHelper = GHPRChangesDiffHelperImpl(project, dataContext.reviewService,
                                               avatarIconsProviderFactory, dataContext.securityService.currentUser)
    val changesLoadingModel = createChangesLoadingModel(changesModel, diffHelper,
                                                        dataProviderModel, projectUiSettings, disposable)
    val changesBrowser = GHPRChangesBrowser(changesModel, diffHelper, project)

    val changesLoadingPanel = GHLoadingPanel(changesLoadingModel, changesBrowser, disposable,
                                             GHLoadingPanel.EmptyTextBundle.Simple("Select pull request to view changes",
                                                                                   "Can't load changes",
                                                                                   "Pull request does not contain any changes")).apply {
      resetHandler = ActionListener { dataProviderModel.value?.reloadCommits() }
    }

    return OnePixelSplitter("Github.PullRequests.Component", 0.33f).apply {
      background = UIUtil.getListBackground()
      isOpaque = true
      isFocusCycleRoot = true
      firstComponent = list
      secondComponent = OnePixelSplitter("Github.PullRequest.Preview.Component", 0.5f).apply {
        firstComponent = detailsLoadingPanel
        secondComponent = changesLoadingPanel
      }.also {
        (actionManager.getAction("Github.PullRequest.Details.Reload") as RefreshAction).registerShortcutOn(it)
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

  private fun createListComponent(dataContext: GHPullRequestsDataContext,
                                  listSelectionHolder: GithubPullRequestsListSelectionHolder,
                                  avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                  disposable: Disposable): JComponent {
    val list = GithubPullRequestsList(copyPasteManager, avatarIconsProviderFactory, dataContext.listModel).apply {
      emptyText.clear()
    }.also {
      it.addFocusListener(object : FocusListener {
        override fun focusGained(e: FocusEvent?) {
          if (it.selectedIndex < 0 && !it.isEmpty) it.selectedIndex = 0
        }

        override fun focusLost(e: FocusEvent?) {}
      })

      installPopup(it)
      installSelectionSaver(it, listSelectionHolder)
      val shortcuts = CompositeShortcutSet(CommonShortcuts.ENTER, CommonShortcuts.DOUBLE_CLICK_1)
      EmptyAction.registerWithShortcutSet("Github.PullRequest.Timeline.Show", shortcuts, it)
      ListSpeedSearch(it) { item -> item.title }
    }

    val search = GithubPullRequestSearchPanel(project, autoPopupController, dataContext.searchHolder).apply {
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
    }

    val listReloadAction = actionManager.getAction("Github.PullRequest.List.Reload") as RefreshAction
    val loaderPanel = GHPRListLoaderPanel(dataContext.listLoader, listReloadAction, list, search).also {
      listReloadAction.registerShortcutOn(it)
    }

    Disposer.register(disposable, Disposable {
      Disposer.dispose(list)
      Disposer.dispose(search)
      Disposer.dispose(loaderPanel)
    })

    return loaderPanel
  }

  private fun createDetailsPanel(dataContext: GHPullRequestsDataContext,
                                 detailsModel: SingleValueModel<GHPullRequest?>,
                                 avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                 parentDisposable: Disposable): JBPanelWithEmptyText {
    val metaPanel = GHPRMetadataPanel(project, detailsModel,
                                      dataContext.securityService,
                                      dataContext.busyStateTracker,
                                      dataContext.metadataService,
                                      avatarIconsProviderFactory).apply {
      border = JBUI.Borders.empty(4, 8, 4, 8)
    }.also {
      Disposer.register(parentDisposable, it)
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

    return JBPanelWithEmptyText(BorderLayout()).apply {
      isOpaque = false

      add(scrollPane, BorderLayout.CENTER)
    }
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

  private fun createChangesLoadingModel(changesModel: GHPRChangesModel,
                                        diffHelper: GHPRChangesDiffHelper,
                                        dataProviderModel: SingleValueModel<GithubPullRequestDataProvider?>,
                                        uiSettings: GithubPullRequestsProjectUISettings,
                                        disposable: Disposable): GHPRChangesLoadingModel {
    val model = GHPRChangesLoadingModel(changesModel, diffHelper, uiSettings.zipChanges)
    projectUiSettings.addChangesListener(disposable) { model.zipChanges = projectUiSettings.zipChanges }

    val requestChangesListener = object : GithubPullRequestDataProvider.RequestsChangedListener {
      override fun commitsRequestChanged() {
        model.dataProvider = model.dataProvider
      }
    }
    dataProviderModel.addValueChangedListener {
      model.dataProvider?.removeRequestsChangesListener(requestChangesListener)
      model.dataProvider = dataProviderModel.value?.apply {
        addRequestsChangesListener(disposable, requestChangesListener)
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

  private fun <T> createValueModel(loadingModel: GHSimpleLoadingModel<T>): SingleValueModel<T?> {
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
    Disposer.register(parentDisposable, Disposable {
      model.value = null
    })

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
