// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.CompletableFutureUtil.handleOnEdt
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.ComponentListPanelFactory
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHCommentTextFieldFactory
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHCommentTextFieldModel
import org.jetbrains.plugins.github.pullrequest.comment.ui.submitAction
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.component.GHHandledErrorPanelModel
import org.jetbrains.plugins.github.ui.component.GHHtmlErrorPanel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

internal class GHPRFileEditorComponentFactory(private val project: Project,
                                              private val timelineVm: GHPRTimelineViewModel,
                                              currentDetails: GHPullRequestShort,
                                              cs: CoroutineScope) {

  private val uiDisposable = cs.nestedDisposable()

  private val detailsModel = SingleValueModel(currentDetails)

  private val errorModel = GHHandledErrorPanelModel(GithubBundle.message("pull.request.timeline.cannot.load"),
                                                    GHApiLoadingErrorHandler(project,
                                                                             timelineVm.securityService.account,
                                                                             timelineVm.timelineLoader::reset))
  private val timelineModel = GHPRTimelineMergingModel()
  private val reviewThreadsModelsProvider = GHPRReviewsThreadsModelsProviderImpl(timelineVm.reviewData, uiDisposable)

  init {
    timelineVm.detailsData.loadDetails(uiDisposable) {
      it.handleOnEdt(uiDisposable) { pr, _ ->
        if (pr != null) detailsModel.value = pr
      }
    }

    timelineVm.timelineLoader.addErrorChangeListener(uiDisposable) {
      errorModel.error = timelineVm.timelineLoader.error
    }
    errorModel.error = timelineVm.timelineLoader.error

    timelineVm.timelineLoader.addDataListener(uiDisposable, object : GHListLoader.ListDataListener {
      override fun onDataAdded(startIdx: Int) {
        val loadedData = timelineVm.timelineLoader.loadedData
        timelineModel.add(loadedData.subList(startIdx, loadedData.size))
      }

      override fun onDataUpdated(idx: Int) {
        val loadedData = timelineVm.timelineLoader.loadedData
        val item = loadedData[idx]
        timelineModel.update(item)
      }

      override fun onDataRemoved(data: Any) {
        if (data !is GHPRTimelineItem) return
        timelineModel.remove(data)
      }

      override fun onAllDataRemoved() = timelineModel.removeAll()
    })
    timelineModel.add(timelineVm.timelineLoader.loadedData)
  }

  fun create(): JComponent {
    val mainPanel = Wrapper()

    val header = GHPRTitleComponentFactory.create(project, detailsModel).let {
      CollaborationToolsUIUtil.wrapWithLimitedSize(it, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH)
    }.apply {
      border = JBUI.Borders.empty(CodeReviewTimelineUIUtil.HEADER_VERT_PADDING, CodeReviewTimelineUIUtil.ITEM_HOR_PADDING)
    }

    val suggestedChangesHelper = GHPRSuggestedChangeHelper(project,
                                                           uiDisposable,
                                                           timelineVm.repositoryDataService.remoteCoordinates.repository,
                                                           timelineVm.reviewData,
                                                           timelineVm.detailsData)

    val itemComponentFactory = createItemComponentFactory(
      project,
      timelineVm.detailsData, timelineVm.commentsData, timelineVm.reviewData,
      reviewThreadsModelsProvider,
      timelineVm.htmlImageLoader, timelineVm.avatarIconsProvider,
      suggestedChangesHelper,
      timelineVm.securityService.ghostUser,
      timelineVm.securityService.currentUser
    )
    val descriptionWrapper = Wrapper().apply {
      isOpaque = false
    }
    detailsModel.addAndInvokeListener {
      descriptionWrapper.setContent(itemComponentFactory.createComponent(detailsModel.value))
    }

    val timeline = ComponentListPanelFactory.createVertical(timelineModel, componentFactory = itemComponentFactory)
    val timelineLoader = timelineVm.timelineLoader

    val progressAndErrorPanel = JPanel(ListLayout.vertical(0, ListLayout.Alignment.CENTER)).apply {
      isOpaque = false

      val errorPanel = GHHtmlErrorPanel.create(errorModel).apply {
        border = CodeReviewTimelineUIUtil.ITEM_BORDER
      }

      val loadingIcon = JLabel(AnimatedIcon.Default()).apply {
        border = CodeReviewTimelineUIUtil.ITEM_BORDER
        isVisible = timelineLoader.loading
      }
      timelineLoader.addLoadingStateChangeListener(uiDisposable) {
        loadingIcon.isVisible = timelineLoader.loading
      }

      add(errorPanel)
      add(loadingIcon)
    }.let {
      CollaborationToolsUIUtil
        .wrapWithLimitedSize(it, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH + CodeReviewTimelineUIUtil.ITEM_HOR_PADDING * 2)
    }

    val timelinePanel = VerticalListPanel().apply {
      border = JBUI.Borders.empty(CodeReviewTimelineUIUtil.VERT_PADDING, 0)

      add(header)
      add(descriptionWrapper)
      add(timeline)

      add(progressAndErrorPanel)

      if (timelineVm.securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.READ)) {
        val commentField = createCommentField(timelineVm.commentsData,
                                              timelineVm.avatarIconsProvider,
                                              timelineVm.securityService.currentUser).apply {
          border = JBUI.Borders.empty(CodeReviewChatItemUIUtil.ComponentType.FULL.inputPaddingInsets)
        }
        add(commentField)
      }
    }

    val scrollPane = ScrollPaneFactory.createScrollPane(timelinePanel, true).apply {
      isOpaque = false
      viewport.isOpaque = false
      verticalScrollBar.model.addChangeListener(object : ChangeListener {
        private var firstScroll = true

        override fun stateChanged(e: ChangeEvent) {
          if (firstScroll && verticalScrollBar.value > 0) firstScroll = false
          if (!firstScroll) {
            if (timelineLoader.canLoadMore()) {
              timelineLoader.loadMore()
            }
          }
        }
      })
    }
    UiNotifyConnector.doWhenFirstShown(scrollPane) {
      timelineLoader.loadMore()
    }

    timelineLoader.addDataListener(uiDisposable, object : GHListLoader.ListDataListener {
      override fun onAllDataRemoved() {
        if (scrollPane.isShowing) timelineLoader.loadMore()
      }
    })

    mainPanel.setContent(scrollPane)

    val ctrl = object : GHPRTimelineController {
      override fun requestUpdate() {
        timelineVm.detailsData.reloadDetails()
        timelineVm.timelineLoader.loadMore(true)
        timelineVm.reviewData.resetReviewThreads()
      }
    }

    DataManager.registerDataProvider(mainPanel, DataProvider {
      when {
        PlatformDataKeys.UI_DISPOSABLE.`is`(it) -> uiDisposable
        GHPRTimelineController.DATA_KEY.`is`(it) -> ctrl
        else -> null
      }
    })

    val actionManager = ActionManager.getInstance()
    actionManager.getAction("Github.PullRequest.Timeline.Update").registerCustomShortcutSet(scrollPane, uiDisposable)
    val groupId = "Github.PullRequest.Timeline.Popup"
    PopupHandler.installPopupMenu(scrollPane, groupId, ActionPlaces.POPUP)

    return mainPanel
  }

  private fun createCommentField(commentService: GHPRCommentsDataProvider,
                                 avatarIconsProvider: GHAvatarIconsProvider,
                                 currentUser: GHUser): JComponent {
    val model = GHCommentTextFieldModel(project) {
      commentService.addComment(EmptyProgressIndicator(), it)
    }

    val submitShortcutText = CommentInputActionsComponentFactory.submitShortcutText

    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = MutableStateFlow(model.submitAction(GithubBundle.message("action.comment.text"))),
      submitHint = MutableStateFlow(GithubBundle.message("pull.request.comment.hint", submitShortcutText))
    )
    val icon = CommentTextFieldFactory.IconConfig.of(CodeReviewChatItemUIUtil.ComponentType.FULL,
                                                     avatarIconsProvider, currentUser.avatarUrl)

    return GHCommentTextFieldFactory(model).create(actions, icon)
  }

  private fun createItemComponentFactory(project: Project,
                                         detailsDataProvider: GHPRDetailsDataProvider,
                                         commentsDataProvider: GHPRCommentsDataProvider,
                                         reviewDataProvider: GHPRReviewDataProvider,
                                         reviewThreadsModelsProvider: GHPRReviewsThreadsModelsProvider,
                                         htmlImageLoader: AsyncHtmlImageLoader,
                                         avatarIconsProvider: GHAvatarIconsProvider,
                                         suggestedChangeHelper: GHPRSuggestedChangeHelper,
                                         ghostUser: GHUser,
                                         currentUser: GHUser)
    : GHPRTimelineItemComponentFactory {

    val selectInToolWindowHelper = GHPRSelectInToolWindowHelper(project, detailsModel.value.prId)
    return GHPRTimelineItemComponentFactory(
      project,
      detailsDataProvider,
      commentsDataProvider,
      reviewDataProvider,
      htmlImageLoader,
      avatarIconsProvider,
      reviewThreadsModelsProvider,
      selectInToolWindowHelper,
      suggestedChangeHelper,
      ghostUser,
      detailsModel.value.author,
      currentUser
    )
  }
}