// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.CommonBundle
import com.intellij.collaboration.async.CompletableFutureUtil.handleOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.ToggleableContainer
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.thread.TimelineThreadCommentsPanel
import com.intellij.collaboration.ui.icon.OverlaidOffsetIconsIcon
import com.intellij.collaboration.ui.util.ActivatableCoroutineScopeProvider
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.ClickListener
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.PathUtil
import com.intellij.util.containers.nullize
import com.intellij.util.text.JBDateFormat
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRReviewThreadDiffComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRSelectInToolWindowHelper
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.cloneDialog.GHCloneDialogExtensionComponentBase.Companion.items
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import java.awt.Cursor
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

object GHPRReviewThreadComponent {

  fun create(project: Project,
             thread: GHPRReviewThreadModel,
             reviewDataProvider: GHPRReviewDataProvider,
             avatarIconsProvider: GHAvatarIconsProvider,
             suggestedChangeHelper: GHPRSuggestedChangeHelper,
             ghostUser: GHUser,
             currentUser: GHUser): JComponent {
    val panel = JPanel(VerticalLayout(12)).apply {
      isOpaque = false
    }
    val commentComponentFactory = GHPRReviewCommentComponent.factory(project, thread, ghostUser,
                                                                     reviewDataProvider,
                                                                     avatarIconsProvider,
                                                                     suggestedChangeHelper)
    val commentsPanel = TimelineThreadCommentsPanel(thread, commentComponentFactory)
    panel.add(commentsPanel)

    if (reviewDataProvider.canComment()) {
      panel.add(getThreadActionsComponent(project, reviewDataProvider, thread, avatarIconsProvider, currentUser))
    }
    return panel
  }

  fun createThreadDiff(thread: GHPRReviewThreadModel,
                       diffComponentFactory: GHPRReviewThreadDiffComponentFactory,
                       selectInToolWindowHelper: GHPRSelectInToolWindowHelper): JComponent {

    val scopeProvider = ActivatableCoroutineScopeProvider()

    val expandCollapseButton = InlineIconButton(AllIcons.General.CollapseComponent, AllIcons.General.CollapseComponentHover,
                                                tooltip = GithubBundle.message("pull.request.timeline.review.thread.collapse")).apply {
      actionListener = ActionListener {
        thread.collapsedState.update { !it }
      }
    }

    val diffComponent = diffComponentFactory.createComponent(thread.diffHunk, thread.startLine).apply {
      border = IdeBorderFactory.createBorder(SideBorder.TOP)
    }
    scopeProvider.launchInScope {
      diffComponent.bindVisibility(this, thread.collapsedState.map { !it })
    }

    scopeProvider.launchInScope {
      thread.collapsedState.collect {
        expandCollapseButton.icon = if (it) {
          AllIcons.General.ExpandComponent
        }
        else {
          AllIcons.General.CollapseComponent
        }
        expandCollapseButton.hoveredIcon = if (it) {
          AllIcons.General.ExpandComponentHover
        }
        else {
          AllIcons.General.CollapseComponentHover
        }
        expandCollapseButton.tooltip = if (it) {
          GithubBundle.message("pull.request.timeline.review.thread.expand")
        }
        else {
          GithubBundle.message("pull.request.timeline.review.thread.collapse")
        }
      }
    }

    thread.addAndInvokeStateChangeListener {
      expandCollapseButton.isVisible = thread.isResolved || thread.isOutdated
    }

    scopeProvider.launchInScope {
      diffComponent.bindVisibility(this, thread.collapsedState.map { !it })
    }

    return RoundedPanel(VerticalLayout(0), 8).apply {
      isOpaque = false
      add(createFileName(thread, selectInToolWindowHelper, expandCollapseButton))
      add(diffComponent)
    }.also {
      scopeProvider.activateWith(it)
    }
  }

