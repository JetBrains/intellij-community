// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction

internal class AgentChatNextProposedPlanAction : DumbAwareAction {
  private val resolveFileEditor: (AnActionEvent) -> AgentChatFileEditor?
  private val canNavigate: (AgentChatFileEditor, AgentChatSemanticNavigationDirection) -> Boolean
  private val navigate: (AgentChatFileEditor, AgentChatSemanticNavigationDirection) -> Boolean

  @Suppress("unused")
  constructor() : this(
    canNavigate = AgentChatFileEditor::canNavigateProposedPlan,
    navigate = AgentChatFileEditor::navigateProposedPlan,
  )

  internal constructor(
    resolveFileEditor: (AnActionEvent) -> AgentChatFileEditor? = ::resolveAgentChatFileEditor,
    canNavigate: (AgentChatFileEditor, AgentChatSemanticNavigationDirection) -> Boolean,
    navigate: (AgentChatFileEditor, AgentChatSemanticNavigationDirection) -> Boolean,
  ) {
    this.resolveFileEditor = resolveFileEditor
    this.canNavigate = canNavigate
    this.navigate = navigate
  }

  override fun actionPerformed(e: AnActionEvent) {
    resolveFileEditor(e)?.let { editor -> navigate(editor, AgentChatSemanticNavigationDirection.NEXT) }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible =
      resolveFileEditor(e)?.let { editor -> canNavigate(editor, AgentChatSemanticNavigationDirection.NEXT) } == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AgentChatPreviousProposedPlanAction : DumbAwareAction {
  private val resolveFileEditor: (AnActionEvent) -> AgentChatFileEditor?
  private val canNavigate: (AgentChatFileEditor, AgentChatSemanticNavigationDirection) -> Boolean
  private val navigate: (AgentChatFileEditor, AgentChatSemanticNavigationDirection) -> Boolean

  @Suppress("unused")
  constructor() : this(
    canNavigate = AgentChatFileEditor::canNavigateProposedPlan,
    navigate = AgentChatFileEditor::navigateProposedPlan,
  )

  internal constructor(
    resolveFileEditor: (AnActionEvent) -> AgentChatFileEditor? = ::resolveAgentChatFileEditor,
    canNavigate: (AgentChatFileEditor, AgentChatSemanticNavigationDirection) -> Boolean,
    navigate: (AgentChatFileEditor, AgentChatSemanticNavigationDirection) -> Boolean,
  ) {
    this.resolveFileEditor = resolveFileEditor
    this.canNavigate = canNavigate
    this.navigate = navigate
  }

  override fun actionPerformed(e: AnActionEvent) {
    resolveFileEditor(e)?.let { editor -> navigate(editor, AgentChatSemanticNavigationDirection.PREVIOUS) }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible =
      resolveFileEditor(e)?.let { editor -> canNavigate(editor, AgentChatSemanticNavigationDirection.PREVIOUS) } == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun resolveAgentChatFileEditor(e: AnActionEvent): AgentChatFileEditor? {
  return PlatformDataKeys.FILE_EDITOR.getData(e.dataContext) as? AgentChatFileEditor
}
