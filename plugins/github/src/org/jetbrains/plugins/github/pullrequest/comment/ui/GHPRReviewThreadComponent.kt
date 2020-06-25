// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.ui.ClickListener
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.HorizontalBox
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRReviewThreadDiffComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRSelectInToolWindowHelper
import org.jetbrains.plugins.github.ui.InlineIconButton
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.handleOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import java.awt.Cursor
import java.awt.event.ActionListener
import java.awt.event.MouseEvent
import javax.swing.*

object GHPRReviewThreadComponent {

  fun create(thread: GHPRReviewThreadModel, reviewDataProvider: GHPRReviewDataProvider,
             avatarIconsProvider: GHAvatarIconsProvider, currentUser: GHUser): JComponent {
    val panel = JPanel(VerticalLayout(UI.scale(12))).apply {
      isOpaque = false
    }
    panel.add(
      GHPRReviewThreadCommentsPanel.create(thread, GHPRReviewCommentComponent.factory(reviewDataProvider, avatarIconsProvider)),
      VerticalLayout.FILL_HORIZONTAL)

    if (reviewDataProvider.canComment()) {
      panel.add(getThreadActionsComponent(reviewDataProvider, thread, avatarIconsProvider, currentUser), VerticalLayout.FILL_HORIZONTAL)
    }
    return panel
  }

  fun createWithDiff(thread: GHPRReviewThreadModel, reviewDataProvider: GHPRReviewDataProvider,
                     selectInToolWindowHelper: GHPRSelectInToolWindowHelper, diffComponentFactory: GHPRReviewThreadDiffComponentFactory,
                     avatarIconsProvider: GHAvatarIconsProvider, currentUser: GHUser): JComponent {

    val collapseButton = InlineIconButton(AllIcons.General.CollapseComponent, AllIcons.General.CollapseComponentHover,
                                          tooltip = GithubBundle.message("pull.request.timeline.review.thread.collapse"))
    val expandButton = InlineIconButton(AllIcons.General.ExpandComponent, AllIcons.General.ExpandComponentHover,
                                        tooltip = GithubBundle.message("pull.request.timeline.review.thread.expand"))

    val panel = JPanel(VerticalLayout(UI.scale(4))).apply {
      isOpaque = false
      add(createFileName(thread, selectInToolWindowHelper, collapseButton, expandButton),
          VerticalLayout.FILL_HORIZONTAL)
    }

    object : CollapseController(thread, panel, collapseButton, expandButton) {
      override fun createThreadsPanel(): JComponent = JPanel(VerticalLayout(UI.scale(12))).apply {
        isOpaque = false

        add(diffComponentFactory.createComponent(thread.diffHunk), VerticalLayout.FILL_HORIZONTAL)

        add(GHPRReviewThreadCommentsPanel.create(thread,
                                                 GHPRReviewCommentComponent.factory(reviewDataProvider, avatarIconsProvider, false)),
            VerticalLayout.FILL_HORIZONTAL)

        if (reviewDataProvider.canComment()) {
          add(getThreadActionsComponent(reviewDataProvider, thread, avatarIconsProvider, currentUser),
              VerticalLayout.FILL_HORIZONTAL)
        }
      }
    }


    return panel
  }

  private abstract class CollapseController(private val thread: GHPRReviewThreadModel,
                                            private val panel: JPanel,
                                            private val collapseButton: InlineIconButton,
                                            private val expandButton: InlineIconButton) {

    private val collapseModel = SingleValueModel(true)
    private var threadsPanel: JComponent? = null

    init {
      collapseButton.actionListener = ActionListener { collapseModel.value = true }
      expandButton.actionListener = ActionListener { collapseModel.value = false }
      collapseModel.addValueChangedListener(::update)
      thread.addAndInvokeStateChangeListener(::update)
    }

    private fun update() {
      val shouldBeVisible = !thread.isResolved || !collapseModel.value
      if (shouldBeVisible) {
        if (threadsPanel == null) {
          threadsPanel = createThreadsPanel()
          panel.add(threadsPanel!!, VerticalLayout.FILL_HORIZONTAL)
          panel.validate()
          panel.repaint()
        }
      }
      else {
        if (threadsPanel != null) {
          panel.remove(threadsPanel!!)
          panel.validate()
          panel.repaint()
        }
        threadsPanel = null
      }

      collapseButton.isVisible = thread.isResolved && !collapseModel.value
      expandButton.isVisible = thread.isResolved && collapseModel.value
    }

    protected abstract fun createThreadsPanel(): JComponent
  }

