// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.inverted
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.ui.*
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.wrapWithLimitedSize
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.ComponentType
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil.Thread.Replies
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.timeline.TimelineDiffComponentFactory
import com.intellij.collaboration.ui.codereview.user.CodeReviewUser
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.collaboration.ui.util.bindChildIn
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.util.exceptionOrNull
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.data.GitLabImageLoader
import org.jetbrains.plugins.gitlab.mergerequest.data.filePath
import org.jetbrains.plugins.gitlab.mergerequest.ui.emoji.GitLabReactionsComponentFactory
import org.jetbrains.plugins.gitlab.mergerequest.util.localizedMessageOrClassName
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionComponentFactory
import org.jetbrains.plugins.gitlab.ui.comment.GitLabEditableComponentFactory
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteComponentFactory
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent

@OptIn(ExperimentalCoroutinesApi::class)
internal object GitLabMergeRequestTimelineDiscussionComponentFactory {
  private val ACTION_PLACE = GitLabStatistics.MergeRequestNoteActionPlace.TIMELINE

  fun createIn(project: Project,
               cs: CoroutineScope,
               vm: GitLabMergeRequestTimelineItemViewModel.Discussion,
               avatarIconsProvider: IconsProvider<GitLabUserDTO>,
               imageLoader: GitLabImageLoader): JComponent =
    VerticalListPanel().apply {
      name = "GitLab Discussion Panel ${vm.id}"
      add(createDiscussionItemIn(project, cs, vm, avatarIconsProvider, imageLoader))
      add(createRepliesPanelIn(project, cs, vm, avatarIconsProvider, imageLoader))
    }

  fun createIn(project: Project,
               cs: CoroutineScope,
               vm: GitLabMergeRequestTimelineItemViewModel.DraftNote,
               avatarIconsProvider: IconsProvider<GitLabUserDTO>,
               imageLoader: GitLabImageLoader): JComponent {
    val contentPanel = createContentIn(project, cs, vm, imageLoader)
    val mainItem = CodeReviewChatItemUIUtil.build(ComponentType.FULL,
                                                  { avatarIconsProvider.getIcon(vm.author, it) },
                                                  contentPanel) {
      maxContentWidth = null

      val titlePanel = GitLabNoteComponentFactory.createTitle(cs, vm, project, ACTION_PLACE)
      val actionsPanel = GitLabNoteComponentFactory.createActions(cs, flowOf(vm), project, ACTION_PLACE)
      withHeader(titlePanel, actionsPanel)
    }
    return mainItem.apply {
      name = "GitLab Draft Discussion Panel ${vm.id}"
    }
  }

  private fun createDiscussionItemIn(project: Project,
                                     cs: CoroutineScope,
                                     vm: GitLabMergeRequestTimelineDiscussionViewModel,
                                     avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                                     imageLoader: GitLabImageLoader,
                                     ): JComponent {
    val contentPanel = createContentIn(project, cs, vm, avatarIconsProvider, imageLoader)
    val mainItem = CodeReviewChatItemUIUtil.buildDynamic(ComponentType.FULL,
                                                         { vm.author.createIconValue(cs, avatarIconsProvider, it) },
                                                         contentPanel) {
      maxContentWidth = null

      val titlePanel = Wrapper().apply {
        bindContentIn(cs, vm.mainNote) { mainNote ->
          GitLabNoteComponentFactory.createTitle(this, mainNote, project, ACTION_PLACE)
        }
      }

      val actionsPanel = GitLabNoteComponentFactory.createActions(cs, vm.mainNote, project, ACTION_PLACE)
      withHeader(titlePanel, actionsPanel)
    }
    return mainItem
  }

  private fun createContentIn(project: Project,
                              cs: CoroutineScope,
                              vm: GitLabMergeRequestTimelineDiscussionViewModel,
                              avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                              imageLoader: GitLabImageLoader,): JPanel {
    val mainNoteVm = vm.mainNote
    val repliesActionsPanel = createRepliesActionsPanel(cs, avatarIconsProvider, vm).apply {
      border = JBUI.Borders.empty(Replies.ActionsFolded.VERTICAL_PADDING, 0)
      bindVisibilityIn(cs, vm.repliesFolded)
    }
    val textPanel = createDiscussionTextPane(project, cs, vm, imageLoader)

    val editVmFlow = mainNoteVm.flatMapLatest { it.actionsVm?.editVm ?: flowOf(null) }
    val textContentPanel = GitLabEditableComponentFactory.wrapTextComponent(cs, textPanel, editVmFlow) {
      GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.UPDATE_NOTE,
                                           ACTION_PLACE)
    }.let {
      wrapWithLimitedSize(it, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH)
    }