  private fun createFileName(thread: GHPRReviewThreadModel,
                             selectInToolWindowHelper: GHPRSelectInToolWindowHelper,
                             expandCollapseButton: InlineIconButton): JComponent {
    val name = PathUtil.getFileName(thread.filePath)
    val path = PathUtil.getParentPath(thread.filePath)
    val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(name)

    val nameLabel = JLabel(name, fileType.icon, SwingConstants.LEFT).apply {
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      object : ClickListener() {
        override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
          selectInToolWindowHelper.selectChange(thread.commit?.oid, thread.filePath)
          return true
        }
      }.installOn(this)
    }
    return NonOpaquePanel(MigLayout(LC().insets("0").gridGap("5", "0").fill().noGrid())).apply {
      border = JBUI.Borders.empty(10)

      add(nameLabel)

      if (!path.isBlank()) add(JLabel(path).apply {
        foreground = UIUtil.getContextHelpForeground()
      })

      add(expandCollapseButton, CC().hideMode(3).gapLeft("10:push"))
    }
  }

  private fun getThreadActionsComponent(
    project: Project,
    reviewDataProvider: GHPRReviewDataProvider,
    thread: GHPRReviewThreadModel,
    avatarIconsProvider: GHAvatarIconsProvider,
    currentUser: GHUser
  ): JComponent {
    val toggleModel = SingleValueModel(false)

    return ToggleableContainer.create(
      toggleModel,
      {
        createCollapsedThreadActionsComponent(reviewDataProvider, thread) {
          toggleModel.value = true
        }
      },
      {
        createUncollapsedThreadActionsComponent(project, reviewDataProvider, thread, avatarIconsProvider, currentUser) {
          toggleModel.value = false
        }
      }
    )
  }

  private fun createCollapsedThreadActionsComponent(reviewDataProvider: GHPRReviewDataProvider,
                                                    thread: GHPRReviewThreadModel,
                                                    onReply: () -> Unit): JComponent {

    val toggleReplyLink = LinkLabel<Any>(GithubBundle.message("pull.request.review.thread.reply"), null) { _, _ ->
      onReply()
    }.apply {
      isFocusable = true
    }

    val unResolveLink = createUnResolveLink(reviewDataProvider, thread)

    return JPanel(HorizontalLayout(8)).apply {
      isOpaque = false
      border = JBUI.Borders.empty(6, GHPRReviewCommentComponent.AVATAR_SIZE + GHPRReviewCommentComponent.AVATAR_GAP, 6, 0)

      add(toggleReplyLink)
      add(unResolveLink)
    }
  }

  fun createUncollapsedThreadActionsComponent(project: Project, reviewDataProvider: GHPRReviewDataProvider,
                                              thread: GHPRReviewThreadModel,
                                              avatarIconsProvider: GHAvatarIconsProvider,
                                              currentUser: GHUser,
                                              onDone: () -> Unit): JComponent {
    val textFieldModel = GHCommentTextFieldModel(project) { text ->
      reviewDataProvider.addComment(EmptyProgressIndicator(), thread.getElementAt(0).id, text).successOnEdt {
        thread.addComment(it)
        onDone()
      }
    }

    val submitShortcutText = KeymapUtil.getFirstKeyboardShortcutText(CommentInputComponentFactory.defaultSubmitShortcut)
    val newLineShortcutText = KeymapUtil.getFirstKeyboardShortcutText(CommonShortcuts.ENTER)

    val unResolveAction = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        textFieldModel.isBusy = true
        if (thread.isResolved) {
          reviewDataProvider.unresolveThread(EmptyProgressIndicator(), thread.id)
        }
        else {
          reviewDataProvider.resolveThread(EmptyProgressIndicator(), thread.id)
        }.handleOnEdt { _, _ ->
          textFieldModel.isBusy = false
        }
      }
    }

    thread.addAndInvokeStateChangeListener {
      val name = if (thread.isResolved) {
        GithubBundle.message("pull.request.review.thread.unresolve")
      }
      else {
        GithubBundle.message("pull.request.review.thread.resolve")
      }
      unResolveAction.putValue(Action.NAME, name)
    }

    val unResolveEnabledListener: () -> Unit = {
      unResolveAction.isEnabled = !textFieldModel.isBusy
    }
    textFieldModel.addStateListener(unResolveEnabledListener)
    unResolveEnabledListener()

    val cancelAction = swingAction(CommonBundle.getCancelButtonText()) {
      onDone()
    }
    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = MutableStateFlow(textFieldModel.submitAction(GithubBundle.message("pull.request.review.thread.reply"))),
      additionalActions = MutableStateFlow(listOf(unResolveAction)),
      hintInfo = MutableStateFlow(CommentInputActionsComponentFactory.HintInfo(
        submitHint = GithubBundle.message("pull.request.review.thread.reply.hint", submitShortcutText),
        newLineHint = GithubBundle.message("pull.request.new.line.hint", newLineShortcutText)
      ))
    )

    return GHCommentTextFieldFactory(textFieldModel)
      .create(GHCommentTextFieldFactory.ActionsConfig(actions, cancelAction),
              GHCommentTextFieldFactory.AvatarConfig(avatarIconsProvider, currentUser))
  }

  fun getCollapsedThreadActionsComponent(reviewDataProvider: GHPRReviewDataProvider,
                                         avatarIconsProvider: GHAvatarIconsProvider,
                                         thread: GHPRReviewThreadModel,
                                         repliesCollapsedState: MutableStateFlow<Boolean>,
                                         ghostUser: GHUser): JComponent {
    val authorsLabel = JLabel()
    val repliesLink = LinkLabel<Any>("", null, LinkListener { _, _ ->
      repliesCollapsedState.update { !it }
    })
    val lastReplyDateLabel = JLabel().apply {
      foreground = UIUtil.getContextHelpForeground()
    }

    val repliesModel = thread.repliesModel
    repliesModel.addListDataListener(object : ListDataListener {
      init {
        update()
      }

      private fun update() {
        val authors = LinkedHashSet<GHActor>()
        val repliesCount = repliesModel.size

        repliesModel.items.mapTo(authors) {
          it.author ?: ghostUser
        }

        authorsLabel.apply {
          icon = authors.map { avatarIconsProvider.getIcon(it.avatarUrl, GHUIUtil.AVATAR_SIZE) }.nullize()?.let {
            OverlaidOffsetIconsIcon(it)
          }
          isVisible = icon != null
        }

        repliesLink.text = if (repliesCount == 0) {
          GithubBundle.message("pull.request.review.thread.reply")
        }
        else {
          GithubBundle.message("pull.request.review.thread.replies", repliesCount)
        }

        lastReplyDateLabel.apply {
          isVisible = repliesCount > 0
          if (isVisible) {
            text = repliesModel.getElementAt(repliesModel.size - 1).dateCreated.let {
              JBDateFormat.getFormatter().formatPrettyDateTime(it)
            }
          }
        }
      }

      override fun intervalAdded(e: ListDataEvent) = update()
      override fun intervalRemoved(e: ListDataEvent) = update()
      override fun contentsChanged(e: ListDataEvent) = Unit
    })

    val repliesPanel = JPanel(HorizontalLayout(8)).apply {
      isOpaque = false
      add(authorsLabel)
      add(repliesLink)
      add(lastReplyDateLabel)
    }

    val unResolveLink = createUnResolveLink(reviewDataProvider, thread)

    return JPanel(HorizontalLayout(14)).apply {
      isOpaque = false
      add(repliesPanel)
      add(unResolveLink)
    }
  }

  private fun createUnResolveLink(reviewDataProvider: GHPRReviewDataProvider, thread: GHPRReviewThreadModel): LinkLabel<Any> {
    val unResolveLink = LinkLabel<Any>("", null) { comp, _ ->
      comp.isEnabled = false
      if (thread.isResolved) {
        reviewDataProvider.unresolveThread(EmptyProgressIndicator(), thread.id)
      }
      else {
        reviewDataProvider.resolveThread(EmptyProgressIndicator(), thread.id)
      }.handleOnEdt { _, _ ->
        comp.isEnabled = true
      }
    }.apply {
      isFocusable = true
    }

    thread.addAndInvokeStateChangeListener {
      unResolveLink.text = if (thread.isResolved) {
        GithubBundle.message("pull.request.review.thread.unresolve")
      }
      else {
        GithubBundle.message("pull.request.review.thread.resolve")
      }
    }
    return unResolveLink
  }
}