// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionCandidate
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JPanel

private const val MAX_VISIBLE_SUGGESTIONS = 3
private const val MAX_ACTION_LABEL_LENGTH = 30

internal class AgentPromptSuggestionsComponent(
  private val onSuggestionSelected: (AgentPromptSuggestionCandidate) -> Unit,
) {
  private val actionsPanel = JPanel().apply {
    isOpaque = false
    layout = FlowLayout(FlowLayout.LEFT, 0, 0)
  }

  val component = JPanel(BorderLayout()).apply {
    isOpaque = false
    isVisible = false
    name = "promptSuggestionsStrip"
    border = JBUI.Borders.emptyBottom(6)
    add(actionsPanel, BorderLayout.CENTER)
  }

  fun render(candidates: List<AgentPromptSuggestionCandidate>) {
    actionsPanel.removeAll()
    candidates.take(MAX_VISIBLE_SUGGESTIONS).forEachIndexed { index, candidate ->
      if (index > 0) {
        actionsPanel.add(createSeparator())
      }
      actionsPanel.add(createSuggestionAction(candidate))
    }
    component.isVisible = candidates.isNotEmpty()
    component.revalidate()
    component.repaint()
  }

  private fun createSuggestionAction(candidate: AgentPromptSuggestionCandidate): JButton {
    return ActionLink(shortenLabel(candidate.label)) {
      onSuggestionSelected(candidate)
    }.apply {
      name = "promptSuggestionAction:${candidate.id}"
      isFocusable = false
      autoHideOnDisable = false
      withFont(JBUI.Fonts.smallFont())
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.empty(2, 0)
      toolTipText = candidate.promptText
      resolveActionIcon(candidate.id)?.let { icon ->
        setIcon(icon, false)
      }
    }
  }

  private fun shortenLabel(label: @NlsSafe String): @NlsSafe String {
    return StringUtil.shortenTextWithEllipsis(label, MAX_ACTION_LABEL_LENGTH, 0, true)
  }

  private fun createSeparator(): JBLabel {
    return JBLabel("·").apply {
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.empty(0, 8)
    }
  }

  private fun resolveActionIcon(id: String): Icon? {
    return when {
      id.startsWith("tests.") -> AllIcons.RunConfigurations.Junit
      id.startsWith("vcs.") -> AllIcons.Actions.Commit
      id.startsWith("paths.") -> AllIcons.FileTypes.Diagram
      id.startsWith("editor.") -> when {
        id.contains("explain") -> AllIcons.Actions.IntentionBulb
        id.contains("refactor") -> AllIcons.Actions.RefactoringBulb
        id.contains("review") -> AllIcons.General.InspectionsEye
        else -> null
      }
      else -> null
    }
  }
}
