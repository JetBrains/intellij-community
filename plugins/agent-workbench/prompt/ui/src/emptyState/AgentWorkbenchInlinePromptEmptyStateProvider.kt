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
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JLabel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingConstants

internal class AgentWorkbenchInlinePromptEmptyStateProvider : EditorEmptyStateComponentProvider {
  override suspend fun createComponent(splitters: EditorsSplitters): JComponent? = withContext(Dispatchers.EDT) {
    if (AgentPromptLaunchers.find() == null) {
      return@withContext null
    }

    val project = splitters.manager.project
    return@withContext AgentWorkbenchInlinePromptEmptyStateComponent(project)
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
  private var initializing: Boolean = false
  private var disposed: Boolean = false

  init {
    Disposer.register(this, parentDisposable)
    name = INLINE_PROMPT_COMPONENT_NAME
    isOpaque = true
    background = UIUtil.getPanelBackground()
    border = JBUI.Borders.customLine(UIUtil.getBoundsColor())
    preferredSize = JBUI.size(620, 172)
    minimumSize = Dimension(JBUI.scale(420), JBUI.scale(124))
    maximumSize = Dimension(JBUI.scale(760), preferredSize.height)
    isFocusable = true
    val accessibleName = AgentPromptBundle.message("inline.empty.state.prompt.accessible.name")
    getAccessibleContext().accessibleName = accessibleName
    getAccessibleContext().accessibleDescription = AgentPromptBundle.message("inline.empty.state.prompt.accessible.description")

    val activationMouseListener = object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        ensureContentInitialized(requestFocus = true)
      }
    }
    addMouseListener(activationMouseListener)

    add(JLabel(accessibleName, SwingConstants.CENTER).apply {
      isOpaque = false
      getAccessibleContext().accessibleName = accessibleName
      addMouseListener(activationMouseListener)
    }, BorderLayout.CENTER)

    val initializeActionName = "initializeAgentPrompt"
    actionMap.put(initializeActionName, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        ensureContentInitialized(requestFocus = true)
      }
    })
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), initializeActionName)
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), initializeActionName)
  }

  @RequiresEdt
  fun ensureContentInitialized(requestFocus: Boolean = false) {
    val existingContent = content
    if (existingContent != null) {
      if (requestFocus) {
        requestPromptFocus(existingContent)
      }
      return
    }
    if (disposed || initializing) {
      return
    }

    initializing = true
    val promptContentDisposable = Disposer.newDisposable("AgentWorkbenchInlinePromptEmptyStateContent")
    var promptContent: AgentPromptPaletteContent? = null
    try {
      Disposer.register(parentDisposable, promptContentDisposable)
      promptContent = createAgentPromptPaletteContent(
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
        parentDisposable = promptContentDisposable,
      )
      configureInlineContent(promptContent)
      removeAll()
      add(promptContent.rootPanel, BorderLayout.CENTER)
      promptContent.sessionController.initialize()
      promptContent.sessionController.installHandlers()
      content = promptContent
      revalidate()
      repaint()
      if (requestFocus) {
        requestPromptFocus(promptContent)
      }
    }
    catch (e: Throwable) {
      promptContent?.dispose("Agent prompt empty state initialization failed") ?: Disposer.dispose(promptContentDisposable)
      throw e
    }
    finally {
      initializing = false
    }
  }

  override fun dispose() {
    if (disposed) {
      return
    }
    disposed = true
    content?.dispose("Agent prompt empty state disposed")
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
    val promptContent = content ?: return
    promptContent.promptArea.text = ""
    promptContent.sessionController.initialize()
  }

  private fun requestPromptFocus(promptContent: AgentPromptPaletteContent) {
    IdeFocusManager.getInstance(project).requestFocusInProject(promptContent.promptArea, project)
  }
}

internal const val INLINE_PROMPT_COMPONENT_NAME: String = "AgentWorkbenchInlinePromptEmptyStateComponent"
private const val INLINE_PROMPT_ACTION_ID: String = "AgentWorkbenchPrompt.InlineEmptyState"
private const val INLINE_PROMPT_PLACE: String = "EditorEmptyState"
