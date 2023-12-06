// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.CommonBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.defaultButton
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.comment.GHSuggestedChangeApplier
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*

class GHPRReviewSuggestedChangeComponentFactory(
  private val project: Project,
  private val thread: GHPRReviewThreadModel,
  private val suggestionApplier: GHSuggestedChangeApplier,
  private val suggestedChangeHelper: GHPRSuggestedChangeHelper
) {
  fun create(content: String): JComponent = RoundedPanel(BorderLayout(), 8).apply {
    isOpaque = false
    border = JBUI.Borders.compound(JBUI.Borders.empty(EMPTY_GAP, 0), border)

    val titleLabel = JBLabel(GithubBundle.message("pull.request.timeline.comment.suggested.changes")).apply {
      isOpaque = false
      foreground = UIUtil.getContextHelpForeground()
    }

    val applyLocallyAction = ApplyLocallySuggestedChangeAction()
    val optionButton = JBOptionButton(applyLocallyAction, null).apply {
      val commitAction = CommitSuggestedChangeAction(this)
      options = arrayOf(commitAction)
    }

    val topPanel = JBUI.Panels.simplePanel().apply {
      border = JBUI.Borders.compound(IdeBorderFactory.createBorder(SideBorder.BOTTOM),
                                     JBUI.Borders.empty(EMPTY_GAP, 2 * EMPTY_GAP))
      background = JBColor.lazy {
        val scheme = EditorColorsManager.getInstance().globalScheme
        scheme.defaultBackground
      }

      add(titleLabel, BorderLayout.WEST)
      if (Registry.`is`("github.suggested.changes.apply")) {
        add(optionButton, BorderLayout.EAST)
      }
    }

    add(topPanel, BorderLayout.NORTH)
    add(SimpleHtmlPane(content), BorderLayout.CENTER)

    suggestedChangeHelper.isCorrectBranch.addAndInvokeListener { updateUI(optionButton, it, thread.isOutdated, thread.isResolved) }
  }

  private fun updateUI(optionButton: JBOptionButton, isCorrectBranch: Boolean, isOutdated: Boolean, isResolved: Boolean) {
    if (isCorrectBranch && !isOutdated && !isResolved) {
      optionButton.enableActions()
      return
    }

    optionButton.apply {
      disableActions()
      optionTooltipText = when {
        isOutdated -> GithubBundle.message("pull.request.timeline.comment.suggested.changes.tooltip.outdated")
        isResolved -> GithubBundle.message("pull.request.timeline.comment.suggested.changes.tooltip.resolved")
        else -> GithubBundle.message("pull.request.timeline.comment.suggested.changes.tooltip.different.branch")
      }
    }
  }

  private inner class ApplyLocallySuggestedChangeAction : AbstractAction(
    GithubBundle.message("pull.request.timeline.comment.suggested.changes.button")
  ) {
    override fun actionPerformed(event: ActionEvent) {
      object : Task.Backgroundable(
        project,
        GithubBundle.message("pull.request.timeline.comment.suggested.changes.progress.bar.apply"),
        true
      ) {
        private var applyStatus = ApplyPatchStatus.FAILURE

        override fun run(indicator: ProgressIndicator) {
          applyStatus = suggestionApplier.applySuggestedChange()
        }

        override fun onSuccess() {
          if (applyStatus == ApplyPatchStatus.SUCCESS) {
            suggestedChangeHelper.resolveThread(thread.id)
          }
        }
      }.queue()
    }
  }

  private inner class CommitSuggestedChangeAction(
    private val parent: JComponent
  ) : AbstractAction(GithubBundle.message("pull.request.timeline.comment.suggested.changes.action.commit.name")) {
    override fun actionPerformed(event: ActionEvent) {
      var cancelRunnable: (() -> Unit)? = null
      val cancelActionListener = ActionListener {
        cancelRunnable?.invoke()
      }

      val container = createPopupComponentContainer(suggestedChangeHelper.suggestedChangeCommitMessageDocument, cancelActionListener)
      val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(container.component, container.preferredFocusableComponent)
        .setFocusable(true)
        .setRequestFocus(true)
        .setResizable(true)
        .createPopup()

      cancelRunnable = { popup.cancel() }

      popup.showUnderneathOf(parent)
    }

    private fun createPopupComponentContainer(commentDocument: Document, cancelActionListener: ActionListener): ComponentContainer {
      return object : ComponentContainer {
        private val commitAction = ActionListener {
          if (commitEditor.text.isBlank()) {
            errorLabel.isVisible = true
            return@ActionListener
          }

          object : Task.Backgroundable(
            project,
            GithubBundle.message("pull.request.timeline.comment.suggested.changes.progress.bar.commit"),
            true
          ) {
            private var commitStatus = ApplyPatchStatus.FAILURE

            override fun run(indicator: ProgressIndicator) {
              commitStatus = suggestionApplier.commitSuggestedChanges(commitEditor.text)
            }

            override fun onSuccess() {
              cancelActionListener.actionPerformed(it)
              if (commitStatus == ApplyPatchStatus.SUCCESS) {
                suggestedChangeHelper.resolveThread(thread.id)
                runWriteAction { commentDocument.setText("") }
              }
            }
          }.queue()
        }

        private val errorLabel = JBLabel().apply {
          text = VcsBundle.message("error.no.commit.message")
          icon = AllIcons.General.Error
          foreground = NamedColorUtil.getErrorForeground()
          isVisible = false
        }

        private val commitEditor = CommitMessage(project, false, false, true, VcsBundle.message("commit.message.placeholder")).apply {
          editorField.apply {
            document = commentDocument
            document.addDocumentListener(object : DocumentListener {
              override fun documentChanged(event: DocumentEvent) {
                errorLabel.isVisible = false
              }
            })
            isOpaque = false
            addSettingsProvider { editor -> editor.scrollPane.border = JBUI.Borders.empty() }
          }
        }

        private val commitButton = JButton(VcsBundle.message("commit.progress.title")).apply {
          isOpaque = false
          addActionListener(commitAction)
        }.defaultButton()

        private val cancelButton = JButton(CommonBundle.getCancelButtonText()).apply {
          isOpaque = false
          addActionListener { cancelActionListener.actionPerformed(it) }
        }

        override fun getComponent(): JComponent = panel {
          row {
            cell(commitEditor)
              .align(Align.FILL)
          }.resizableRow()
          row(errorLabel) { }
          row {
            cell(commitButton).align(AlignX.LEFT)
            cell(cancelButton).align(AlignX.LEFT)
          }
        }.apply {
          background = EditorColorsManager.getInstance().globalScheme.defaultBackground
          preferredSize = JBDimension(450, 165)

          isFocusCycleRoot = true
          focusTraversalPolicy = LayoutFocusTraversalPolicy()

          registerKeyboardAction(commitAction, KeyStroke.getKeyStroke("ctrl ENTER"), JPanel.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        }

        override fun getPreferredFocusableComponent(): JComponent = commitEditor.editorField

        override fun dispose() {}
      }
    }
  }

  private fun JBOptionButton.enableActions() {
    action.isEnabled = true
    options?.forEach { it.isEnabled = true }
  }

  private fun JBOptionButton.disableActions() {
    action.isEnabled = false
    options?.forEach { it.isEnabled = false }
  }

  companion object {
    private const val EMPTY_GAP = 4
  }
}