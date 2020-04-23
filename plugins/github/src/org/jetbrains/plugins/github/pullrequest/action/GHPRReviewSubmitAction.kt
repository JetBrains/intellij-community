// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonPainter
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.EditorTextField
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.HorizontalBox
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JButtonAction
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.GithubIcons
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReview
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRReviewDataProvider
import org.jetbrains.plugins.github.ui.InlineIconButton
import org.jetbrains.plugins.github.util.successOnEdt
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.border.Border

class GHPRReviewSubmitAction : JButtonAction(StringUtil.ELLIPSIS, GithubBundle.message("pull.request.review.submit.action.description")) {

  override fun update(e: AnActionEvent) {
    val reviewData = e.getData(GHPRActionKeys.ACTION_DATA_CONTEXT)?.pullRequestDataProvider?.reviewData
    e.presentation.isVisible = true
    val pendingReviewFuture = reviewData?.loadPendingReview()
    e.presentation.isEnabled = pendingReviewFuture?.isDone ?: false
    e.presentation.putClientProperty(PROP_PREFIX, getPrefix(e.place))

    if (e.presentation.isEnabledAndVisible) {
      val review = pendingReviewFuture?.getNow(null)
      val pendingReview = review != null
      val comments = review?.comments?.totalCount

      e.presentation.text = getText(comments)
      e.presentation.putClientProperty(PROP_DEFAULT, pendingReview)
    }

    updateButtonFromPresentation(e)
  }

  private fun getPrefix(place: String) = if (place == ActionPlaces.DIFF_TOOLBAR) GithubBundle.message("pull.request.review.submit")
  else GithubBundle.message("pull.request.review.submit.review")

  private fun getText(pendingComments: Int?): String {
    val builder = StringBuilder()
    if (pendingComments != null) builder.append(" ($pendingComments)")
    builder.append(StringUtil.ELLIPSIS)
    return builder.toString()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dataContext = e.getRequiredData(GHPRActionKeys.ACTION_DATA_CONTEXT)
    val reviewDataProvider = dataContext.pullRequestDataProvider?.reviewData ?: return
    val pendingReviewFuture = reviewDataProvider.loadPendingReview()
    if (!pendingReviewFuture.isDone) return
    val pendingReview = pendingReviewFuture.getNow(null)
    val parentComponent = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: return

    var cancelRunnable: (() -> Unit)? = null
    val cancelActionListener = ActionListener {
      cancelRunnable?.invoke()
    }

    val container = createPopupComponent(reviewDataProvider, dataContext.submitReviewCommentDocument, cancelActionListener, pendingReview)
    val popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(container.component, container.preferredFocusableComponent)
      .setFocusable(true)
      .setRequestFocus(true)
      .createPopup()

    cancelRunnable = { popup.cancel() }

    popup.showUnderneathOf(parentComponent)
  }

