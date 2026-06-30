// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.emptyState

import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchers
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
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.impl.EditorEmptyStateComponentHost
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
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.JPanel

internal class AgentWorkbenchInlinePromptEmptyStateProvider : EditorEmptyStateComponentProvider {
  override fun isAvailable(splitters: EditorsSplitters): Boolean = isInlineEmptyStatePromptEnabled()

  override suspend fun createComponent(splitters: EditorsSplitters): JComponent? {
    if (!isInlineEmptyStatePromptEnabled()) {
      return null
    }
    return withContext(Dispatchers.UI) {
      val project = splitters.manager.project
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

  override fun disposeComponent(component: JComponent) {
    (component as? Disposable)?.let(Disposer::dispose)
  }
}

@ApiStatus.Internal
class AgentWorkbenchInlinePromptEmptyStateComponent internal constructor(
  private val project: Project,
  private val configuration: AgentWorkbenchInlinePromptConfiguration = emptyStateInlinePromptConfiguration(project),
) : JPanel(BorderLayout()), Disposable {
  private var content: AgentPromptPaletteContent? = null
  private var initializing: Boolean = false
  private var disposed: Boolean = false

  init {
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
  }

  val preferredFocusedComponent: JComponent
    get() = content?.promptArea ?: this

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
        invocationData = configuration.invocationData,
        contextResolverService = project.service<AgentPromptContextResolverService>(),
        uiStateService = project.service<AgentPromptUiSessionStateService>(),
        sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptPalettePopup::class.java.classLoader),
        launcherProvider = configuration.launcherProvider,
        closeHost = ::handleSubmitSucceeded,
        isHostActive = { isShowing },
        revalidateHost = {
          promptContent?.let(::syncInlineContentSize)
          revalidate()
          repaint()
        },
        hostMode = configuration.hostMode,
      )
      configureInlineContent(promptContent)
      removeAll()
      add(promptContent.rootPanel, BorderLayout.CENTER)
      promptContent.sessionController.initialize(initialLaunchProfileId = configuration.initialLaunchProfileId)
      syncInlineContentSize(promptContent)
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
    disposeContent(configuration.disposeReason)
  }

  private fun disposeContent(reason: String) {
    val promptContent = content ?: return
    content = null
    promptContent.dispose(reason)
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

  private fun syncInlineContentSize(promptContent: AgentPromptPaletteContent) {
    preferredSize = contentHostSize(promptContent.rootPanel.preferredSize)
    minimumSize = contentHostSize(promptContent.rootPanel.minimumSize)
    maximumSize = contentHostSize(promptContent.rootPanel.maximumSize)
  }

  private fun contentHostSize(contentSize: Dimension): Dimension {
    val borderInsets = insets
    return Dimension(
      contentSize.width + borderInsets.left + borderInsets.right,
      contentSize.height + borderInsets.top + borderInsets.bottom,
    )
  }

  private fun handleSubmitSucceeded() {
    if (!configuration.resetAfterSuccessfulSubmit) {
      return
    }
    val promptContent = content ?: return
    promptContent.promptArea.text = ""
    promptContent.sessionController.initialize(initialLaunchProfileId = configuration.initialLaunchProfileId)
  }

  private fun requestPromptFocus(promptContent: AgentPromptPaletteContent) {
    IdeFocusManager.getInstance(project).requestFocusInProject(promptContent.promptArea, project)
  }
}

@ApiStatus.Internal
@RequiresEdt
fun createAgentWorkbenchInlineNewThreadPromptComponent(
  project: Project,
  invocationData: AgentPromptInvocationData,
  launcherProvider: () -> AgentPromptLauncherBridge?,
  initialLaunchProfileId: String?,
): AgentWorkbenchInlinePromptEmptyStateComponent {
  return AgentWorkbenchInlinePromptEmptyStateComponent(
    project = project,
    configuration = AgentWorkbenchInlinePromptConfiguration(
      invocationData = invocationData,
      launcherProvider = launcherProvider,
      hostMode = AgentPromptPaletteHostMode.INLINE_NEW_THREAD,
      initialLaunchProfileId = initialLaunchProfileId,
      disposeReason = "Agent prompt inline new thread disposed",
      resetAfterSuccessfulSubmit = false,
    ),
  ).also { component ->
    component.ensureContentInitialized()
  }
}

@ApiStatus.Internal
@RequiresEdt
fun createAgentWorkbenchInlinePromptEditorHost(component: JComponent): JComponent {
  return AgentWorkbenchInlinePromptEditorHost(component)
}

private class AgentWorkbenchInlinePromptEditorHost(component: JComponent) : JPanel(BorderLayout()) {
  init {
    isOpaque = true
    background = editorBackground()
    add(EditorEmptyStateComponentHost(fillContent = false).apply {
      setComponents(listOf(component))
    }, BorderLayout.CENTER)
  }

  override fun updateUI() {
    super.updateUI()
    background = editorBackground()
  }
}

private fun editorBackground() = EditorColorsManager.getInstance().globalScheme.defaultBackground

internal data class AgentWorkbenchInlinePromptConfiguration(
  @JvmField val invocationData: AgentPromptInvocationData,
  @JvmField val launcherProvider: () -> AgentPromptLauncherBridge?,
  @JvmField val hostMode: AgentPromptPaletteHostMode,
  @JvmField val initialLaunchProfileId: String?,
  @JvmField val disposeReason: String,
  @JvmField val resetAfterSuccessfulSubmit: Boolean,
)

private fun emptyStateInlinePromptConfiguration(project: Project): AgentWorkbenchInlinePromptConfiguration {
  return AgentWorkbenchInlinePromptConfiguration(
    invocationData = AgentPromptInvocationData(
      project = project,
      actionId = INLINE_PROMPT_ACTION_ID,
      actionText = AgentPromptBundle.message("inline.empty.state.prompt.accessible.name"),
      actionPlace = INLINE_PROMPT_PLACE,
      invokedAtMs = System.currentTimeMillis(),
    ),
    launcherProvider = AgentPromptLaunchers::find,
    hostMode = AgentPromptPaletteHostMode.INLINE_EMPTY_STATE,
    initialLaunchProfileId = null,
    disposeReason = "Agent prompt empty state disposed",
    resetAfterSuccessfulSubmit = true,
  )
}

internal const val INLINE_PROMPT_COMPONENT_NAME: String = "AgentWorkbenchInlinePromptEmptyStateComponent"
private const val INLINE_PROMPT_ACTION_ID: String = "AgentWorkbenchPrompt.InlineEmptyState"
private const val INLINE_PROMPT_PLACE: String = "EditorEmptyState"

/**
 * Feature flag for the inline Agent prompt shown in the empty editor.
 * When disabled, the inline composer is not created and the
 * `AgentWorkbenchGlobalPromptEmptyTextProvider` fallback hint is used instead.
 */
internal const val INLINE_EMPTY_STATE_PROMPT_PROPERTY: String = "agent.workbench.inline.empty.state.prompt"

internal fun isInlineEmptyStatePromptEnabled(): Boolean =
  System.getProperty(INLINE_EMPTY_STATE_PROMPT_PROPERTY, "true").toBoolean()

private fun inlinePromptArcDiameter(): Int = JBUI.scale(JBUI.getInt("Island.arc", 20))
