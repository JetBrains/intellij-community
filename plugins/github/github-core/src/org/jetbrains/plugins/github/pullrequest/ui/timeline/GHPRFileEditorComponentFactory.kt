// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.ComponentListPanelFactory
import com.intellij.collaboration.ui.EditableComponentFactory
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.VerticalListPanel
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
import com.intellij.collaboration.ui.util.bindIcon
import com.intellij.collaboration.ui.util.bindTextHtml
import com.intellij.collaboration.ui.util.bindTextHtmlIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.util.getOrNull
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColorUtil
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.IconUtil
import com.intellij.util.asDisposable
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.plugins.github.GithubIcons
import org.jetbrains.plugins.github.ai.GHPRAISummaryExtension
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsFull
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionsComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionsPickerComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineEventComponentFactoryImpl.Companion.branchHTML
import org.jetbrains.plugins.github.ui.component.GHHtmlErrorPanel
import org.jetbrains.plugins.github.ui.util.addGithubHyperlinkListener
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.event.HyperlinkEvent

internal class GHPRFileEditorComponentFactory(
  private val cs: CoroutineScope,
  private val project: Project,
  private val projectVm: GHPRConnectedProjectViewModel,
  private val timelineVm: GHPRTimelineViewModel,
  private val initialDetails: GHPRDetailsFull,
) {
  fun create(): JComponent {
    val mainPanel = Wrapper()
    val loadedDetails = timelineVm.detailsVm.details
      .map { it.getOrNull() }.filterNotNull().stateIn(cs, SharingStarted.Eagerly, initialDetails)

    val header = createTitle(loadedDetails)
    val description = createDescription(loadedDetails)
    val itemComponentFactory = createItemComponentFactory()

    val timelineEvents = ComponentListPanelFactory.createVertical(cs, timelineVm.timelineItems, componentFactory = itemComponentFactory)
    val mergedTimelineItem = Wrapper().apply {
      bindContent(
        debugName = "mergeActionChild",
        dataFlow = combine(timelineVm.detailsVm.isMerged, timelineVm.loadingError, ::Pair).distinctUntilChanged()
      ) { (isMerged, loadingError) ->
        if (isMerged && loadingError == null) createMergedTimelineItem(timelineVm.detailsVm) else null
      }
    }

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
          projectVm.acquireAISummaryViewModel(loadedDetails.value.id, cs),
          GHPRAISummaryExtension.singleFlow
        ) { summaryVm, extension ->
          summaryVm?.let { extension?.createTimelineComponent(project, it) }
        }
        bindVisibilityIn(cs, summaryComponent.map { it != null })
        bindContent("${javaClass.name}.summaryComponent.content", summaryComponent)
      })

      add(description)
      add(timelineEvents)

      add(progressAndErrorPanel)
      add(mergedTimelineItem)

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
    UiNotifyConnector.doWhenFirstShown(scrollPane) {
      timelineVm.requestMore()
    }

    mainPanel.setContent(scrollPane)

    DataManager.registerDataProvider(mainPanel, DataProvider {
      when {
        GHPRTimelineViewModel.DATA_KEY.`is`(it) -> timelineVm
        else -> null
      }
    })

    val actionManager = ActionManager.getInstance()
    actionManager.getAction("Github.PullRequest.Timeline.Update").registerCustomShortcutSet(scrollPane, cs.asDisposable())
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

    return CodeReviewCommentTextFieldFactory.createIn(cs, vm, actions, icon) { editor ->
      editor.putUserData(GHViewModelWithTextCompletion.MENTIONS_COMPLETION_KEY, vm)
    }
  }

  private fun createMergedTimelineItem(detailsVm: GHPRDetailsTimelineViewModel): JComponent {
    return CodeReviewChatItemUIUtil.build(
      type = CodeReviewChatItemUIUtil.ComponentType.FULL,
      iconProvider = { iconSize -> IconUtil.resizeSquared(GithubIcons.PullRequestMerged, iconSize) },
      content = HorizontalListPanel(gap = 4).apply {
        add(SimpleHtmlPane(addBrowserListener = false).apply {
          addHyperlinkListener { e ->
            if (e.eventType != HyperlinkEvent.EventType.ACTIVATED) return@addHyperlinkListener

            if (e.description == DELETE_ACTION_NAME) {
              detailsVm.deleteMergedBranch()
            }
            else if (e.description == RESTORE_ACTION_NAME) {
              val url = detailsVm.details.value.getOrNull()?.url ?: return@addHyperlinkListener
              BrowserUtil.browse(url)
            }
          }

          bindTextHtml(
            "mergedTimelineItem.body.text",
            combine(detailsVm.canDeleteMergedBranch, detailsVm.headRefName) { canDeleteMergedBranch, headRefName ->
              val branchHtml = headRefName?.let(::branchHTML) ?: "branch"
              if (canDeleteMergedBranch) {
                val deleteBranchHtml = GithubBundle.message("pullRequest.timeline.merged.body.delete")
                val link = HtmlChunk.link(DELETE_ACTION_NAME, HtmlChunk.raw(deleteBranchHtml))

                GithubBundle.message("pullRequest.timeline.merged.body.withDelete", link, branchHtml)
              }
              else {
                val restoreText = GithubBundle.message("pullRequest.timeline.merged.body.restore")
                val link = HtmlChunk.link(RESTORE_ACTION_NAME, HtmlChunk.raw(restoreText))

                GithubBundle.message("pullRequest.timeline.merged.body.withoutDelete", branchHtml, link)
              }
            }
          )
        })

        add(JLabel().apply {
          bindIcon("mergedTimelineItem.body.icon", detailsVm.canDeleteMergedBranch.map { canDeleteMergedBranch ->
            if (!canDeleteMergedBranch) AllIcons.Ide.External_link_arrow else null
          })
        })
      }
    ) {
      withHeader(JLabel(GithubBundle.message("pullRequest.timeline.merged.title")).apply {
        font = font.deriveFont(Font.BOLD)
      })
    }
  }

  private fun createItemComponentFactory() = GHPRTimelineItemComponentFactory(project, timelineVm)

  private val noDescriptionHtmlText by lazy {
    HtmlBuilder()
      .append(GithubBundle.message("pull.request.timeline.no.description"))
      .wrapWith(HtmlChunk.font(ColorUtil.toHex(UIUtil.getContextHelpForeground())))
      .wrapWith("i")
      .toString()
  }

  companion object {
    private const val RESTORE_ACTION_NAME = "restore"
    private const val DELETE_ACTION_NAME = "delete"
  }
}