  private fun createPopupComponent(reviewDataProvider: GHPRReviewDataProvider,
                                   document: Document,
                                   cancelActionListener: ActionListener,
                                   pendingReview: GHPullRequestPendingReview?): ComponentContainer {
    return object : ComponentContainer {

      private val editor = createEditor(document)

      private val approveButton = JButton(GithubBundle.message("pull.request.review.submit.approve.button")).apply {
        addActionListener(createSubmitButtonActionListener(GHPullRequestReviewEvent.APPROVE))
      }

      private val rejectButton = JButton(GithubBundle.message("pull.request.review.submit.request.changes")).apply {
        addActionListener(createSubmitButtonActionListener(GHPullRequestReviewEvent.REQUEST_CHANGES))
      }

      private val commentButton = JButton(GithubBundle.message("pull.request.review.submit.comment.button")).apply {
        toolTipText = GithubBundle.message("pull.request.review.submit.comment.description")
        addActionListener(createSubmitButtonActionListener(GHPullRequestReviewEvent.COMMENT))
      }

      private fun createSubmitButtonActionListener(event: GHPullRequestReviewEvent): ActionListener = ActionListener { e ->
        editor.isEnabled = false
        approveButton.isEnabled = false
        rejectButton.isEnabled = false
        commentButton.isEnabled = false
        discardButton?.isEnabled = false

        reviewDataProvider.submitReview(EmptyProgressIndicator(), pendingReview?.id, event, editor.text).successOnEdt {
          cancelActionListener.actionPerformed(e)
          runWriteAction { document.setText("") }
        }
      }

      private val discardButton: InlineIconButton?

      init {
        discardButton = pendingReview?.let { review ->
          InlineIconButton(GithubIcons.Delete, GithubIcons.DeleteHovered,
                           tooltip = GithubBundle.message("pull.request.discard.pending.comments")).apply {
            actionListener = ActionListener {
              if (Messages.showConfirmationDialog(this, GithubBundle.message("pull.request.discard.pending.comments.dialog.msg"),
                                                  GithubBundle.message("pull.request.discard.pending.comments.dialog.title"),
                                                  Messages.getYesButton(), Messages.getNoButton()) == Messages.YES) {
                reviewDataProvider.deleteReview(EmptyProgressIndicator(), review.id)
              }
            }
          }
        }
      }

      override fun getComponent(): JComponent {
        val title = JLabel(GithubBundle.message("pull.request.review.submit.review")).apply {
          font = font.deriveFont(font.style or Font.BOLD)
        }
        val titlePanel = HorizontalBox().apply {
          border = JBUI.Borders.empty(4, 4, 4, 4)

          add(title)
          if (pendingReview != null) {
            val commentsCount = pendingReview.comments.totalCount!!
            add(JLabel("  $commentsCount pending comment${if (commentsCount > 1) "s" else ""}  ")).apply {
              foreground = UIUtil.getContextHelpForeground()
            }
          }
          add(Box.createHorizontalGlue())
          discardButton?.let { add(it) }
          add(InlineIconButton(AllIcons.Actions.Close, AllIcons.Actions.CloseHovered).apply {
            actionListener = cancelActionListener
          })
        }

        val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
          border = JBUI.Borders.empty(8, 4, 4, 4)

          add(approveButton)
          add(rejectButton)
          add(commentButton)
        }

        return BorderLayoutPanel().andTransparent()
          .addToCenter(editor)
          .addToTop(titlePanel)
          .addToBottom(buttonsPanel)
      }

      private fun createEditor(document: Document) = EditorTextField(document, null, FileTypes.PLAIN_TEXT).apply {
        setOneLineMode(false)
        putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
        setPlaceholder(GithubBundle.message("pull.request.review.comment.empty.text"))
        addSettingsProvider {
          it.settings.additionalLinesCount = 2
          it.setVerticalScrollbarVisible(true)
          it.scrollPane.border = IdeBorderFactory.createBorder(SideBorder.TOP or SideBorder.BOTTOM)
          it.putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
        }
      }


      override fun getPreferredFocusableComponent() = editor

      override fun dispose() {}
    }
  }

  override fun createButtonBorder(button: JButton): Border {
    return JBUI.Borders.empty(0, if (UIUtil.isUnderDefaultMacTheme()) 6 else 4)
  }

  override fun createButton(): JButton {
    return object : JButton() {
      override fun isDefaultButton(): Boolean {
        return getClientProperty(PROP_DEFAULT) as? Boolean ?: super.isDefaultButton()
      }

      override fun updateUI() {
        super.updateUI()
        border = object : DarculaButtonPainter() {
          override fun getBorderInsets(c: Component) = JBUI.insets(0)
        }
      }
    }
  }

  override fun updateButtonFromPresentation(button: JButton, presentation: Presentation) {
    super.updateButtonFromPresentation(button, presentation)
    val prefix = presentation.getClientProperty(PROP_PREFIX) as? String ?: GithubBundle.message("pull.request.review.submit.review")
    button.text = prefix + presentation.text
    button.putClientProperty(PROP_DEFAULT, presentation.getClientProperty(PROP_DEFAULT))
  }

  companion object {
    private const val PROP_PREFIX = "PREFIX"
    private const val PROP_DEFAULT = "DEFAULT"
  }
}