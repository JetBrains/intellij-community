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
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.filePath
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionComponentFactory
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
               vm: GitLabMergeRequestTimelineDiscussionViewModel,
               avatarIconsProvider: IconsProvider<GitLabUserDTO>): JComponent =
    VerticalListPanel().apply {
      name = "GitLab Discussion Panel ${vm.id}"
      add(createDiscussionItemIn(project, cs, vm, avatarIconsProvider))
      add(createRepliesPanelIn(project, cs, vm, avatarIconsProvider))
    }

  fun createIn(project: Project,
               cs: CoroutineScope,
               vm: GitLabMergeRequestTimelineItemViewModel.DraftNote,
               avatarIconsProvider: IconsProvider<GitLabUserDTO>): JComponent {
    val contentPanel = createContentIn(project, cs, vm)
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
                                     avatarIconsProvider: IconsProvider<GitLabUserDTO>): JComponent {
    val contentPanel = createContentIn(project, cs, vm, avatarIconsProvider)
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
                              avatarIconsProvider: IconsProvider<GitLabUserDTO>): JPanel {
    val mainNoteVm = vm.mainNote
    val repliesActionsPanel = createRepliesActionsPanel(cs, avatarIconsProvider, vm).apply {
      border = JBUI.Borders.empty(Replies.ActionsFolded.VERTICAL_PADDING, 0)
      bindVisibilityIn(cs, vm.repliesFolded)
    }
    val textPanel = createDiscussionTextPane(cs, vm)

    val editVmFlow = mainNoteVm.flatMapLatest { it.actionsVm?.editVm ?: flowOf(null) }
    val textContentPanel = EditableComponentFactory.wrapTextComponent(cs, textPanel, editVmFlow) {
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

    val contentPanel = VerticalListPanel().apply {
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
                              vm: GitLabMergeRequestTimelineItemViewModel.DraftNote): JPanel {
    val textPanel = GitLabNoteComponentFactory.createTextPanel(cs, vm.bodyHtml, vm.serverUrl)

    val textContentPanel = EditableComponentFactory.wrapTextComponent(cs, textPanel, vm.actionsVm?.editVm ?: flowOf(null)) {
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
      bindContentIn(this@createDiffPanel, diffVm.patchHunk) { hunkState ->
        val loadedDiffCs = this
        when (hunkState) {
          is GitLabDiscussionDiffViewModel.PatchHunkResult.Loaded -> {
            TimelineDiffComponentFactory.createDiffComponentIn(loadedDiffCs, project, EditorFactory.getInstance(), hunkState.hunk,
                                                               hunkState.anchor, null)
          }
          is GitLabDiscussionDiffViewModel.PatchHunkResult.Error,
          GitLabDiscussionDiffViewModel.PatchHunkResult.NotLoaded -> {
            JPanel(SingleComponentCenteringLayout()).apply {
              isOpaque = false
              border = JBUI.Borders.empty(16)

              bindChildIn(loadedDiffCs, diffVm.showDiffHandler.asActionHandler()) { clickListener ->
                if (clickListener != null) {
                  val text = buildCantLoadHunkText(hunkState)
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
                  val text = buildCantLoadHunkText(hunkState).toString()
                  SimpleHtmlPane(text)
                }
              }
            }
          }
        }
      }
    }

  private fun buildCantLoadHunkText(hunkState: GitLabDiscussionDiffViewModel.PatchHunkResult) =
    HtmlBuilder()
      .append(HtmlChunk.p().addText(GitLabBundle.message("merge.request.timeline.discussion.cant.load.diff")))
      .apply {
        if (hunkState is GitLabDiscussionDiffViewModel.PatchHunkResult.Error) {
          append(HtmlChunk.p().addText(hunkState.error.localizedMessage))
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

  private fun createDiscussionTextPane(cs: CoroutineScope, vm: GitLabMergeRequestTimelineDiscussionViewModel): JComponent {
    val collapsedFlow = combine(vm.collapsible, vm.collapsed) { collapsible, collapsed ->
      collapsible && collapsed
    }

    val textFlow = combine(collapsedFlow, vm.mainNote) { collapsed, mainNote ->
      if (collapsed) mainNote.body else mainNote.bodyHtml
    }.flatMapLatest { it }

    val textPane = GitLabNoteComponentFactory.createTextPanel(cs, textFlow, vm.serverUrl)
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
                                   avatarIconsProvider: IconsProvider<GitLabUserDTO>): JPanel {
    val repliesListPanel = ComponentListPanelFactory.createVertical(cs, vm.replies) { noteVm ->
      GitLabNoteComponentFactory.create(ComponentType.FULL_SECONDARY, project, this, avatarIconsProvider, noteVm,
                                        ACTION_PLACE)
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
