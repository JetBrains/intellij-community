// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.collaboration.async.CompletableFutureUtil.errorOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.InstallButton
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ClientProperty
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.*
import icons.CollaborationToolsIcons
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.ui.component.GHHtmlErrorPanel
import org.jetbrains.plugins.github.ui.component.GHSimpleErrorPanelModel
import java.awt.Font
import java.awt.event.ActionListener
import java.util.concurrent.CompletableFuture
import javax.swing.*

class GHPRReviewSubmitAction : JButtonAction(StringUtil.ELLIPSIS, GithubBundle.message("pull.request.review.submit.action.description")) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val dataProvider = e.getData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)
    if (dataProvider == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val reviewData = dataProvider.reviewData
    val details = dataProvider.detailsData.loadedDetails
    e.presentation.isVisible = true
    val pendingReviewFuture = reviewData.loadPendingReview()
    e.presentation.isEnabled = pendingReviewFuture.isDone && details != null
    e.presentation.putClientProperty(PROP_PREFIX, getPrefix(e.place))

    if (e.presentation.isEnabledAndVisible) {
      val review = try {
        pendingReviewFuture.getNow(null)
      }
      catch (e: Exception) {
        null
      }
      val pendingReview = review != null
      val comments = review?.commentsCount

      e.presentation.text = getText(comments)
      e.presentation.putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, pendingReview)
    }
  }

  private fun getPrefix(place: String) = if (place == ActionPlaces.DIFF_TOOLBAR) GithubBundle.message("pull.request.review.submit")
  else CollaborationToolsBundle.message("review.start.submit.action")

  @NlsSafe
  private fun getText(pendingComments: Int?): String {
    val builder = StringBuilder()
    if (pendingComments != null) builder.append(" ($pendingComments)")
    builder.append(StringUtil.ELLIPSIS)
    return builder.toString()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dataProvider = e.getRequiredData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)
    val details = dataProvider.detailsData.loadedDetails ?: return
    val reviewDataProvider = dataProvider.reviewData
    val pendingReviewFuture = reviewDataProvider.loadPendingReview()
    val parentComponent = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: return

    var cancelRunnable: (() -> Unit)? = null
    val cancelActionListener = ActionListener {
      cancelRunnable?.invoke()
    }

    val container = createPopupComponent(
      reviewDataProvider,
      reviewDataProvider.submitReviewCommentDocument,
      cancelActionListener,
      pendingReviewFuture,
      details.viewerDidAuthor
    )
    val popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(container.component, container.preferredFocusableComponent)
      .setFocusable(true)
      .setRequestFocus(true)
      .setResizable(true)
      .createPopup()

    cancelRunnable = { popup.cancel() }

    popup.showUnderneathOf(parentComponent)
  }

  private fun createPopupComponent(
    reviewDataProvider: GHPRReviewDataProvider,
    document: Document,
    cancelActionListener: ActionListener,
    pendingReviewFuture: CompletableFuture<GHPullRequestPendingReview?>,
    viewerIsAuthor: Boolean
  ): ComponentContainer {
    return object : ComponentContainer {
      private val wrapper = Wrapper().apply {
        isOpaque = true
        background = JBUI.CurrentTheme.List.BACKGROUND
        preferredSize = JBDimension(500, 200)
      }
      private val editor = createEditor(document)
      private val errorModel = GHSimpleErrorPanelModel(GithubBundle.message("pull.request.review.submit.error"))

      private val approveButton = object : InstallButton(GithubBundle.message("pull.request.review.submit.approve.button"), true) {
        override fun setTextAndSize() {}
      }
      private val rejectButton = JButton(GithubBundle.message("pull.request.review.submit.request.changes")).apply {
        isOpaque = false
      }
      private val commentButton = JButton(GithubBundle.message("pull.request.review.submit.comment.button")).apply {
        isOpaque = false
        toolTipText = GithubBundle.message("pull.request.review.submit.comment.description")
      }
      private val discardButton = InlineIconButton(
        icon = CollaborationToolsIcons.Delete,
        hoveredIcon = CollaborationToolsIcons.DeleteHovered,
        tooltip = GithubBundle.message("pull.request.discard.pending.comments")
      ).apply {
        border = JBUI.Borders.empty(5)
      }
      private val closeButton = InlineIconButton(
        icon = AllIcons.Actions.Close,
        hoveredIcon = AllIcons.Actions.CloseHovered
      ).apply {
        border = JBUI.Borders.empty(5)
        actionListener = cancelActionListener
      }

      private val loadingLabel = JLabel(AnimatedIcon.Default())

      init {
        approveButton.isVisible = false
        rejectButton.isVisible = false
        discardButton.isVisible = false

        wrapper.setContent(loadingLabel)
        wrapper.repaint()
        pendingReviewFuture.thenAccept { pendingReview ->
          approveButton.apply {
            isVisible = !viewerIsAuthor
            addActionListener(createSubmitButtonActionListener(GHPullRequestReviewEvent.APPROVE, pendingReview))
          }
          rejectButton.apply {
            isVisible = !viewerIsAuthor
            addActionListener(createSubmitButtonActionListener(GHPullRequestReviewEvent.REQUEST_CHANGES, pendingReview))
          }
          commentButton.apply {
            addActionListener(createSubmitButtonActionListener(GHPullRequestReviewEvent.COMMENT, pendingReview))
          }

          if (pendingReview != null) {
            discardButton.isVisible = true
            discardButton.actionListener = ActionListener {
              val discardButtonMessageDialog = MessageDialogBuilder.yesNo(
                GithubBundle.message("pull.request.discard.pending.comments.dialog.title"),
                GithubBundle.message("pull.request.discard.pending.comments.dialog.msg")
              )
              if (discardButtonMessageDialog.ask(discardButton)) {
                reviewDataProvider.deleteReview(EmptyProgressIndicator(), pendingReview.id)
              }
            }
          }

          val submitActionComponent = createSubmitActionComponent(pendingReview)
          wrapper.setContent(submitActionComponent)
          wrapper.repaint()
          CollaborationToolsUIUtil.focusPanel(editor)
        }
      }

      override fun getComponent(): JComponent = wrapper

      override fun getPreferredFocusableComponent(): EditorTextField = editor

      override fun dispose() {}

      private fun createSubmitButtonActionListener(
        event: GHPullRequestReviewEvent,
        pendingReview: GHPullRequestPendingReview?
      ): ActionListener = ActionListener { e ->
        disableUI()
        val reviewId = pendingReview?.id
        if (reviewId == null) {
          reviewDataProvider.createReview(EmptyProgressIndicator(), event, editor.text)
        }
        else {
          reviewDataProvider.submitReview(EmptyProgressIndicator(), reviewId, event, editor.text)
        }.successOnEdt {
          cancelActionListener.actionPerformed(e)
          runWriteAction { document.setText("") }
        }.errorOnEdt {
          errorModel.error = it
          enableUI()
        }
      }

      private fun createSubmitActionComponent(pendingReview: GHPullRequestPendingReview?): JComponent {
        val titleLabel = JLabel(CollaborationToolsBundle.message("review.start.submit.action")).apply {
          font = font.deriveFont(font.style or Font.BOLD)
        }
        val titlePanel = JPanel(HorizontalLayout(0)).apply {
          isOpaque = false
          add(titleLabel, HorizontalLayout.LEFT)
          if (pendingReview != null) {
            val commentsCount = pendingReview.commentsCount
            add(Box.createRigidArea(JBDimension(5, 0)), HorizontalLayout.LEFT)
            val pendingCommentsLabel = JLabel(CollaborationToolsBundle.message("review.pending.comments.count", commentsCount)).apply {
              foreground = UIUtil.getContextHelpForeground()
            }
            add(pendingCommentsLabel, HorizontalLayout.LEFT)
          }
          add(discardButton, HorizontalLayout.RIGHT)
          add(closeButton, HorizontalLayout.RIGHT)
        }

        val errorPanel = GHHtmlErrorPanel.create(errorModel, SwingConstants.LEFT).apply {
          border = JBUI.Borders.empty(4)
        }

        val buttonsPanel = HorizontalListPanel(12).apply {
          add(approveButton)
          add(rejectButton)
          add(commentButton)
        }

        return JPanel(MigLayout(LC().insets("12").fill().flowY().noGrid().hideMode(3))).apply {
          isOpaque = false
          add(titlePanel, CC().growX())
          add(editor, CC().growX().growY())
          add(errorPanel, CC().minHeight("32").growY().growPrioY(0))
          add(buttonsPanel, CC())
        }
      }

      private fun createEditor(document: Document): EditorTextField = EditorTextField(document, null, FileTypes.PLAIN_TEXT).apply {
        setOneLineMode(false)
        putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
        setPlaceholder(GithubBundle.message("pull.request.review.comment.empty.text"))
        addSettingsProvider {
          it.settings.isUseSoftWraps = true
          it.setVerticalScrollbarVisible(true)
          it.scrollPane.viewportBorder = JBUI.Borders.emptyLeft(4)
          it.putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
        }
      }

      private fun disableUI() {
        editor.isEnabled = false
        approveButton.isEnabled = false
        rejectButton.isEnabled = false
        commentButton.isEnabled = false
        discardButton.isEnabled = false
      }

      private fun enableUI() {
        editor.isEnabled = true
        approveButton.isEnabled = true
        rejectButton.isEnabled = true
        commentButton.isEnabled = true
        discardButton.isEnabled = true
      }
    }
  }

  override fun updateButtonFromPresentation(button: JButton, presentation: Presentation) {
    super.updateButtonFromPresentation(button, presentation)
    val prefix = presentation.getClientProperty(PROP_PREFIX) ?: CollaborationToolsBundle.message("review.start.submit.action")
    button.text = prefix + presentation.text
    ClientProperty.put(button, DarculaButtonUI.DEFAULT_STYLE_KEY, presentation.getClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY))
  }

  companion object {
    private val PROP_PREFIX: Key<String> = Key("PREFIX")
  }
}