    val diffPanelFlow = vm.diffVm.mapScoped { diffVm ->
      diffVm?.let {
        val fileNameClickHandler = diffVm.showDiffHandler.asActionHandler()
        TimelineDiffComponentFactory.createDiffWithHeader(this, vm, diffVm.position.filePath, fileNameClickHandler) {
          createDiffPanel(project, diffVm)
        }
      }
    }
    val reactions = HorizontalListPanel().apply {
      bindChildIn(cs, mainNoteVm.mapNotNull { it.reactionsVm }.filterNotNull()) { reactionsVm ->
        GitLabReactionsComponentFactory.create(this, reactionsVm)
      }
    }

    val contentPanel = VerticalListPanel(gap = CodeReviewTimelineUIUtil.VERTICAL_GAP).apply {
      add(reactions)
      add(repliesActionsPanel)
    }

    contentPanel.bindChildIn(cs, combine(vm.collapsed, diffPanelFlow, ::Pair), index = 0) { (collapsed, diffPanel) ->
      if (diffPanel != null) {
        VerticalListPanel(CodeReviewTimelineUIUtil.Thread.DIFF_TEXT_GAP).apply {
          if (collapsed) {
            add(textContentPanel)
            add(diffPanel)
          }
          else {
            add(diffPanel)
            add(textContentPanel)
          }
        }
      }
      else {
        textContentPanel
      }
    }