  private fun createFileName(thread: GHPRReviewThreadModel,
                             selectInToolWindowHelper: GHPRSelectInToolWindowHelper,
                             collapseButton: InlineIconButton,
                             expandButton: InlineIconButton): JComponent {
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

    val resolvedLabel = JBLabel(" ${GithubBundle.message("pull.request.review.comment.resolved")} ", UIUtil.ComponentStyle.SMALL).apply {
      foreground = UIUtil.getContextHelpForeground()
      background = UIUtil.getPanelBackground()
    }.andOpaque()


    thread.addAndInvokeStateChangeListener {
      resolvedLabel.isVisible = thread.isResolved
    }

    return NonOpaquePanel(MigLayout(LC().insets("0").gridGap("${UI.scale(5)}", "0").fill().noGrid())).apply {
      add(nameLabel)

      if (!path.isBlank()) add(JLabel(path).apply {
        foreground = UIUtil.getContextHelpForeground()
      })

      add(resolvedLabel, CC().hideMode(3))

      add(collapseButton, CC().hideMode(3))
      add(expandButton, CC().hideMode(3))
    }
  }

  private fun getThreadActionsComponent(reviewDataProvider: GHPRReviewDataProvider,
                                        thread: GHPRReviewThreadModel,
                                        avatarIconsProvider: GHAvatarIconsProvider,
                                        currentUser: GHUser): JComponent {
    val toggleModel = SingleValueModel(false)
    val textFieldModel = GHSubmittableTextFieldModel { text ->
      reviewDataProvider.addComment(EmptyProgressIndicator(), thread.getElementAt(0).id, text).successOnEdt {
        thread.addComment(GHPRReviewCommentModel.convert(it))
        toggleModel.value = false
      }
    }

    val toggleReplyLink = LinkLabel<Any>(GithubBundle.message("pull.request.review.thread.reply"), null) { _, _ ->
      toggleModel.value = true
    }.apply {
      isFocusable = true
    }

    val resolveLink = LinkLabel<Any>(GithubBundle.message("pull.request.review.thread.resolve"), null).apply {
      isFocusable = true
    }.also {
      it.setListener({ _, _ ->
                       it.isEnabled = false
                       reviewDataProvider.resolveThread(EmptyProgressIndicator(), thread.id).handleOnEdt { _, _ ->
                         it.isEnabled = true
                       }
                     }, null)
    }

    val unresolveLink = LinkLabel<Any>(GithubBundle.message("pull.request.review.thread.unresolve"), null).apply {
      isFocusable = true
    }.also {
      it.setListener({ _, _ ->
                       it.isEnabled = false
                       reviewDataProvider.unresolveThread(EmptyProgressIndicator(), thread.id).handleOnEdt { _, _ ->
                         it.isEnabled = true
                       }
                     }, null)
    }

    return GHPRToggleableContainer.create(toggleModel,
                                          { createThreadActionsComponent(thread, toggleReplyLink, resolveLink, unresolveLink) },
                                          {
                                            GHSubmittableTextFieldFactory(textFieldModel).create(avatarIconsProvider, currentUser,
                                                                                                 GithubBundle.message(
                                                                                                   "pull.request.review.thread.reply"),
                                                                                                 onCancel = { toggleModel.value = false })
                                          })
  }

  private fun createThreadActionsComponent(model: GHPRReviewThreadModel,
                                           toggleReplyLink: LinkLabel<Any>,
                                           resolveLink: LinkLabel<Any>,
                                           unresolveLink: LinkLabel<Any>): JComponent {
    fun update() {
      resolveLink.isVisible = model.state != GHPullRequestReviewCommentState.PENDING && !model.isResolved
      unresolveLink.isVisible = model.state != GHPullRequestReviewCommentState.PENDING && model.isResolved
    }

    model.addAndInvokeStateChangeListener(::update)

    return HorizontalBox().apply {
      isOpaque = false
      border = JBUI.Borders.empty(6, 28, 6, 0)

      add(toggleReplyLink)
      add(Box.createHorizontalStrut(UI.scale(8)))
      add(resolveLink)
      add(unresolveLink)
    }
  }
}