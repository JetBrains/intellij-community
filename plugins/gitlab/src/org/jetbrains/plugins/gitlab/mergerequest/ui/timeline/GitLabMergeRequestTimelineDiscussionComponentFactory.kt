// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.inverted
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.*
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.animatedLoadingIcon
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.wrapWithLimitedSize
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.ComponentType
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil.Thread.Replies
import com.intellij.collaboration.ui.codereview.timeline.TimelineDiffComponentFactory
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.icon.OverlaidOffsetIconsIcon
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.*
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.containers.nullize
import com.intellij.util.text.JBDateFormat
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineUIUtil.createNoteTitleComponent
import org.jetbrains.plugins.gitlab.ui.comment.*
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

@OptIn(ExperimentalCoroutinesApi::class)
object GitLabMergeRequestTimelineDiscussionComponentFactory {

  fun create(project: Project,
             cs: CoroutineScope,
             avatarIconsProvider: IconsProvider<GitLabUserDTO>,
             vm: GitLabMergeRequestTimelineDiscussionViewModel): JComponent {
    val contentPanel = createContent(project, cs, avatarIconsProvider, vm)
    val actionsPanel = GitLabNoteComponentFactory.createActions(cs, vm.mainNote)

    val repliesPanel = ComponentListPanelFactory.createVertical(cs, vm.replies, GitLabNoteViewModel::id) { noteCs, noteVm ->
      GitLabNoteComponentFactory.create(ComponentType.FULL_SECONDARY, project, noteCs, avatarIconsProvider, noteVm)
    }.let {
      VerticalListPanel().apply {
        add(it)

        val replyVm = vm.replyVm
        if (replyVm != null) {
          bindChild(cs, replyVm.newNoteVm) { cs, newNoteVm ->
            newNoteVm?.let {
              GitLabDiscussionComponentFactory.createReplyField(ComponentType.FULL_SECONDARY, project, cs, it, vm.resolveVm,
                                                                avatarIconsProvider)
            }
          }

          cs.launch(start = CoroutineStart.UNDISPATCHED) {
            vm.repliesFolded.collect {
              if (!it) replyVm.startWriting()
            }
          }
        }
      }
    }.apply {
      bindVisibility(cs, vm.repliesFolded.inverted())
    }

    val titlePanel = Wrapper().apply {
      bindContent(cs, vm.mainNote) { titleCs, mainNote ->
        createNoteTitleComponent(titleCs, mainNote)
      }
    }

    return CodeReviewChatItemUIUtil.buildDynamic(ComponentType.FULL,
                                                 { vm.author.createIconValue(cs, avatarIconsProvider, it) },
                                                 contentPanel) {
      maxContentWidth = null
      withHeader(titlePanel, actionsPanel)
    }.let {
      VerticalListPanel().apply {
        add(it)
        add(repliesPanel)
      }
    }.apply {
      name = "GitLab Discussion Panel ${vm.id}"
    }
  }

