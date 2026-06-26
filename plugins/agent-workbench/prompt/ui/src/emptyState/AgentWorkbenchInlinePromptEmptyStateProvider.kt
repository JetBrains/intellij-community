// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.emptyState

import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.ui.AGENT_PROMPT_INLINE_EMPTY_STATE_MAXIMUM_SIZE
import com.intellij.agent.workbench.prompt.ui.AGENT_PROMPT_INLINE_EMPTY_STATE_MINIMUM_SIZE
import com.intellij.agent.workbench.prompt.ui.AGENT_PROMPT_INLINE_EMPTY_STATE_PREFERRED_SIZE
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteHostMode
import com.intellij.agent.workbench.prompt.ui.AgentPromptPaletteContent
import com.intellij.agent.workbench.prompt.ui.AgentPromptPalettePopup
import com.intellij.agent.workbench.prompt.ui.AgentPromptSessionsMessageResolver
import com.intellij.agent.workbench.prompt.ui.AgentPromptUiSessionStateService
import com.intellij.agent.workbench.prompt.ui.createAgentPromptPaletteContent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.EditorEmptyStateComponentProvider
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
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
  override fun isAvailable(splitters: EditorsSplitters): Boolean = isInlineEmptyStatePromptEnabled()

  override suspend fun createComponent(splitters: EditorsSplitters): JComponent? {
    if (!isInlineEmptyStatePromptEnabled()) {
      return null
    }
    return withContext(Dispatchers.EDT) {
      val project = splitters.manager.project
      writeIntentReadAction {
        val component = AgentWorkbenchInlinePromptEmptyStateComponent(project)
        try {
          component.ensureContentInitialized()
          component
        }
        catch (e: Throwable) {
          Disposer.dispose(component)
          throw e
        }
      }
    }
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
    isOpaque = false
    background = JBUI.CurrentTheme.Popup.BACKGROUND
    border = RoundedLineBorder(UIUtil.getBoundsColor(), inlinePromptArcDiameter())
    preferredSize = AGENT_PROMPT_INLINE_EMPTY_STATE_PREFERRED_SIZE
    minimumSize = AGENT_PROMPT_INLINE_EMPTY_STATE_MINIMUM_SIZE
    maximumSize = AGENT_PROMPT_INLINE_EMPTY_STATE_MAXIMUM_SIZE
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
    var promptContent: AgentPromptPaletteContent? = null
    try {
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
        hostMode = AgentPromptPaletteHostMode.INLINE_EMPTY_STATE,
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
      promptContent?.dispose("Agent prompt empty state initialization failed")
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

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as? Graphics2D
    if (g2 == null) {
      super.paintComponent(g)
      return
    }
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.color = background
      val arc = inlinePromptArcDiameter()
      g2.fillRoundRect(0, 0, width, height, arc, arc)
    }
    finally {
      g2.dispose()
    }
    super.paintComponent(g)
  }

  private fun configureInlineContent(promptContent: AgentPromptPaletteContent) {
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

/**
 * Feature flag for the inline Agent prompt shown in the empty editor.
 * When disabled, the inline composer is not created and the
 * `AgentWorkbenchGlobalPromptEmptyTextProvider` painted hint is used instead.
 */
internal const val INLINE_EMPTY_STATE_PROMPT_PROPERTY: String = "agent.workbench.inline.empty.state.prompt"

internal fun isInlineEmptyStatePromptEnabled(): Boolean =
  System.getProperty(INLINE_EMPTY_STATE_PROMPT_PROPERTY, "true").toBoolean()

private fun inlinePromptArcDiameter(): Int = JBUI.scale(JBUI.getInt("Island.arc", 20))
