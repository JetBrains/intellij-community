// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.codeInsight.AutoPopupController
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.actions.RefreshAction
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ui.FontUtil
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsUserImpl
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import com.intellij.vcs.log.ui.details.commit.getCommitDetailsBackground
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHGitActor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionDataContext
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.action.GHPRFixedActionDataContext
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchPanel
import org.jetbrains.plugins.github.pullrequest.ui.*
import org.jetbrains.plugins.github.pullrequest.ui.changes.*
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDescriptionPanel
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRMetadataPanel
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.*
import java.awt.BorderLayout
import java.awt.Rectangle
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants

@Service
internal class GHPRComponentFactory(private val project: Project) {

  private val progressManager = ProgressManager.getInstance()
  private val actionManager = ActionManager.getInstance()
  private val copyPasteManager = CopyPasteManager.getInstance()
  private val avatarLoader = CachingGithubUserAvatarLoader.getInstance()
  private val imageResizer = GithubImageResizer.getInstance()

  private val autoPopupController = AutoPopupController.getInstance(project)
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
    val commitsModel = GHPRCommitsModelImpl()
    val cumulativeChangesModel = GHPRChangesModelImpl(project)
    val diffHelper = GHPRChangesDiffHelperImpl(avatarIconsProviderFactory, dataContext.securityService.currentUser)
    val changesLoadingModel = createChangesLoadingModel(commitsModel, cumulativeChangesModel, diffHelper, dataProvider, disposable)

    val infoComponent = createInfoComponent(dataContext, actionDataContext, changesLoadingModel, disposable)
    val commitsComponent = createCommitsComponent(dataContext, actionDataContext, changesLoadingModel, disposable)

    val infoTabInfo = TabInfo(infoComponent).apply {
      text = "Info"
      sideComponent = createReturnToListSideComponent(returnToListListener)
    }
    val commitsTabInfo = TabInfo(commitsComponent).apply {
      text = "Commits"
      sideComponent = createReturnToListSideComponent(returnToListListener)
    }

    fun updateCommitsTabText() {
      val commitsCount = commitsModel.commitsWithChanges?.size
      commitsTabInfo.text = if (commitsCount == null) "Commits" else "Commits ($commitsCount)"
    }
    updateCommitsTabText()
    commitsModel.addStateChangesListener(::updateCommitsTabText)

