// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SideBorder
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionDataContext
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.action.GHPRFixedActionDataContext
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRChangesDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandlerImpl
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanel
import org.jetbrains.plugins.github.pullrequest.ui.GHSimpleLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.changes.*
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDetailsModelImpl
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.util.function.Consumer
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
                                        private val parentDisposable: Disposable) {

    private val avatarIconsProviderFactory =
      CachingGithubAvatarIconsProvider.Factory(CachingGithubUserAvatarLoader.getInstance(), GithubImageResizer.getInstance(),
                                               dataContext.requestExecutor)

    private val listComponent = GHPRListComponent.create(project, dataContext, avatarIconsProviderFactory, parentDisposable)

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
                                                            details,
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
                                         details: GHPullRequestShort,
                                         returnToListListener: () -> Unit,
                                         avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                         disposable: Disposable): JComponent {

    val detailsLoadingModel = createDetailsLoadingModel(dataProvider.detailsData, disposable)

    val commitsModel = GHPRCommitsModelImpl()
    val cumulativeChangesModel = GHPRChangesModelImpl(project)
    val diffHelper = GHPRChangesDiffHelperImpl(dataProvider.reviewData, avatarIconsProviderFactory, dataContext.securityService.currentUser)
    val changesLoadingModel = createChangesLoadingModel(commitsModel, cumulativeChangesModel, diffHelper, dataProvider.changesData,
                                                        disposable)

    val actionDataContext = GHPRFixedActionDataContext(dataContext, dataProvider, avatarIconsProviderFactory) {
      detailsLoadingModel.result ?: details
    }

    val infoComponent = createInfoComponent(dataContext, actionDataContext, detailsLoadingModel, changesLoadingModel, disposable)
    val commitsComponent = createCommitsComponent(dataContext, actionDataContext, changesLoadingModel, disposable)

    val infoTabInfo = TabInfo(infoComponent).apply {
      text = GithubBundle.message("pull.request.info")
      sideComponent = createReturnToListSideComponent(returnToListListener)
    }
    val commitsTabInfo = TabInfo(commitsComponent).apply {
      text = GithubBundle.message("pull.request.commits")
      sideComponent = createReturnToListSideComponent(returnToListListener)
    }

    fun updateCommitsTabText() {
      val commitsCount = commitsModel.commitsWithChanges?.size
      commitsTabInfo.text = if (commitsCount == null) GithubBundle.message("pull.request.commits")
      else GithubBundle.message("pull.request.commits.count", commitsCount)
    }
    updateCommitsTabText()
    commitsModel.addStateChangesListener(::updateCommitsTabText)

    return object : SingleHeightTabs(project, disposable) {
      override fun adjust(each: TabInfo?) {}
    }.apply {
      addTab(infoTabInfo)
      addTab(commitsTabInfo)
    }
  }

  private fun createReturnToListSideComponent(returnToListListener: () -> Unit): JComponent {
    return BorderLayoutPanel()
      .addToRight(LinkLabel<Any>(GithubBundle.message("pull.request.back.to.list"), AllIcons.Actions.Back) { _, _ ->
        returnToListListener()
      }.apply {
        border = JBUI.Borders.emptyRight(8)
      })
      .andTransparent()
      .withBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM))
  }

  private fun createInfoComponent(dataContext: GHPRDataContext,
                                  actionDataContext: GHPRActionDataContext,
                                  detailsLoadingModel: GHSimpleLoadingModel<GHPullRequest>,
                                  changesLoadingModel: GHPRChangesLoadingModel,
                                  disposable: Disposable): JComponent {
    val dataProvider = actionDataContext.pullRequestDataProvider

    val detailsLoadingPanel = GHLoadingPanel.create(detailsLoadingModel, {
      val detailsModel = GHPRDetailsModelImpl(detailsLoadingModel,
                                              dataContext.securityService,
                                              dataContext.repositoryDataService,
                                              dataProvider.detailsData)
      GHPRDetailsComponent.create(detailsModel, actionDataContext.avatarIconsProviderFactory)
    },
                                                    disposable,
                                                    GithubBundle.message("cannot.load.details"),
                                                    GHLoadingErrorHandlerImpl(project, dataContext.account) {
                                                      dataProvider.detailsData.reloadDetails()
                                                    }).also {
      ActionManager.getInstance().getAction("Github.PullRequest.Details.Reload").registerCustomShortcutSet(it, disposable)
    }

    val changesBrowser = GHPRChangesBrowser.create(project,
                                                   changesLoadingModel,
                                                   changesLoadingModel.cumulativeChangesModel,
                                                   changesLoadingModel.diffHelper,
                                                   GithubBundle.message("pull.request.does.not.contain.changes"),
                                                   disposable)

    return OnePixelSplitter(true, "Github.PullRequest.Info.Component", 0.33f).apply {
      isOpaque = true
      background = UIUtil.getListBackground()

      firstComponent = detailsLoadingPanel
      secondComponent = changesBrowser
    }.also {
      DataManager.registerDataProvider(it) { dataId ->
        if (Disposer.isDisposed(disposable)) null
        else when {
          GHPRActionKeys.ACTION_DATA_CONTEXT.`is`(dataId) -> GHPRFixedActionDataContext(dataContext, dataProvider,
                                                                                        actionDataContext.avatarIconsProviderFactory) {
            detailsLoadingModel.result ?: actionDataContext.pullRequestDetails
          }
          else -> null
        }
      }
    }
  }

  private fun createCommitsComponent(dataContext: GHPRDataContext,
                                     actionDataContext: GHPRActionDataContext,
                                     changesLoadingModel: GHPRChangesLoadingModel,
                                     disposable: Disposable): JComponent {

    val changesModel = GHPRChangesModelImpl(project)

    val commitsLoadingPanel = GHLoadingPanel.create(changesLoadingModel, {
      GHPRCommitsBrowserComponent.create(changesLoadingModel.commitsModel, changesModel)
    },
                                                    disposable,
                                                    GithubBundle.message("cannot.load.commits"),
                                                    GHLoadingErrorHandlerImpl(project, dataContext.account) {
                                                      actionDataContext.pullRequestDataProvider.changesData.reloadChanges()
                                                    })

    val changesBrowser = GHPRChangesBrowser.create(project,
                                                   changesModel,
                                                   changesLoadingModel.diffHelper,
                                                   GithubBundle.message("pull.request.select.commit.to.view.changes"),
                                                   disposable)

    return OnePixelSplitter(true, "Github.PullRequest.Commits.Component", 0.4f).apply {
      isOpaque = true
      background = UIUtil.getListBackground()

      firstComponent = commitsLoadingPanel
      secondComponent = changesBrowser
    }.also {
      ActionManager.getInstance().getAction("Github.PullRequest.Changes.Reload").registerCustomShortcutSet(it, disposable)
      DataManager.registerDataProvider(it) { dataId ->
        if (Disposer.isDisposed(disposable)) null
        else when {
          GHPRActionKeys.ACTION_DATA_CONTEXT.`is`(dataId) -> actionDataContext
          else -> null
        }
      }
    }
  }

  private fun createChangesLoadingModel(commitsModel: GHPRCommitsModel,
                                        cumulativeChangesModel: GHPRChangesModel,
                                        diffHelper: GHPRChangesDiffHelper,
                                        changesProvider: GHPRChangesDataProvider,
                                        disposable: Disposable): GHPRChangesLoadingModel {
    val model = GHPRChangesLoadingModel(commitsModel, cumulativeChangesModel, diffHelper, disposable)
    changesProvider.loadChanged(disposable) {
      model.future = it
    }
    return model
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
