// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.CommonBundle
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.*
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTitleUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.comment.submitActionIn
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.util.bindTextHtmlIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.collaboration.util.getOrNull
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColorUtil
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsFull
import org.jetbrains.plugins.github.ui.component.GHHandledErrorPanelModel
import org.jetbrains.plugins.github.ui.component.GHHtmlErrorPanel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

internal class GHPRFileEditorComponentFactory(private val project: Project,
                                              private val timelineVm: GHPRTimelineViewModel,
                                              private val initialDetails: GHPRDetailsFull,
                                              private val cs: CoroutineScope) {

  private val uiDisposable = cs.nestedDisposable()

  private val errorModel = GHHandledErrorPanelModel(GithubBundle.message("pull.request.timeline.cannot.load"),
                                                    timelineVm.loadingErrorHandler)
  private val timelineModel = GHPRTimelineMergingModel()

  init {
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
    val loadedDetails = timelineVm.detailsVm.details
      .map { it.getOrNull() }.filterNotNull().stateIn(cs, SharingStarted.Eagerly, initialDetails)

    val header = createTitle(loadedDetails)
    val description = createDescription(loadedDetails)
    val itemComponentFactory = createItemComponentFactory()

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
      add(description)
      add(timeline)

      add(progressAndErrorPanel)

      timelineVm.commentVm?.also {
        val commentTextField = createCommentField(it).apply {
          border = JBUI.Borders.empty(CodeReviewChatItemUIUtil.ComponentType.FULL.inputPaddingInsets)
        }
        add(commentTextField)
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

    DataManager.registerDataProvider(mainPanel, DataProvider {
      when {
        PlatformDataKeys.UI_DISPOSABLE.`is`(it) -> uiDisposable
        GHPRTimelineViewModel.DATA_KEY.`is`(it) -> timelineVm
        else -> null
      }
    })

    val actionManager = ActionManager.getInstance()
    actionManager.getAction("Github.PullRequest.Timeline.Update").registerCustomShortcutSet(scrollPane, uiDisposable)
    val groupId = "Github.PullRequest.Timeline.Popup"
    PopupHandler.installPopupMenu(scrollPane, groupId, ActionPlaces.POPUP)

    return mainPanel
  }

  private fun createTitle(loadedDetailsState: StateFlow<GHPRDetailsFull>): JComponent {
    val titlePane = SimpleHtmlPane().apply {
      font = JBFont.h2().asBold()

      bindTextHtmlIn(cs, loadedDetailsState.map { details ->
        CodeReviewTitleUIUtil.createTitleText(
          title = details.titleHtml,
          reviewNumber = "#${details.id.number}",
          url = details.url,
          tooltip = GithubBundle.message("open.on.github.action")
        )
      })
    }
    val header = titlePane.let {
      CollaborationToolsUIUtil.wrapWithLimitedSize(it, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH)
    }.apply {
      border = JBUI.Borders.empty(CodeReviewTimelineUIUtil.HEADER_VERT_PADDING, CodeReviewTimelineUIUtil.ITEM_HOR_PADDING)
    }
    return header
  }

  private fun createDescription(loadedDetailsState: StateFlow<GHPRDetailsFull>): JComponent {
    val canEdit = loadedDetailsState.value.canEditDescription
    val author = loadedDetailsState.value.author
    val createdAt = loadedDetailsState.value.createdAt

    val textPane = SimpleHtmlPane(customImageLoader = timelineVm.htmlImageLoader).apply {
      bindTextIn(cs, loadedDetailsState.mapState { it.descriptionHtml ?: noDescriptionHtmlText })
    }
    val detailsVm = timelineVm.detailsVm
    val contentPane = EditableComponentFactory.create(cs, textPane, detailsVm.descriptionEditVm) { editVm ->
      val actions = createEditActionsConfig(editVm)
      val editor = CodeReviewCommentTextFieldFactory.createIn(this, editVm, actions)
      editVm.requestFocus()
      editor
    }

    val actionsPanel = if (canEdit) HorizontalListPanel(CodeReviewCommentUIUtil.Actions.HORIZONTAL_GAP).apply {
      add(CodeReviewCommentUIUtil.createEditButton {
        detailsVm.editDescription()
      })
    }
    else null

    return GHPRTimelineItemUIUtil.createTimelineItem(timelineVm.avatarIconsProvider, author, createdAt, contentPane, actionsPanel)
  }

  private fun createCommentField(vm: GHPRNewCommentViewModel): JComponent {
    val submitShortcutText = CommentInputActionsComponentFactory.submitShortcutText
    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = MutableStateFlow(vm.submitActionIn(cs, GithubBundle.message("action.comment.text"), GHPRNewCommentViewModel::submit)),
      submitHint = MutableStateFlow(GithubBundle.message("pull.request.comment.hint", submitShortcutText))
    )
    val icon = CommentTextFieldFactory.IconConfig.of(CodeReviewChatItemUIUtil.ComponentType.FULL,
                                                     timelineVm.avatarIconsProvider, timelineVm.currentUser.avatarUrl)

    return CodeReviewCommentTextFieldFactory.createIn(cs, vm, actions, icon)
  }

  private fun createItemComponentFactory(): GHPRTimelineItemComponentFactory {
    val reviewThreadsModelsProvider = GHPRReviewsThreadsModelsProviderImpl(timelineVm.reviewData, uiDisposable)
    val suggestedChangesHelper = GHPRSuggestedChangeHelper(project,
                                                           uiDisposable,
                                                           timelineVm.repository,
                                                           timelineVm.reviewData,
                                                           timelineVm.detailsData)
    val selectInToolWindowHelper = GHPRSelectInToolWindowHelper(project, timelineVm.prId)
    return GHPRTimelineItemComponentFactory(
      project,
      timelineVm.commentsData,
      timelineVm.reviewData,
      timelineVm.htmlImageLoader,
      timelineVm.avatarIconsProvider,
      reviewThreadsModelsProvider,
      selectInToolWindowHelper,
      suggestedChangesHelper,
      timelineVm.ghostUser,
      initialDetails.author,
      timelineVm.currentUser
    )
  }

  private val noDescriptionHtmlText by lazy {
    HtmlBuilder()
      .append(GithubBundle.message("pull.request.timeline.no.description"))
      .wrapWith(HtmlChunk.font(ColorUtil.toHex(UIUtil.getContextHelpForeground())))
      .wrapWith("i")
      .toString()
  }

  private fun CoroutineScope.createEditActionsConfig(editVm: GHPREditDescriptionViewModel): CommentInputActionsComponentFactory.Config =
    CommentInputActionsComponentFactory.Config(
      primaryAction = MutableStateFlow(editVm.submitActionIn(this, CollaborationToolsBundle.message("review.comment.save")) {
        save()
      }),
      cancelAction = MutableStateFlow(swingAction(CommonBundle.getCancelButtonText()) {
        editVm.cancelEditing()
      }),
      submitHint = MutableStateFlow(CollaborationToolsBundle.message("review.comment.save.hint",
                                                                     CommentInputActionsComponentFactory.submitShortcutText))
    )
}