    return object : SingleHeightTabs(project, project) {
      override fun adjust(each: TabInfo?) {}
    }.apply {
      addTab(infoTabInfo)
      addTab(commitsTabInfo)
    }
  }

  private fun createReturnToListSideComponent(returnToListListener: () -> Unit): JComponent {
    return BorderLayoutPanel()
      .addToRight(LinkLabel<Any>("Back to List", AllIcons.Actions.Back) { _, _ ->
        returnToListListener()
      }.apply {
        border = JBUI.Borders.emptyRight(8)
      })
      .andTransparent()
      .withBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM))
  }

  private fun createInfoComponent(dataContext: GHPRDataContext,
                                  actionDataContext: GHPRActionDataContext,
                                  changesLoadingModel: GHPRChangesLoadingModel,
                                  disposable: Disposable): JComponent {
    val dataProvider = actionDataContext.pullRequestDataProvider

    val detailsLoadingModel = createDetailsLoadingModel(dataProvider, disposable)
    val detailsModel = createValueModel(detailsLoadingModel)

    val detailsPanel = createDetailsPanel(dataContext, detailsModel, actionDataContext.avatarIconsProviderFactory)
    val detailsLoadingPanel = GHLoadingPanel(detailsLoadingModel, detailsPanel, disposable,
                                             GHLoadingPanel.EmptyTextBundle.Simple("Select pull request to view details",
                                                                                   "Can't load details")).apply {
      errorHandler = GHLoadingErrorHandlerImpl(project, dataContext.account) { dataProvider.reloadDetails() }
    }.also {
      (actionManager.getAction("Github.PullRequest.Details.Reload") as RefreshAction).registerCustomShortcutSet(it, disposable)
    }

    val changesBrowser = object : GHPRChangesBrowser(changesLoadingModel.cumulativeChangesModel, changesLoadingModel.diffHelper, project) {
      override fun createCenterPanel(): JComponent {
        val centerPanel = super.createCenterPanel()
        val panel = object : NonOpaquePanel(centerPanel), ComponentWithEmptyText {
          override fun getEmptyText() = viewer.emptyText
        }
        return GHLoadingPanel(changesLoadingModel, panel, disposable,
                              GHLoadingPanel.EmptyTextBundle.Simple("Select pull request to view changes",
                                                                    "Can't load changes",
                                                                    "Pull request does not contain any changes")).apply {
          errorHandler = GHLoadingErrorHandlerImpl(project, dataContext.account) { dataProvider.reloadChanges() }
        }
      }
    }


    return OnePixelSplitter(true, "Github.PullRequest.Info.Component", 0.5f).apply {
      isOpaque = true
      background = UIUtil.getListBackground()

      firstComponent = detailsLoadingPanel
      secondComponent = changesBrowser
    }.also {
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

  private fun createCommitsComponent(dataContext: GHPRDataContext,
                                     actionDataContext: GHPRActionDataContext,
                                     changesLoadingModel: GHPRChangesLoadingModel,
                                     disposable: Disposable): JComponent {

    val commitsModel = changesLoadingModel.commitsModel
    val commitsListModel = CollectionListModel(commitsModel.commitsWithChanges?.keys?.toList().orEmpty())
    commitsModel.addStateChangesListener {
      commitsListModel.replaceAll(commitsModel.commitsWithChanges?.keys?.toList().orEmpty())
    }

    val commitsList = JBList(commitsListModel).apply {
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      val renderer = GHPRCommitsListCellRenderer()
      cellRenderer = renderer
      UIUtil.putClientProperty(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(renderer.panel))
    }.also {
      ScrollingUtil.installActions(it)
      ListSpeedSearch(it) { commit ->
        commit.messageHeadlineHTML
      }
    }

    val commitDetailsModel = SingleValueModel<GHCommit?>(null)
    val commitDetailsComponent = createCommitDetailsComponent(commitDetailsModel)

    val changesModel = GHPRChangesModelImpl(project)
    val changesBrowser = GHPRChangesBrowser(changesModel, changesLoadingModel.diffHelper, project).apply {
      emptyText.text = "Select commit to view changes"
    }

    commitsList.addListSelectionListener { e ->
      if (e.valueIsAdjusting) return@addListSelectionListener
      changesModel.changes = commitsList.selectedValue?.let { commitsModel.commitsWithChanges?.get(it) }
    }

    val commitsScrollPane = ScrollPaneFactory.createScrollPane(commitsList, true).apply {
      isOpaque = false
      viewport.isOpaque = false
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    val commitsBrowser = object : OnePixelSplitter(true, "Github.PullRequest.Commits.Browser", 0.7f), ComponentWithEmptyText {
      override fun getEmptyText() = commitsList.emptyText
    }.apply {
      firstComponent = commitsScrollPane
      secondComponent = commitDetailsComponent
    }

    commitsList.addListSelectionListener { e ->
      if (e.valueIsAdjusting) return@addListSelectionListener

      val index = commitsList.selectedIndex
      commitDetailsModel.value = if (index != -1) commitsListModel.getElementAt(index) else null
      commitsBrowser.validate()
      commitsBrowser.repaint()
      if (index != -1) ScrollingUtil.ensureRangeIsVisible(commitsList, index, index)
    }

    val commitsLoadingPanel = GHLoadingPanel(changesLoadingModel, commitsBrowser, disposable,
                                             GHLoadingPanel.EmptyTextBundle.Simple("Select pull request to view commits",
                                                                                   "Can't load commits",
                                                                                   "Pull request does not contain any commits")).apply {
      errorHandler = GHLoadingErrorHandlerImpl(project, dataContext.account) { actionDataContext.pullRequestDataProvider.reloadChanges() }
    }

    return OnePixelSplitter(true, "Github.PullRequest.Commits.Component", 0.5f).apply {
      isOpaque = true
      background = UIUtil.getListBackground()

      firstComponent = commitsLoadingPanel
      secondComponent = changesBrowser
    }.also {
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

  private fun createCommitDetailsComponent(model: SingleValueModel<GHCommit?>): JComponent {
    val messagePane = HtmlEditorPane().apply {
      font = FontUtil.getCommitMessageFont()
    }
    //TODO: show avatar
    val hashAndAuthorPane = HtmlEditorPane().apply {
      font = FontUtil.getCommitMetadataFont()
    }

    val commitDetailsPanel = ScrollablePanel(VerticalLayout(CommitDetailsPanel.INTERNAL_BORDER)).apply {
      border = JBUI.Borders.empty(CommitDetailsPanel.EXTERNAL_BORDER, CommitDetailsPanel.SIDE_BORDER)
      background = getCommitDetailsBackground()

      add(messagePane, VerticalLayout.FILL_HORIZONTAL)
      add(hashAndAuthorPane, VerticalLayout.FILL_HORIZONTAL)
    }
    val commitDetailsScrollPane = ScrollPaneFactory.createScrollPane(commitDetailsPanel, true).apply {
      isVisible = false
      isOpaque = false
      viewport.isOpaque = false
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    model.addAndInvokeValueChangedListener {
      val commit = model.value

      if (commit == null) {
        messagePane.setBody("")
        hashAndAuthorPane.setBody("")
        commitDetailsScrollPane.isVisible = false
      }
      else {
        val subject = "<b>${commit.messageHeadlineHTML}</b>"
        val body = commit.messageBodyHTML
        val fullMessage = if (body.isNotEmpty()) "$subject<br><br>$body" else subject

        messagePane.setBody(fullMessage)
        hashAndAuthorPane.setBody(getHashAndAuthorText(commit.oid, commit.author, commit.committer))
        commitDetailsScrollPane.isVisible = true
        commitDetailsPanel.scrollRectToVisible(Rectangle(0, 0, 0, 0))
        invokeLater {
          // JDK bug - need to force height recalculation
          messagePane.setSize(messagePane.width, Int.MAX_VALUE)
          hashAndAuthorPane.setSize(hashAndAuthorPane.width, Int.MAX_VALUE)
        }
      }
    }

    return commitDetailsScrollPane
  }

  private fun getHashAndAuthorText(hash: String, author: GHGitActor?, committer: GHGitActor?): String {
    val authorUser = createUser(author)
    val authorTime = author?.date?.time ?: 0L
    val committerUser = createUser(committer)
    val committerTime = committer?.date?.time ?: 0L

    return CommitPresentationUtil.formatCommitHashAndAuthor(HashImpl.build(hash), authorUser, authorTime, committerUser, committerTime)
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

  private fun createChangesLoadingModel(commitsModel: GHPRCommitsModel,
                                        cumulativeChangesModel: GHPRChangesModel,
                                        diffHelper: GHPRChangesDiffHelper,
                                        dataProvider: GHPRDataProvider,
                                        disposable: Disposable): GHPRChangesLoadingModel {
    val model = GHPRChangesLoadingModel(commitsModel, cumulativeChangesModel, diffHelper).also {
      it.dataProvider = dataProvider
    }
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

  companion object {
    private val unknownUser = VcsUserImpl("unknown user", "")

    private fun createUser(actor: GHGitActor?): VcsUser {
      val name = actor?.name
      val email = actor?.email
      return if (name != null && email != null) {
        VcsUserImpl(name, email)
      }
      else unknownUser
    }
  }
}
