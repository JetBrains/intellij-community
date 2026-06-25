// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.emptyState

import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchers
import com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteContent
import com.intellij.agent.workbench.prompt.ui.AgentPromptPalettePopup
import com.intellij.agent.workbench.prompt.ui.AgentPromptSessionsMessageResolver
import com.intellij.agent.workbench.prompt.ui.AgentPromptUiSessionStateService
import com.intellij.agent.workbench.prompt.ui.createAgentPromptPaletteContent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.EditorEmptyStateComponentProvider
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

internal class AgentWorkbenchInlinePromptEmptyStateProvider : EditorEmptyStateComponentProvider {
  override suspend fun createComponent(splitters: EditorsSplitters): JComponent? = withContext(Dispatchers.EDT) {
    if (AgentPromptLaunchers.find() == null) {
      return@withContext null
    }

    val project = splitters.manager.project
    val component = AgentWorkbenchInlinePromptEmptyStateComponent(project)
    component.initialize()
    return@withContext component
  }

  override fun disposeComponent(component: JComponent) {
    (component as? Disposable)?.let(Disposer::dispose)
  }
}

internal class AgentWorkbenchInlinePromptEmptyStateComponent(
  private val project: Project,
) : JPanel(BorderLayout()), Disposable {
  private val parentDisposable = Disposer.newDisposable("AgentWorkbenchInlinePromptEmptyState")
  private var content: AgentPromptPaletteContent? = null

  init {
    name = INLINE_PROMPT_COMPONENT_NAME
    isOpaque = true
    background = UIUtil.getPanelBackground()
    border = JBUI.Borders.customLine(UIUtil.getBoundsColor())
    preferredSize = JBUI.size(620, 172)
    minimumSize = Dimension(JBUI.scale(420), JBUI.scale(124))
    maximumSize = Dimension(JBUI.scale(760), preferredSize.height)
  }

  fun initialize() {
    val promptContent = createAgentPromptPaletteContent(
      invocationData = AgentPromptInvocationData(
        project = project,
        actionId = INLINE_PROMPT_ACTION_ID,
        actionText = AgentPromptBundle.message("inline.empty.state.prompt.accessible.name"),
        actionPlace = INLINE_PROMPT_PLACE,
        invokedAtMs = System.currentTimeMillis(),
      ),
      contextResolverService = project.service<AgentPromptContextResolverService>(),
      uiStateService = project.service<AgentPromptUiSessionStateService>(),
      sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptPalettePopup::class.java.classLoader),
      closeHost = ::resetAfterSubmit,
      isHostActive = { isShowing },
      revalidateHost = {
        revalidate()
        repaint()
      },
      parentDisposableName = "AgentWorkbenchInlinePromptEmptyStateContent",
      parentDisposable = parentDisposable,
    )
    content = promptContent
    configureInlineContent(promptContent)
    add(promptContent.rootPanel, BorderLayout.CENTER)
    promptContent.sessionController.initialize()
    promptContent.sessionController.installHandlers()
  }

  override fun dispose() {
    content?.let { promptContent ->
      promptContent.sessionController.onHostClosed()
      promptContent.sessionScope.cancel("Agent prompt empty state disposed")
    }
    content = null
    Disposer.dispose(parentDisposable)
  }

  private fun configureInlineContent(promptContent: AgentPromptPaletteContent) {
    val view = promptContent.view
    promptContent.rootPanel.apply {
      preferredSize = JBUI.size(620, 172)
      minimumSize = Dimension(JBUI.scale(420), JBUI.scale(124))
      maximumSize = Dimension(JBUI.scale(760), preferredSize.height)
      border = JBUI.Borders.empty(8)
    }
    view.tabbedPane.isVisible = false
    view.bottomPanel.isVisible = true
    view.existingTaskScrollPane.isVisible = false
    view.footerPanel.isVisible = true
    view.footerPinToolbar.component.isVisible = false
    view.promptPanel.border = JBUI.Borders.empty(4, 8)
    view.promptEditorPanel.border = JBUI.Borders.customLine(UIUtil.getBoundsColor())
    view.promptEditorPanel.preferredSize = JBUI.size(0, 86)
    view.generationSettingsPanel.border = JBUI.Borders.empty(0, 6, 4, 6)
    val accessibleName = AgentPromptBundle.message("inline.empty.state.prompt.accessible.name")
    val accessibleDescription = AgentPromptBundle.message("inline.empty.state.prompt.accessible.description")
    promptContent.promptArea.accessibleContext.accessibleName = accessibleName
    promptContent.promptArea.accessibleContext.accessibleDescription = accessibleDescription
    promptContent.promptArea.addSettingsProvider { editor ->
      editor.contentComponent.accessibleContext.accessibleName = accessibleName
      editor.contentComponent.accessibleContext.accessibleDescription = accessibleDescription
    }
    promptContent.promptArea.editor?.contentComponent?.accessibleContext?.accessibleName = accessibleName
    promptContent.promptArea.editor?.contentComponent?.accessibleContext?.accessibleDescription = accessibleDescription
  }

  private fun resetAfterSubmit() {
    content?.promptArea?.text = ""
    content?.sessionController?.initialize()
  }
}

internal const val INLINE_PROMPT_COMPONENT_NAME: String = "AgentWorkbenchInlinePromptEmptyStateComponent"
private const val INLINE_PROMPT_ACTION_ID: String = "AgentWorkbenchPrompt.InlineEmptyState"
private const val INLINE_PROMPT_PLACE: String = "EditorEmptyState"
