// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.CommonBundle
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.defaultButton
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
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
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*

private const val EMPTY_GAP = 4

internal object GHPRReviewSuggestedChangeComponentFactory2 {
  fun createIn(cs: CoroutineScope, vm: GHPRReviewCommentBodyViewModel, block: GHPRCommentBodyBlock.SuggestedChange): JComponent =
    RoundedPanel(BorderLayout(), 8).apply {
      border = JBUI.Borders.compound(JBUI.Borders.empty(EMPTY_GAP, 0), border)

      val titleLabel = JBLabel(GithubBundle.message("pull.request.timeline.comment.suggested.changes")).apply {
        isOpaque = false
        foreground = UIUtil.getContextHelpForeground()
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
          val applyLocallyAction = ApplyLocallySuggestedChangeAction(vm, block)
          val optionButton = JBOptionButton(applyLocallyAction, null).apply {
            val commitAction = CommitSuggestedChangeAction(vm, block, this)
            options = arrayOf(commitAction)
            isEnabled = block.applicability == GHPRCommentBodyBlock.SuggestionsApplicability.APPLICABLE
            optionTooltipText = when (block.applicability) {
              GHPRCommentBodyBlock.SuggestionsApplicability.APPLICABLE -> null
              GHPRCommentBodyBlock.SuggestionsApplicability.RESOLVED ->
                GithubBundle.message("pull.request.timeline.comment.suggested.changes.tooltip.resolved")
              GHPRCommentBodyBlock.SuggestionsApplicability.OUTDATED ->
                GithubBundle.message("pull.request.timeline.comment.suggested.changes.tooltip.outdated")
            }
          }
          cs.launchNow {
            vm.isOnReviewBranch.collect {
              if (!it) {
                applyLocallyAction.isEnabled = false
                optionButton.isEnabled = false
                optionButton.optionTooltipText =
                  GithubBundle.message("pull.request.timeline.comment.suggested.changes.tooltip.different.branch")
              }
              else {
                optionButton.applyApplicability(block.applicability)
              }
            }
          }
          add(optionButton, BorderLayout.EAST)
        }
      }

      add(topPanel, BorderLayout.NORTH)
      add(SimpleHtmlPane(block.bodyHtml), BorderLayout.CENTER)
    }

  private fun JBOptionButton.applyApplicability(applicability: GHPRCommentBodyBlock.SuggestionsApplicability) {
    isEnabled = applicability == GHPRCommentBodyBlock.SuggestionsApplicability.APPLICABLE
    action.isEnabled = isEnabled
    optionTooltipText = when (applicability) {
      GHPRCommentBodyBlock.SuggestionsApplicability.APPLICABLE -> null
      GHPRCommentBodyBlock.SuggestionsApplicability.RESOLVED ->
        GithubBundle.message("pull.request.timeline.comment.suggested.changes.tooltip.resolved")
      GHPRCommentBodyBlock.SuggestionsApplicability.OUTDATED ->
        GithubBundle.message("pull.request.timeline.comment.suggested.changes.tooltip.outdated")
    }
  }

  private class ApplyLocallySuggestedChangeAction(
    private val vm: GHPRReviewCommentBodyViewModel,
    private val block: GHPRCommentBodyBlock.SuggestedChange
  ) : AbstractAction(
    GithubBundle.message("pull.request.timeline.comment.suggested.changes.button")
  ) {
    override fun actionPerformed(event: ActionEvent) {
      block.patch?.let { vm.applySuggestionLocally(it) }
    }
  }

  private class CommitSuggestedChangeAction(
    private val vm: GHPRReviewCommentBodyViewModel,
    private val block: GHPRCommentBodyBlock.SuggestedChange,
    private val parent: JComponent
  ) : AbstractAction(GithubBundle.message("pull.request.timeline.comment.suggested.changes.action.commit.name")) {
    override fun actionPerformed(event: ActionEvent) {
      if (block.patch == null) return
      var cancelRunnable: (() -> Unit)? = null
      val cancelActionListener = ActionListener {
        cancelRunnable?.invoke()
      }

      val container = createPopupComponentContainer(vm.project, cancelActionListener)
      val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(container.component, container.preferredFocusableComponent)
        .setFocusable(true)
        .setRequestFocus(true)
        .setResizable(true)
        .createPopup()

      cancelRunnable = { popup.cancel() }

      popup.showUnderneathOf(parent)
    }

    private fun createPopupComponentContainer(project: Project, cancelActionListener: ActionListener): ComponentContainer =
      object : ComponentContainer {
        private val commitAction = ActionListener {
          if (commitEditor.text.isBlank()) {
            errorLabel.isVisible = true
            return@ActionListener
          }

          vm.commitSuggestion(block.patch!!, commitEditor.text)
        }

        private val errorLabel = JBLabel().apply {
          text = VcsBundle.message("error.no.commit.message")
          icon = AllIcons.General.Error
          foreground = NamedColorUtil.getErrorForeground()
          isVisible = false
        }

        private val commitEditor = CommitMessage(project, false, false, true, VcsBundle.message("commit.message.placeholder")).apply {
          editorField.apply {
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