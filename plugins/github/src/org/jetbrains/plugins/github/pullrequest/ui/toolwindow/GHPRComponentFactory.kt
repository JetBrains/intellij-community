// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.action.GHPRFixedActionDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRChangesDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandlerImpl
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesBrowserFactory
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesDiffHelper
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesDiffHelperImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDetailsModelImpl
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.handleOnEdt
import javax.swing.JComponent

internal class GHPRComponentFactory(private val project: Project) {

  @CalledInAwt
  fun createComponent(dataContext: GHPRDataContext, disposable: Disposable): JComponent {
    val wrapper = Wrapper()
    ContentController(dataContext, wrapper, disposable)
    return wrapper
  }

  private inner class ContentController(private val dataContext: GHPRDataContext,
                                        private val wrapper: Wrapper,
                                        private val parentDisposable: Disposable) : GHPRViewController {

    private val listComponent = GHPRListComponent.create(project, dataContext, parentDisposable)

    private var currentDisposable: Disposable? = null

    init {
      viewList()

      DataManager.registerDataProvider(wrapper) { dataId ->
        when {
          GHPRActionKeys.PULL_REQUESTS_CONTROLLER.`is`(dataId) -> this
          else -> null
        }
      }
    }

    override fun viewList() {
      currentDisposable?.let { Disposer.dispose(it) }
      wrapper.setContent(listComponent)
      wrapper.repaint()
    }

    override fun refreshList() {
      dataContext.listLoader.reset()
      dataContext.repositoryDataService.resetData()
    }

    override fun viewPullRequest(id: GHPRIdentifier) {
      currentDisposable?.let { Disposer.dispose(it) }
      currentDisposable = Disposer.newDisposable("Pull request component disposable").also {
        Disposer.register(parentDisposable, it)
      }
      val dataProvider = dataContext.dataProviderRepository.getDataProvider(id, currentDisposable!!)
      val pullRequestComponent =
        createPullRequestComponent(dataContext, dataProvider, id, this, currentDisposable!!)
      wrapper.setContent(pullRequestComponent)
      wrapper.repaint()
      GithubUIUtil.focusPanel(wrapper)
    }

    override fun openPullRequestTimeline(id: GHPRIdentifier, requestFocus: Boolean) {
      dataContext.filesManager.createAndOpenFile(id, requestFocus)
    }
  }

  private fun createPullRequestComponent(dataContext: GHPRDataContext,
                                         dataProvider: GHPRDataProvider,
                                         pullRequest: GHPRIdentifier,
                                         viewController: GHPRViewController,
                                         disposable: Disposable): JComponent {

    val infoComponent = createInfoComponent(dataContext, dataProvider, disposable)
    val commitsComponent = createCommitsComponent(dataContext, dataProvider, disposable)

    val infoTabInfo = TabInfo(infoComponent).apply {
      text = GithubBundle.message("pull.request.info")
      sideComponent = createReturnToListSideComponent(viewController)
    }
    val commitsTabInfo = TabInfo(commitsComponent).apply {
      text = GithubBundle.message("pull.request.commits")
      sideComponent = createReturnToListSideComponent(viewController)
    }

    dataProvider.changesData.loadCommitsFromApi(disposable) {
      it.handleOnEdt(disposable) { commits, _ ->
        val commitsCount = commits?.size
        commitsTabInfo.text = if (commitsCount == null) GithubBundle.message("pull.request.commits")
        else GithubBundle.message("pull.request.commits.count", commitsCount)
      }
    }

    return object : SingleHeightTabs(project, disposable) {
      override fun adjust(each: TabInfo?) {}
    }.apply {
      val actionDataContext = GHPRFixedActionDataContext(dataContext, pullRequest, dataProvider)
      val diffHelper = createDiffHelper(dataContext, dataProvider, disposable)
      setDataProvider { dataId ->
        when {
          GHPRActionKeys.ACTION_DATA_CONTEXT.`is`(dataId) -> actionDataContext
          GHPRChangesDiffHelper.DATA_KEY.`is`(dataId) -> diffHelper
          else -> null
        }
      }
      addTab(infoTabInfo)
      addTab(commitsTabInfo)
    }
  }

