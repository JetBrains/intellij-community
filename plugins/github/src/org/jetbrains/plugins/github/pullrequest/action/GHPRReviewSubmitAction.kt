// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
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
import com.intellij.util.ui.*
import icons.GithubIcons
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReview
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.ui.GHHtmlErrorPanel
import org.jetbrains.plugins.github.ui.GHSimpleErrorPanelModel
import org.jetbrains.plugins.github.ui.InlineIconButton
import org.jetbrains.plugins.github.util.errorOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.ActionListener
import javax.swing.*

class GHPRReviewSubmitAction : JButtonAction(StringUtil.ELLIPSIS, GithubBundle.message("pull.request.review.submit.action.description")) {

  override fun update(e: AnActionEvent) {
    val dataProvider = e.getData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)
    val reviewData = dataProvider?.reviewData
    val details = dataProvider?.detailsData?.loadedDetails
    e.presentation.isVisible = true
    val pendingReviewFuture = reviewData?.loadPendingReview()
    e.presentation.isEnabled = (pendingReviewFuture?.isDone ?: false) && details != null
    e.presentation.putClientProperty(PROP_PREFIX, getPrefix(e.place))

    if (e.presentation.isEnabledAndVisible) {
      val review = try {
        pendingReviewFuture?.getNow(null)
      }
      catch (e: Exception) {
        null
      }
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
    val dataProvider = e.getRequiredData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)
    val details = dataProvider.detailsData.loadedDetails ?: return
    val reviewDataProvider = dataProvider.reviewData
    val pendingReviewFuture = reviewDataProvider.loadPendingReview()
    if (!pendingReviewFuture.isDone) return
    val pendingReview = try {
      pendingReviewFuture.getNow(null)
    }
    catch (e: Exception) {
      null
    }
    val parentComponent = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: return

    var cancelRunnable: (() -> Unit)? = null
    val cancelActionListener = ActionListener {
      cancelRunnable?.invoke()
    }

    val container = createPopupComponent(reviewDataProvider, reviewDataProvider.submitReviewCommentDocument,
                                         cancelActionListener, pendingReview,
                                         details.viewerDidAuthor)
    val popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(container.component, container.preferredFocusableComponent)
      .setFocusable(true)
      .setRequestFocus(true)
      .setResizable(true)
      .createPopup()

    cancelRunnable = { popup.cancel() }

    popup.showUnderneathOf(parentComponent)
  }

  private fun createPopupComponent(reviewDataProvider: GHPRReviewDataProvider,
                                   document: Document,
                                   cancelActionListener: ActionListener,
                                   pendingReview: GHPullRequestPendingReview?,
                                   viewerIsAuthor: Boolean): ComponentContainer {
    return object : ComponentContainer {

      private val editor = createEditor(document)
      private val errorModel = GHSimpleErrorPanelModel(GithubBundle.message("pull.request.review.submit.error"))

      private val approveButton = if (!viewerIsAuthor) JButton(GithubBundle.message("pull.request.review.submit.approve.button")).apply {
        addActionListener(createSubmitButtonActionListener(GHPullRequestReviewEvent.APPROVE))
      }
      else null

      private val rejectButton = if (!viewerIsAuthor) JButton(GithubBundle.message("pull.request.review.submit.request.changes")).apply {
        addActionListener(createSubmitButtonActionListener(GHPullRequestReviewEvent.REQUEST_CHANGES))
      }
      else null

      private val commentButton = JButton(GithubBundle.message("pull.request.review.submit.comment.button")).apply {
        toolTipText = GithubBundle.message("pull.request.review.submit.comment.description")
        addActionListener(createSubmitButtonActionListener(GHPullRequestReviewEvent.COMMENT))
      }

      private fun createSubmitButtonActionListener(event: GHPullRequestReviewEvent): ActionListener = ActionListener { e ->
        editor.isEnabled = false
        approveButton?.isEnabled = false
        rejectButton?.isEnabled = false
        commentButton.isEnabled = false
        discardButton?.isEnabled = false

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
          editor.isEnabled = true
          approveButton?.isEnabled = true
          rejectButton?.isEnabled = true
          commentButton.isEnabled = true
          discardButton?.isEnabled = true
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
        val titleLabel = JLabel(GithubBundle.message("pull.request.review.submit.review")).apply {
          font = font.deriveFont(font.style or Font.BOLD)
        }
        val titlePanel = HorizontalBox().apply {
          border = JBUI.Borders.empty(4, 4, 4, 4)

          add(titleLabel)
          if (pendingReview != null) {
            val commentsCount = pendingReview.comments.totalCount!!
            add(Box.createRigidArea(JBDimension(5, 0)))
            add(JLabel(GithubBundle.message("pull.request.review.pending.comments.count", commentsCount))).apply {
              foreground = UIUtil.getContextHelpForeground()
            }
          }
          add(Box.createHorizontalGlue())
          discardButton?.let { add(it) }
          add(InlineIconButton(AllIcons.Actions.Close, AllIcons.Actions.CloseHovered).apply {
            actionListener = cancelActionListener
          })
        }

        val errorPanel = GHHtmlErrorPanel.create(errorModel, SwingConstants.LEFT).apply {
          border = JBUI.Borders.empty(4)
        }

        val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
          border = JBUI.Borders.empty(4)

          if (!viewerIsAuthor) add(approveButton)
          if (!viewerIsAuthor) add(rejectButton)
          add(commentButton)
        }

        return JPanel(MigLayout(LC().gridGap("0", "0")
                                  .insets("0", "0", "0", "0")
                                  .fill().flowY().noGrid())).apply {
          isOpaque = false
          preferredSize = JBDimension(450, 165)

          add(titlePanel, CC().growX())
          add(editor, CC().growX().growY()
            .gap("0", "0", "0", "0"))
          add(errorPanel, CC().minHeight("${UI.scale(32)}").growY().growPrioY(0).hideMode(3)
            .gap("0", "0", "0", "0"))
          add(buttonsPanel, CC().alignX("right"))
        }
      }

      private fun createEditor(document: Document) = EditorTextField(document, null, FileTypes.PLAIN_TEXT).apply {
        setOneLineMode(false)
        putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
        setPlaceholder(GithubBundle.message("pull.request.review.comment.empty.text"))
        addSettingsProvider {
          it.settings.isUseSoftWraps = true
          it.setVerticalScrollbarVisible(true)
          it.scrollPane.border = IdeBorderFactory.createBorder(SideBorder.TOP or SideBorder.BOTTOM)
          it.scrollPane.viewportBorder = JBUI.Borders.emptyLeft(4)
          it.putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
        }
      }


      override fun getPreferredFocusableComponent() = editor

      override fun dispose() {}
    }
  }

  override fun createButton(): JButton {
    return object : JButton() {
      override fun isDefaultButton(): Boolean {
        return getClientProperty(PROP_DEFAULT) as? Boolean ?: super.isDefaultButton()
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