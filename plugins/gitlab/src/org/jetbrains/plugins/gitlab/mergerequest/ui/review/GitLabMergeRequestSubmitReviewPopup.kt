// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.review

import com.intellij.collaboration.async.inverted
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.util.*
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.InstallButton
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.vcsUtil.showAbove
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.Component
import java.awt.Font
import java.awt.event.ActionListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

internal object GitLabMergeRequestSubmitReviewPopup {
  suspend fun show(vm: GitLabMergeRequestSubmitReviewViewModel, parentComponent: Component, above: Boolean = false) {
    withContext(Dispatchers.Main) {
      val container = createPopupComponent(vm)
      val popup = createPopup(container)

      if (above) {
        popup.showAbove(parentComponent)
      }
      else {
        popup.showUnderneathOf(parentComponent)
      }
      popup.awaitClose()
    }
  }

  suspend fun show(vm: GitLabMergeRequestSubmitReviewViewModel, project: Project) {
    withContext(Dispatchers.Main) {
      val container = createPopupComponent(vm)
      val popup = createPopup(container)

      popup.showCenteredInCurrentWindow(project)
      popup.awaitClose()
    }
  }

  private fun createPopup(container: ComponentContainer): JBPopup = JBPopupFactory.getInstance()
    // popup requires a properly focusable component, will not look under a panel
    .createComponentPopupBuilder(container.component, container.preferredFocusableComponent)
    .setFocusable(true)
    .setRequestFocus(true)
    .setResizable(true)
    .createPopup()

  private fun CoroutineScope.createPopupComponent(vm: GitLabMergeRequestSubmitReviewViewModel): ComponentContainer {
    val cs = this
    return object : ComponentContainer {
      private val editor = createEditor(vm.text)

      private val approveButton = object : InstallButton(GitLabBundle.message("merge.request.approve.action"), true) {
        init {
          toolTipText = GitLabBundle.message("merge.request.approve.action.tooltip")
          bindVisibilityIn(cs, vm.isApproved.inverted())
          bindDisabledIn(cs, vm.isBusy)

          addActionListener {
            vm.approve()
          }
        }

        override fun setTextAndSize() {}
      }
      private val unApproveButton = JButton(GitLabBundle.message("merge.request.revoke.action")).apply {
        isOpaque = false
        toolTipText = GitLabBundle.message("merge.request.revoke.action.tooltip")
        bindVisibilityIn(cs, vm.isApproved)
        bindDisabledIn(cs, vm.isBusy)
        addActionListener {
          vm.unApprove()
        }
      }
      private val submitButton = JButton(CollaborationToolsBundle.message("review.submit.action")).apply {
        isOpaque = false
        toolTipText = GitLabBundle.message("merge.request.submit.action.tooltip")
        bindEnabledIn(cs, combine(vm.isBusy, vm.text, vm.draftCommentsCount) { busy, text, draftComments ->
          // Is enabled when not busy and: the text is not blank, or there are draft comments to submit
          !busy && (text.isNotBlank() || draftComments > 0)
        })
        addActionListener {
          vm.submit()
        }
      }
      private val closeButton = InlineIconButton(
        icon = AllIcons.Actions.Close,
        hoveredIcon = AllIcons.Actions.CloseHovered
      ).apply {
        border = JBUI.Borders.empty(5)
        actionListener = ActionListener { vm.cancel() }
      }

      private val panel = createPanel()

      override fun getComponent(): JComponent = panel

      override fun getPreferredFocusableComponent(): EditorTextField = editor

      override fun dispose() {}

      private fun createPanel(): JComponent {
        val titleLabel = JLabel(CollaborationToolsBundle.message("review.submit.review.title")).apply {
          font = font.deriveFont(font.style or Font.BOLD)
        }
        val titlePanel = JPanel(HorizontalLayout(5)).apply {
          isOpaque = false
          add(titleLabel, HorizontalLayout.LEFT)
          bindChildIn(cs, vm.draftCommentsCount, HorizontalLayout.LEFT, 1) {
            if (it <= 0) null else JLabel(CollaborationToolsBundle.message("review.pending.comments.count", it))
          }
          add(closeButton, HorizontalLayout.RIGHT)
        }

        val errorPanel = SimpleHtmlPane().apply {
          bindTextIn(cs, vm.error.map { CollaborationToolsBundle.message("review.comment.placeholder") + "\n" + it?.localizedMessage })
          bindVisibilityIn(cs, vm.error.map { it != null })
        }

        // gap 12 minus button borders (3x2)
        val buttonsPanel = HorizontalListPanel(6).apply {
          add(approveButton)
          add(unApproveButton)
          add(submitButton)
        }

        return JPanel(MigLayout(LC().insets("12").fill().flowY().noGrid().hideMode(3))).apply {
          background = JBUI.CurrentTheme.Popup.BACKGROUND
          preferredSize = JBDimension(500, 200)

          add(titlePanel, CC().growX())
          add(editor, CC().growX().growY())
          add(errorPanel, CC().growY().growPrioY(0))
          add(buttonsPanel, CC())
        }
      }

      private fun createEditor(text: MutableStateFlow<String>): EditorTextField =
        EditorTextField(text.value, null, FileTypes.PLAIN_TEXT).apply {
          setOneLineMode(false)
          setPlaceholder(CollaborationToolsBundle.message("review.comment.placeholder"))
          addSettingsProvider {
            it.settings.isUseSoftWraps = true
            it.setVerticalScrollbarVisible(true)
            it.scrollPane.viewportBorder = JBUI.Borders.emptyLeft(4)
            it.putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
          }
          document.bindTextIn(cs, text)
        }
    }
  }

  private suspend fun JBPopup.awaitClose() {
    if (isDisposed) {
      currentCoroutineContext().cancel()
      return
    }
    try {
      suspendCancellableCoroutine<Unit> { cont ->
        addListener(object : JBPopupListener {
          override fun onClosed(event: LightweightWindowEvent) {
            if (event.isOk) {
              cont.resume(Unit)
            }
            else {
              cont.cancel()
            }
          }
        })
      }
    }
    catch (e: CancellationException) {
      cancel()
      throw e
    }
  }
}