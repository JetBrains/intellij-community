// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.*
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTitleUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.comment.submitActionIn
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.util.bindContent
import com.intellij.collaboration.ui.util.bindTextHtmlIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
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
import org.jetbrains.plugins.github.ai.GHPRAISummaryExtension
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsFull
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionsComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionsPickerComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowProjectViewModel
import org.jetbrains.plugins.github.ui.component.GHHtmlErrorPanel
import org.jetbrains.plugins.github.ui.util.addGithubHyperlinkListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

internal class GHPRFileEditorComponentFactory(
  private val cs: CoroutineScope,
  private val project: Project,
  private val projectVm: GHPRToolWindowProjectViewModel,
  private val timelineVm: GHPRTimelineViewModel,
  private val initialDetails: GHPRDetailsFull,
) {

  private val uiDisposable = cs.nestedDisposable()

  fun create(): JComponent {
    val mainPanel = Wrapper()
    val loadedDetails = timelineVm.detailsVm.details
      .map { it.getOrNull() }.filterNotNull().stateIn(cs, SharingStarted.Eagerly, initialDetails)

    val header = createTitle(loadedDetails)
    val description = createDescription(loadedDetails)
    val itemComponentFactory = createItemComponentFactory()

    val timeline = ComponentListPanelFactory.createVertical(cs, timelineVm.timelineItems, componentFactory = itemComponentFactory)

    val progressAndErrorPanel = JPanel(ListLayout.vertical(0, ListLayout.Alignment.CENTER)).apply {
      isOpaque = false
      val errorPanel = ErrorStatusPanelFactory.create(cs, timelineVm.loadingError, ErrorStatusPresenter.simple(
        GithubBundle.message("pull.request.timeline.cannot.load"),
        descriptionProvider = { error ->
          if (error is GithubAuthenticationException) GithubBundle.message("pull.request.list.error.authorization")
          else GHHtmlErrorPanel.getLoadingErrorText(error)
        },
        actionProvider = timelineVm.loadingErrorHandler::getActionForError
      ))

      val loadingIcon = JLabel(AnimatedIcon.Default()).apply {
        border = CodeReviewTimelineUIUtil.ITEM_BORDER
        isVisible = false
        bindVisibilityIn(cs, timelineVm.isLoading)
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

      add(Wrapper().apply {
        val summaryComponent = combine(
          projectVm.acquireAISummaryViewModel(loadedDetails.value.id, uiDisposable),
          GHPRAISummaryExtension.singleFlow
        ) { summaryVm, extension ->
          summaryVm?.let { extension?.createTimelineComponent(project, it) }
        }
        bindVisibilityIn(cs, summaryComponent.map { it != null })
        bindContent("${javaClass.name}.summaryComponent.content", summaryComponent)
      })

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
            timelineVm.requestMore()
          }
        }
      })
    }
    UiNotifyConnector.doWhenFirstShown(scrollPane)
    {
      timelineVm.requestMore()
    }

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
    val canReact = loadedDetailsState.value.canReactDescription
    val author = loadedDetailsState.value.author
    val createdAt = loadedDetailsState.value.createdAt

    val textPane = SimpleHtmlPane(customImageLoader = timelineVm.htmlImageLoader, addBrowserListener = false).apply {
      addGithubHyperlinkListener(projectVm::openPullRequestInfoAndTimeline)
      bindTextIn(cs, loadedDetailsState.mapState { it.descriptionHtml ?: noDescriptionHtmlText })
    }

    val reactionsVm = timelineVm.detailsVm.reactionsVm
    val contentPane = VerticalListPanel(CodeReviewTimelineUIUtil.VERTICAL_GAP).apply {
      add(EditableComponentFactory.wrapTextComponent(cs, textPane, timelineVm.detailsVm.descriptionEditVm))
      add(GHReactionsComponentFactory.create(cs, reactionsVm))
    }
    val actionsPanel = HorizontalListPanel(CodeReviewCommentUIUtil.Actions.HORIZONTAL_GAP).apply {
      if (canEdit) {
        add(CodeReviewCommentUIUtil.createEditButton {
          timelineVm.detailsVm.editDescription()
        })
      }
      if (canReact) {
        add(CodeReviewCommentUIUtil.createAddReactionButton {
          val parentComponent = it.source as JComponent
          GHReactionsPickerComponentFactory.showPopup(reactionsVm, parentComponent)
        })
      }
    }

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

  private fun createItemComponentFactory() = GHPRTimelineItemComponentFactory(project, timelineVm)

  private val noDescriptionHtmlText by lazy {
    HtmlBuilder()
      .append(GithubBundle.message("pull.request.timeline.no.description"))
      .wrapWith(HtmlChunk.font(ColorUtil.toHex(UIUtil.getContextHelpForeground())))
      .wrapWith("i")
      .toString()
  }
}