  private fun createContent(project: Project,
                            cs: CoroutineScope,
                            avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                            vm: GitLabMergeRequestTimelineDiscussionViewModel): JPanel {
    val mainNoteVm = vm.mainNote
    val repliesActionsPanel = createRepliesActionsPanel(cs, avatarIconsProvider, vm).apply {
      border = JBUI.Borders.empty(Replies.ActionsFolded.VERTICAL_PADDING, 0)
      bindVisibility(cs, vm.repliesFolded)
    }
    val textPanel = GitLabNoteComponentFactory.createTextPanel(cs, mainNoteVm.flatMapLatest { it.htmlBody }).let {
      collapseDiscussionTextIfNeeded(cs, vm, it)
    }

    // oh well... probably better to make a suitable API in EditableComponentFactory, but that would look ugly
    val actionAndEditVmsFlow: Flow<Pair<GitLabNoteAdminActionsViewModel, GitLabNoteEditingViewModel>?> =
      mainNoteVm.flatMapLatest { note ->
        val actionsVm = note.actionsVm
        actionsVm?.editVm?.map { it?.let { actionsVm to it } } ?: flowOf(null)
      }

    val textContentPanel = EditableComponentFactory.create(cs, textPanel, actionAndEditVmsFlow) { editCs, (actionsVm, editVm) ->
      GitLabNoteEditorComponentFactory.create(project, editCs, editVm,
                                              GitLabNoteComponentFactory.createEditActionsConfig(actionsVm, editVm))
    }.let {
      wrapWithLimitedSize(it, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH)
    }

    val diffPanel = createDiffPanel(project, cs, vm)

    val contentPanel = VerticalListPanel().apply {
      add(repliesActionsPanel)
    }

    if (diffPanel == null) {
      contentPanel.add(textContentPanel, null, 0)
    }
    else {
      contentPanel.bindChild(cs, vm.collapsed, index = 0) { _, collapsed ->
        VerticalListPanel(4).apply {
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
    }
    return contentPanel
  }

  private fun createDiffPanel(project: Project,
                              cs: CoroutineScope,
                              vm: GitLabMergeRequestTimelineDiscussionViewModel): JComponent? {
    val diffVm = vm.diffVm ?: return null
    return TimelineDiffComponentFactory.createDiffWithHeader(cs, vm, diffVm.position.filePath, {}) {
      Wrapper().apply {
        bindContent(it, diffVm.patchHunk) { _, hunkState ->
          when (hunkState) {
            GitLabDiscussionDiffViewModel.PatchHunkLoadingState.Loading -> {
              JLabel(animatedLoadingIcon)
            }
            is GitLabDiscussionDiffViewModel.PatchHunkLoadingState.Loaded -> {
              TimelineDiffComponentFactory.createDiffComponent(project, EditorFactory.getInstance(), hunkState.hunk, hunkState.anchor, null)
            }
            is GitLabDiscussionDiffViewModel.PatchHunkLoadingState.LoadingError,
            GitLabDiscussionDiffViewModel.PatchHunkLoadingState.NotAvailable -> {
              val text = HtmlBuilder()
                .append(HtmlChunk.p().addText("Not able to load diff hunk"))
                .apply {
                  if (hunkState is GitLabDiscussionDiffViewModel.PatchHunkLoadingState.LoadingError) {
                    append(HtmlChunk.p().addText(hunkState.error.localizedMessage))
                  }
                }
                .append(HtmlChunk.p().child(HtmlChunk.link("OPEN", "Open full diff")))
                .wrapWith(HtmlChunk.div("text-align: center"))
                .toString()

              JPanel(SingleComponentCenteringLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(16)

                add(SimpleHtmlPane(text))
              }
            }
          }
        }
      }
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
    val authorsLabel = JLabel().apply {
      bindVisibility(cs, vm.replies.map { it.isNotEmpty() })

      val repliesAuthors = vm.replies.map { replies ->
        val authors = LinkedHashSet<GitLabUserDTO>()
        replies.mapTo(authors) { it.author }
      }

      bindIcon(cs, repliesAuthors.map { authors ->
        authors.map {
          avatarIconsProvider.getIcon(it, ComponentType.COMPACT.iconSize)
        }.nullize()?.let {
          OverlaidOffsetIconsIcon(it)
        }
      })
    }

    val hasRepliesOrCanCreateNewFlow = vm.replies.map { it.isNotEmpty() || vm.replyVm != null }

    val repliesLink = LinkLabel<Any>("", null, LinkListener { _, _ ->
      vm.setRepliesFolded(false)
    }).apply {
      bindVisibility(cs, hasRepliesOrCanCreateNewFlow)
      bindText(cs, vm.replies.map { replies ->
        val replyCount = replies.size
        if (replyCount == 0) {
          CollaborationToolsBundle.message("review.comments.reply.action")
        }
        else {
          CollaborationToolsBundle.message("review.comments.replies.action", replyCount)
        }
      })
    }

    val lastReplyDateLabel = JLabel().apply {
      foreground = UIUtil.getContextHelpForeground()
    }.apply {
      bindVisibility(cs, vm.replies.map { it.isNotEmpty() })
      bindText(cs, vm.replies.mapNotNull { replies ->
        replies.lastOrNull()?.createdAt?.let { JBDateFormat.getFormatter().formatPrettyDateTime(it) }
      })
    }

    val repliesActions = HorizontalListPanel(Replies.ActionsFolded.HORIZONTAL_GAP).apply {
      add(authorsLabel)
      add(repliesLink)
      add(lastReplyDateLabel)
    }.apply {
      bindVisibility(cs, hasRepliesOrCanCreateNewFlow)
    }
    return HorizontalListPanel(Replies.ActionsFolded.HORIZONTAL_GROUP_GAP).apply {
      add(repliesActions)

      vm.resolveVm?.let {
        GitLabDiscussionComponentFactory.createUnResolveLink(cs, it).also(::add)
      }
    }
  }

  private fun collapseDiscussionTextIfNeeded(cs: CoroutineScope, vm: GitLabMergeRequestTimelineDiscussionViewModel,
                                             textPane: JComponent): JComponent {
    val resolveVm = vm.resolveVm
    if (resolveVm == null) return textPane

    return JPanel(null).apply {
      name = "Text pane wrapper"
      isOpaque = false
      layout = SizeRestrictedSingleComponentLayout().apply {
        cs.launch {
          vm.collapsed.collect {
            if (it) {
              textPane.foreground = UIUtil.getContextHelpForeground()
              maxSize = DimensionRestrictions.LinesHeight(textPane, 2)
            }
            else {
              textPane.foreground = UIUtil.getLabelForeground()
              maxSize = DimensionRestrictions.None
            }
            revalidate()
            repaint()
          }
        }
      }
      add(textPane)
    }
  }
}