// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.emptyState

import com.intellij.agent.workbench.prompt.core.AgentPromptContainerLauncher
import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchers
import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextSourceBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteExtension
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionAiBackend
import com.intellij.agent.workbench.prompt.ui.AGENT_PROMPT_PALETTE_PREFERRED_SIZE
import com.intellij.agent.workbench.prompt.ui.AgentPromptUiDraft
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.agent.workbench.prompt.ui.AgentPromptTextField
import com.intellij.agent.workbench.prompt.ui.AgentPromptUiSessionStateService
import com.intellij.agent.workbench.prompt.ui.PromptTargetMode
import com.intellij.agent.workbench.prompt.ui.layoutRecursively
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorEmptyStateComponentHost
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBTabbedPane
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JLabel

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchInlinePromptEmptyStateProviderTest {
  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInit = { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()
  private val registeredExtensionPoints = mutableListOf<String>()

  @BeforeEach
  fun setUp() {
    ensurePromptExtensionPoints()
  }

  @AfterEach
  fun tearDown() {
    val extensionArea = ApplicationManager.getApplication().extensionArea
    for (extensionPointName in registeredExtensionPoints.asReversed()) {
      extensionArea.unregisterExtensionPoint(extensionPointName)
    }
    registeredExtensionPoints.clear()
  }

  @Test
  fun providerCreatesPromptContentWhenLauncherIsUnavailable() {
    val provider = AgentWorkbenchInlinePromptEmptyStateProvider()

    val component = AgentPromptLaunchers.withLauncherForTest(null) {
      runBlocking { provider.createComponent(fileEditorManagerFixture.get().mainSplitters) }
    }

    try {
      assertThat(component).isInstanceOf(AgentWorkbenchInlinePromptEmptyStateComponent::class.java)
      assertThat(collectComponents(component!!, AgentPromptTextField::class.java)).hasSize(1)
    }
    finally {
      disposeComponent(component as AgentWorkbenchInlinePromptEmptyStateComponent)
    }
  }

  @Test
  fun providerCreatesInitializedPromptContent() {
    val component = createProviderComponentWithLauncher()
    try {
      assertThat(component.name).isEqualTo(INLINE_PROMPT_COMPONENT_NAME)
      assertThat(collectComponents(component, AgentPromptTextField::class.java)).hasSize(1)
    }
    finally {
      disposeComponent(component)
    }
  }

  @Test
  fun disposingInitializedPromptDisposesPromptEditorAndClearsContent() {
    runInEdtAndWait {
      val component = AgentWorkbenchInlinePromptEmptyStateComponent(ProjectManager.getInstance().defaultProject)
      component.ensureContentInitialized()
      val promptArea = collectComponents(component, AgentPromptTextField::class.java).single()
      val editor = checkNotNull(promptArea.getEditor(true))

      disposeComponent(component)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

      assertThat(editor.isDisposed).isTrue()
      assertThat(collectComponents(component, AgentPromptTextField::class.java)).isEmpty()
      assertThat(component.preferredFocusedComponent).isSameAs(component)
    }
  }

  @Test
  fun disposingInlineNewThreadPromptDisposesPromptEditorAndClearsContent() {
    runInEdtAndWait {
      val project = ProjectManager.getInstance().defaultProject
      val component = createAgentWorkbenchInlineNewThreadPromptComponent(
        project = project,
        invocationData = testInvocationData(project),
        launcherProvider = { TestPromptLauncher },
        initialLaunchProfileId = null,
      )
      val promptArea = collectComponents(component, AgentPromptTextField::class.java).single()
      val editor = checkNotNull(promptArea.getEditor(true))

      disposeComponent(component)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

      assertThat(editor.isDisposed).isTrue()
      assertThat(collectComponents(component, AgentPromptTextField::class.java)).isEmpty()
      assertThat(component.preferredFocusedComponent).isSameAs(component)
    }
  }

  @Test
  fun providerReturnsNullWhenFeatureDisabled() {
    val previous = System.getProperty(INLINE_EMPTY_STATE_PROMPT_PROPERTY)
    System.setProperty(INLINE_EMPTY_STATE_PROMPT_PROPERTY, "false")
    try {
      val provider = AgentWorkbenchInlinePromptEmptyStateProvider()
      val component = AgentPromptLaunchers.withLauncherForTest(TestPromptLauncher) {
        runBlocking { provider.createComponent(fileEditorManagerFixture.get().mainSplitters) }
      }
      assertThat(component).isNull()
    }
    finally {
      if (previous == null) System.clearProperty(INLINE_EMPTY_STATE_PROMPT_PROPERTY)
      else System.setProperty(INLINE_EMPTY_STATE_PROMPT_PROPERTY, previous)
    }
  }

  @Test
  fun inlinePromptUsesCompactStructureAndAccessiblePromptText() {
    runInEdtAndWait {
      val component = AgentWorkbenchInlinePromptEmptyStateComponent(ProjectManager.getInstance().defaultProject)
      try {
        assertThat(collectComponents(component, AgentPromptTextField::class.java)).isEmpty()

        component.ensureContentInitialized()
        component.addNotify()

        val promptArea = collectComponents(component, AgentPromptTextField::class.java).single()
        val tabbedPane = collectComponents(component, JBTabbedPane::class.java).single()

        assertThat(component.name).isEqualTo(INLINE_PROMPT_COMPONENT_NAME)
        assertThat(component.isOpaque).isFalse()
        assertThat(component.preferredSize.height).isLessThan(AGENT_PROMPT_PALETTE_PREFERRED_SIZE.height)
        assertThat(component.border).isInstanceOf(RoundedLineBorder::class.java)
        assertThat(totalBorderInsets(component)).isGreaterThan(0)
        assertThat(tabbedPane.isVisible).isFalse()
        assertThat(promptArea.editor?.contentComponent?.accessibleContext?.accessibleName)
          .isEqualTo(AgentPromptBundle.message("inline.empty.state.prompt.accessible.name"))
        assertThat(promptArea.editor?.contentComponent?.accessibleContext?.accessibleDescription)
          .isEqualTo(AgentPromptBundle.message("inline.empty.state.prompt.accessible.description"))

        component.ensureContentInitialized()
        assertThat(collectComponents(component, AgentPromptTextField::class.java)).containsExactly(promptArea)
      }
      finally {
        disposeComponent(component)
      }
    }
  }

  @Test
  fun inlinePromptEmptyStateExpandsOuterSizeWhenInitialContextIsLoaded() {
    val contextItems = createManyContextItems()
    val contextContributorDisposable = Disposer.newDisposable()
    try {
      ExtensionTestUtil.maskExtensions(
        ExtensionPointName.create(PROMPT_CONTEXT_CONTRIBUTOR_EP),
        listOf(testContextContributor(contextItems)),
        contextContributorDisposable,
      )

      runInEdtAndWait {
        val component = AgentWorkbenchInlinePromptEmptyStateComponent(ProjectManager.getInstance().defaultProject)
        try {
          val compactPreferredHeight = component.preferredSize.height

          component.ensureContentInitialized()
          component.size = component.preferredSize
          layoutRecursively(component)

          val rootPanel = component.components.single() as JComponent
          val promptArea = collectComponents(component, AgentPromptTextField::class.java).single()
          assertThat(contentAreaSize(component.preferredSize, component)).isEqualTo(rootPanel.preferredSize)
          assertThat(contentAreaSize(component.minimumSize, component)).isEqualTo(rootPanel.minimumSize)
          assertThat(contentAreaSize(component.maximumSize, component)).isEqualTo(rootPanel.maximumSize)
          assertThat(component.preferredSize.height).isGreaterThan(compactPreferredHeight)
          assertThat(promptArea.height).isGreaterThan(0)
        }
        finally {
          disposeComponent(component)
        }
      }
    }
    finally {
      Disposer.dispose(contextContributorDisposable)
    }
  }

  @Test
  fun inlinePromptEditorHostUsesRichEmptyStateLayout() {
    runInEdtAndWait {
      val component = AgentWorkbenchInlinePromptEmptyStateComponent(ProjectManager.getInstance().defaultProject)
      try {
        val root = createAgentWorkbenchInlinePromptEditorHost(component)
        val preferredSize = component.preferredSize
        val hostWidth = preferredSize.width + 400
        val hostHeight = preferredSize.height + 300

        root.setBounds(0, 0, hostWidth, hostHeight)
        root.doLayout()
        val host = collectComponents(root, EditorEmptyStateComponentHost::class.java).single()
        host.doLayout()
        val contentPanel = host.components.single() as JComponent
        contentPanel.doLayout()

        assertThat(root.isOpaque).isTrue()
        assertThat(root.background).isEqualTo(EditorColorsManager.getInstance().globalScheme.defaultBackground)
        assertThat(host).isInstanceOf(EditorEmptyStateComponentHost::class.java)
        assertThat(component.size).isEqualTo(preferredSize)
        assertThat(kotlin.math.abs(contentPanel.x * 2 + contentPanel.width - host.width)).isLessThanOrEqualTo(1)
        assertThat(kotlin.math.abs(contentPanel.y * 2 + contentPanel.height - host.height)).isLessThanOrEqualTo(1)
      }
      finally {
        disposeComponent(component)
      }
    }
  }

  @Test
  fun clickingShellLabelInitializesPromptContent() {
    runInEdtAndWait {
      val component = AgentWorkbenchInlinePromptEmptyStateComponent(ProjectManager.getInstance().defaultProject)
      try {
        val label = collectComponents(component, JLabel::class.java).single()

        label.mouseListeners.forEach { listener ->
          listener.mouseClicked(MouseEvent(label, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false))
        }

        assertThat(collectComponents(component, AgentPromptTextField::class.java)).hasSize(1)
      }
      finally {
        disposeComponent(component)
      }
    }
  }

  @Test
  fun providerCreatesInitializedPromptBeforeSwingHierarchy() {
    val component = createProviderComponentWithLauncher()
    try {
      assertThat(collectComponents(component, AgentPromptTextField::class.java)).hasSize(1)
    }
    finally {
      disposeComponent(component)
    }
  }

  @Test
  fun inlinePromptIgnoresExistingTaskDraftMode() {
    val project = projectFixture.get()
    project.service<AgentPromptUiSessionStateService>().saveDraft(
      AgentPromptUiDraft(
        promptText = "new task prompt",
        targetMode = PromptTargetMode.EXISTING_TASK,
        existingTaskSearch = "old search",
        selectedExistingTaskId = "old-task",
        taskDrafts = mapOf(
          PromptTargetMode.NEW_TASK.name to "new task prompt",
          PromptTargetMode.EXISTING_TASK.name to "existing task prompt",
        ),
      )
    )

    runInEdtAndWait {
      val component = AgentWorkbenchInlinePromptEmptyStateComponent(project)
      try {
        component.ensureContentInitialized()

        val promptArea = collectComponents(component, AgentPromptTextField::class.java).single()

        assertThat(promptArea.text).isEqualTo("new task prompt")
      }
      finally {
        disposeComponent(component)
      }
    }
  }

  @Test
  fun disposeBeforeInitializationDoesNotCreatePromptContent() {
    runInEdtAndWait {
      val component = AgentWorkbenchInlinePromptEmptyStateComponent(ProjectManager.getInstance().defaultProject)

      disposeComponent(component)

      assertThat(collectComponents(component, AgentPromptTextField::class.java)).isEmpty()
    }
  }

  private fun createProviderComponentWithLauncher(): AgentWorkbenchInlinePromptEmptyStateComponent {
    val provider = AgentWorkbenchInlinePromptEmptyStateProvider()
    val component = AgentPromptLaunchers.withLauncherForTest(TestPromptLauncher) {
      runBlocking { provider.createComponent(fileEditorManagerFixture.get().mainSplitters) }
    }
    return component as AgentWorkbenchInlinePromptEmptyStateComponent
  }

  private fun testInvocationData(project: com.intellij.openapi.project.Project): AgentPromptInvocationData {
    return AgentPromptInvocationData(
      project = project,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent",
      actionPlace = "EditorEmptyState",
      invokedAtMs = 0L,
    )
  }

  private fun disposeComponent(component: AgentWorkbenchInlinePromptEmptyStateComponent) {
    if (ApplicationManager.getApplication().isDispatchThread) {
      Disposer.dispose(component)
    }
    else {
      runInEdtAndWait {
        Disposer.dispose(component)
      }
    }
  }

  private fun <T : Component> collectComponents(component: Component, componentClass: Class<T>): List<T> {
    return buildList {
      if (componentClass.isInstance(component)) {
        add(componentClass.cast(component))
      }
      if (component is Container) {
        component.components.forEach { child -> addAll(collectComponents(child, componentClass)) }
      }
    }
  }

  private fun totalBorderInsets(component: JComponent): Int {
    val insets = component.border.getBorderInsets(component)
    return insets.top + insets.left + insets.bottom + insets.right
  }

  private fun contentAreaSize(size: Dimension, component: JComponent): Dimension {
    val insets = component.insets
    return Dimension(
      size.width - insets.left - insets.right,
      size.height - insets.top - insets.bottom,
    )
  }

  private fun createManyContextItems(): List<AgentPromptContextItem> {
    return (1..12).map { index ->
      AgentPromptContextItem(
        rendererId = "test",
        title = "File $index",
        body = "community/plugins/agent-workbench/prompt/ui/src/context/VeryLongContextFileName$index.kt",
        source = "test",
      )
    }
  }

  private fun testContextContributor(items: List<AgentPromptContextItem>): AgentPromptContextContributorBridge {
    return object : AgentPromptContextContributorBridge {
      override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
        return items
      }
    }
  }

  private fun ensurePromptExtensionPoints() {
    registerExtensionPointIfNeeded(PROMPT_LAUNCHER_EP, AgentPromptLauncherBridge::class.java)
    registerExtensionPointIfNeeded(PROMPT_CONTEXT_CONTRIBUTOR_EP, AgentPromptContextContributorBridge::class.java)
    registerExtensionPointIfNeeded(PROMPT_CONTEXT_RENDERER_EP, AgentPromptContextRendererBridge::class.java)
    registerExtensionPointIfNeeded(PALETTE_EXTENSION_EP, AgentPromptPaletteExtension::class.java)
    registerExtensionPointIfNeeded(MANUAL_CONTEXT_SOURCE_EP, AgentPromptManualContextSourceBridge::class.java)
    registerExtensionPointIfNeeded(PROMPT_SUGGESTION_AI_BACKEND_EP, AgentPromptSuggestionAiBackend::class.java)
    registerExtensionPointIfNeeded(CONTAINER_LAUNCHER_EP, AgentPromptContainerLauncher::class.java)
  }

  private fun registerExtensionPointIfNeeded(extensionPointName: String, interfaceClass: Class<*>) {
    val extensionArea = ApplicationManager.getApplication().extensionArea
    if (extensionArea.hasExtensionPoint(extensionPointName)) {
      return
    }

    extensionArea.registerExtensionPoint(
      extensionPointName,
      interfaceClass.name,
      ExtensionPoint.Kind.INTERFACE,
      true,
    )
    registeredExtensionPoints.add(extensionPointName)
  }

  private object TestPromptLauncher : AgentPromptLauncherBridge {
    override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
      return AgentPromptLaunchResult.SUCCESS
    }
  }

  private companion object {
    const val PROMPT_LAUNCHER_EP: String = "com.intellij.agent.workbench.promptLauncher"
    const val PROMPT_CONTEXT_CONTRIBUTOR_EP: String = "com.intellij.agent.workbench.promptContextContributor"
    const val PROMPT_CONTEXT_RENDERER_EP: String = "com.intellij.agent.workbench.promptContextRenderer"
    const val MANUAL_CONTEXT_SOURCE_EP: String = "com.intellij.agent.workbench.promptManualContextSource"
    const val PALETTE_EXTENSION_EP: String = "com.intellij.agent.workbench.promptPaletteExtension"
    const val PROMPT_SUGGESTION_AI_BACKEND_EP: String = "com.intellij.agent.workbench.promptSuggestionAiBackend"
    const val CONTAINER_LAUNCHER_EP: String = "com.intellij.agent.workbench.containerLauncher"
  }
}