    return contentPanel
  }

  private fun createContentIn(project: Project,
                              cs: CoroutineScope,
                              vm: GitLabMergeRequestTimelineItemViewModel.DraftNote,
                              imageLoader: GitLabImageLoader): JPanel {
    val textPanel = GitLabNoteComponentFactory.createTextPanel(project, cs, vm.bodyHtml, vm.serverUrl,
                                                               imageLoader)

    val textContentPanel = GitLabEditableComponentFactory.wrapTextComponent(cs, textPanel, vm.actionsVm?.editVm ?: flowOf(null)) {
      GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.UPDATE_NOTE,
                                           ACTION_PLACE)
    }.let {
      wrapWithLimitedSize(it, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH)
    }

    return VerticalListPanel(CodeReviewTimelineUIUtil.Thread.DIFF_TEXT_GAP).apply {
      bindChildIn(cs, vm.diffVm, index = 0) { diffVm ->
        diffVm?.let {
          val fileNameClickHandler = diffVm.showDiffHandler.asActionHandler()
          TimelineDiffComponentFactory.createDiffWithHeader(this, diffVm.position.filePath, fileNameClickHandler,
                                                            createDiffPanel(project, it))
        }
      }
      add(textContentPanel)
    }
  }

  private const val OPEN_DIFF_LINK_HREF = "OPEN_DIFF"

  private fun CoroutineScope.createDiffPanel(project: Project, diffVm: GitLabDiscussionDiffViewModel): JComponent =
    Wrapper(LoadingLabel()).apply {
      bindContentIn(this@createDiffPanel, diffVm.patchHunk) { hunkResult ->
        val loadedDiffCs = this

        if (hunkResult.isInProgress) {
          return@bindContentIn LoadingLabel()
        }

        val hunk = hunkResult.getOrNull()
        if (hunk != null) {
          TimelineDiffComponentFactory.createDiffComponentIn(loadedDiffCs, project, EditorFactory.getInstance(), hunk.hunk, hunk.anchor, null)
        }
        else {
          createMissingHunkComponent(diffVm, hunkResult.exceptionOrNull())
        }
      }
    }

  private fun createMissingHunkComponent(
    diffVm: GitLabDiscussionDiffViewModel,
    error: Throwable?,
  ): JPanel = JPanel(SingleComponentCenteringLayout()).apply {
    isOpaque = false
    border = JBUI.Borders.empty(16)

    launchOnShow("Hunk text") {
      bindChildIn(this, diffVm.showDiffHandler.asActionHandler()) { clickListener ->
        if (clickListener != null) {
          val text = buildCantLoadHunkText(error)
            .append(HtmlChunk.p().child(
              HtmlChunk.link(OPEN_DIFF_LINK_HREF, GitLabBundle.message("merge.request.timeline.discussion.open.full.diff"))))
            .wrapWith(HtmlChunk.div("text-align: center"))
            .toString()

          SimpleHtmlPane(addBrowserListener = false).apply {
            setHtmlBody(text)
          }.also {
            it.addHyperlinkListener(object : HyperlinkAdapter() {
              override fun hyperlinkActivated(e: HyperlinkEvent) {
                if (e.description == OPEN_DIFF_LINK_HREF) {
                  clickListener.actionPerformed(ActionEvent(it, ActionEvent.ACTION_PERFORMED, "execute"))
                }
              }
            })
          }
        }
        else {
          val text = buildCantLoadHunkText(error).toString()
          SimpleHtmlPane(text)
        }
      }
    }
  }

  private fun buildCantLoadHunkText(error: Throwable?) =
    HtmlBuilder()
      .append(HtmlChunk.p().addText(GitLabBundle.message("merge.request.timeline.discussion.cant.load.diff")))
      .apply {
        if (error != null) {
          append(HtmlChunk.p().addText(error.localizedMessageOrClassName()))
        }
      }

  private fun Flow<GitLabUserDTO>.createIconValue(cs: CoroutineScope, iconsProvider: IconsProvider<GitLabUserDTO>, size: Int) =
    SingleValueModel<Icon>(EmptyIcon.create(size)).apply {
      cs.launch {
        collect {
          value = iconsProvider.getIcon(it, size)
        }
      }
    }

  private fun createRepliesActionsPanel(cs: CoroutineScope,
                                        avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                                        vm: GitLabMergeRequestTimelineDiscussionViewModel): JComponent {
    val iconsProvider = IconsProvider<CodeReviewUser> { key, size ->
      if (key is GitLabUserDTO) avatarIconsProvider.getIcon(key, size) else EmptyIcon.create(size)
    }
    return CodeReviewCommentUIUtil.createFoldedThreadControlsIn(
      cs, vm, iconsProvider
    )
  }

  private fun createDiscussionTextPane(
    project: Project, cs: CoroutineScope, vm: GitLabMergeRequestTimelineDiscussionViewModel,
    imageLoader: GitLabImageLoader,
  ): JComponent {
    val collapsedFlow = combine(vm.collapsible, vm.collapsed) { collapsible, collapsed ->
      collapsible && collapsed
    }

    val textFlow = combine(collapsedFlow, vm.mainNote) { collapsed, mainNote ->
      if (collapsed) mainNote.body else mainNote.bodyHtml
    }.flatMapLatest { it }

    val textPane = GitLabNoteComponentFactory.createTextPanel(project, cs, textFlow, vm.serverUrl, imageLoader)
    val layout = SizeRestrictedSingleComponentLayout()
    return JPanel(layout).apply {
      name = "Text pane wrapper"
      isOpaque = false

      cs.launch {
        collapsedFlow.collect {
          if (it) {
            textPane.foreground = UIUtil.getContextHelpForeground()
            layout.maxSize = DimensionRestrictions.LinesHeight(textPane, 2)
          }
          else {
            textPane.foreground = UIUtil.getLabelForeground()
            layout.maxSize = DimensionRestrictions.None
          }
          revalidate()
          repaint()
        }
      }
      add(textPane)
    }
  }

  private fun createRepliesPanelIn(project: Project,
                                   cs: CoroutineScope,
                                   vm: GitLabMergeRequestTimelineDiscussionViewModel,
                                   avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                                   imageLoader: GitLabImageLoader,
                                   ): JPanel {
    val repliesListPanel = ComponentListPanelFactory.createVertical(cs, vm.replies) { noteVm ->
      GitLabNoteComponentFactory.create(
        ComponentType.FULL_SECONDARY, project, this, avatarIconsProvider, imageLoader, noteVm,
        ACTION_PLACE
      )
    }

    val repliesPanel = VerticalListPanel().apply {
      add(repliesListPanel)
      bindChildIn(cs, vm.replyVm) { newNoteVm ->
        newNoteVm?.let {
          GitLabDiscussionComponentFactory.createReplyField(ComponentType.FULL_SECONDARY, project, this, vm, it,
                                                            avatarIconsProvider, ACTION_PLACE)
        }
      }
      bindVisibilityIn(cs, vm.repliesFolded.inverted())
    }
    return repliesPanel
  }
}

private fun Flow<(() -> Unit)?>.asActionHandler() = map { handler ->
  handler?.let { ActionListener { _ -> it() } }
}