  private fun createReturnToListSideComponent(viewController: GHPRViewController): JComponent {
    return BorderLayoutPanel()
      .addToRight(LinkLabel<Any>(GithubBundle.message("pull.request.back.to.list"), AllIcons.Actions.Back) { _, _ ->
        viewController.viewList()
      }.apply {
        border = JBUI.Borders.emptyRight(8)
      })
      .andTransparent()
      .withBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM))
  }

  private fun createInfoComponent(dataContext: GHPRDataContext, dataProvider: GHPRDataProvider, disposable: Disposable): JComponent {
    val detailsLoadingModel = createDetailsLoadingModel(dataProvider.detailsData, disposable)
    val detailsLoadingPanel = GHLoadingPanelFactory(detailsLoadingModel,
                                                    null, GithubBundle.message("cannot.load.details"),
                                                    GHLoadingErrorHandlerImpl(project, dataContext.securityService.account) {
                                                      dataProvider.detailsData.reloadDetails()
                                                    }).createWithUpdatesStripe(disposable) { _, model ->
      val detailsModel = GHPRDetailsModelImpl(model,
                                              dataContext.securityService,
                                              dataContext.repositoryDataService,
                                              dataProvider.detailsData)
      GHPRDetailsComponent.create(detailsModel, dataContext.avatarIconsProviderFactory)
    }.also {
      ActionManager.getInstance().getAction("Github.PullRequest.Details.Reload").registerCustomShortcutSet(it, disposable)
    }

    val changesLoadingModel = GHCompletableFutureLoadingModel<List<Change>>(disposable)
    dataProvider.changesData.loadChanges(disposable) { future ->
      changesLoadingModel.future = future.thenApply { it.changes }
    }
    val changesLoadingErrorHandler = GHLoadingErrorHandlerImpl(project, dataContext.securityService.account) {
      dataProvider.changesData.reloadChanges()
    }

    val changesBrowserFactory = GHPRChangesBrowserFactory(ActionManager.getInstance(), project)
    val changesLoadingPanel = GHLoadingPanelFactory(changesLoadingModel, null,
                                                    GithubBundle.message("cannot.load.changes"),
                                                    changesLoadingErrorHandler)
      .createWithUpdatesStripe(disposable) { parent, model ->
        val tree = changesBrowserFactory.createTree(parent, model).apply {
          emptyText.text = GithubBundle.message("pull.request.does.not.contain.changes")
        }
        ScrollPaneFactory.createScrollPane(tree, true)
      }.apply {
        border = IdeBorderFactory.createBorder(SideBorder.TOP)
      }
    val toolbar = changesBrowserFactory.createToolbar(changesLoadingPanel)
    val changesBrowser = BorderLayoutPanel().andTransparent()
      .addToTop(toolbar)
      .addToCenter(changesLoadingPanel)

    return OnePixelSplitter(true, "Github.PullRequest.Info.Component", 0.33f).apply {
      isOpaque = true
      background = UIUtil.getListBackground()

      firstComponent = detailsLoadingPanel
      secondComponent = changesBrowser
    }
  }

  private fun createCommitsComponent(dataContext: GHPRDataContext, dataProvider: GHPRDataProvider, disposable: Disposable): JComponent {

    val commitsLoadingModel = GHCompletableFutureLoadingModel<List<GHCommit>>(disposable)
    dataProvider.changesData.loadCommitsFromApi(disposable) { future ->
      commitsLoadingModel.future = future
    }
    val changesLoadingModel = GHCompletableFutureLoadingModel<List<Change>>(disposable)
    val changesLoadingErrorHandler = GHLoadingErrorHandlerImpl(project, dataContext.securityService.account) {
      dataProvider.changesData.reloadChanges()
    }

    val commitSelectionListener = CommitsSelectionListener(changesLoadingModel, dataProvider.changesData).also {
      Disposer.register(disposable, it)
    }

    val commitsLoadingPanel = GHLoadingPanelFactory(commitsLoadingModel,
                                                    null, GithubBundle.message("cannot.load.commits"),
                                                    changesLoadingErrorHandler)
      .createWithUpdatesStripe(disposable) { _, model ->
        GHPRCommitsBrowserComponent.create(model, commitSelectionListener)
      }

    val changesBrowserFactory = GHPRChangesBrowserFactory(ActionManager.getInstance(), project)
    val changesLoadingPanel = GHLoadingPanelFactory(changesLoadingModel,
                                                    GithubBundle.message("pull.request.select.commit.to.view.changes"),
                                                    GithubBundle.message("cannot.load.changes"),
                                                    changesLoadingErrorHandler)
      .createWithModel { parent, model ->
        val tree = changesBrowserFactory.createTree(parent, model).apply {
          emptyText.text = GithubBundle.message("pull.request.commit.does.not.contain.changes")
        }
        ScrollPaneFactory.createScrollPane(tree, true)
      }.apply {
        border = IdeBorderFactory.createBorder(SideBorder.TOP)
      }
    val toolbar = changesBrowserFactory.createToolbar(changesLoadingPanel)
    val changesBrowser = BorderLayoutPanel().andTransparent()
      .addToTop(toolbar)
      .addToCenter(changesLoadingPanel)

    return OnePixelSplitter(true, "Github.PullRequest.Commits.Component", 0.4f).apply {
      isOpaque = true
      background = UIUtil.getListBackground()

      firstComponent = commitsLoadingPanel
      secondComponent = changesBrowser
    }.also {
      ActionManager.getInstance().getAction("Github.PullRequest.Changes.Reload").registerCustomShortcutSet(it, disposable)
    }
  }

  private class CommitsSelectionListener(private val changesLoadingModel: GHCompletableFutureLoadingModel<List<Change>>,
                                         private val changesData: GHPRChangesDataProvider)
    : (GHCommit?) -> Unit, Disposable {

    private var currentDisposable: Disposable? = null

    override fun invoke(commit: GHCommit?) {
      if (Disposer.isDisposed(this)) return
      currentDisposable?.let { Disposer.dispose(it) }
      changesLoadingModel.future = null
      if (commit != null) {
        val disposable = Disposer.newDisposable()
        currentDisposable = disposable
        changesData.loadChanges(disposable) { future ->
          changesLoadingModel.future = future.thenApply { it.changesByCommits[commit] }
        }
      }
    }

    override fun dispose() {
      currentDisposable?.let { Disposer.dispose(it) }
    }
  }

  private fun createDiffHelper(dataContext: GHPRDataContext, dataProvider: GHPRDataProvider, disposable: Disposable)
    : GHPRChangesDiffHelper {
    val diffHelper = GHPRChangesDiffHelperImpl(dataProvider.reviewData,
                                               dataContext.avatarIconsProviderFactory,
                                               dataContext.securityService.currentUser)
    dataProvider.changesData.loadChanges(disposable) { future ->
      future.handleOnEdt(disposable) { changes, _ ->
        if (changes != null) diffHelper.setUp(changes) else diffHelper.reset()
      }
    }
    return diffHelper
  }

  private fun createDetailsLoadingModel(detailsProvider: GHPRDetailsDataProvider,
                                        parentDisposable: Disposable): GHCompletableFutureLoadingModel<GHPullRequest> {
    val model = GHCompletableFutureLoadingModel<GHPullRequest>(parentDisposable)
    detailsProvider.loadDetails(parentDisposable) {
      model.future = it
    }
    return model
  }
}
