// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.emptyState

import com.intellij.agent.workbench.prompt.core.AgentPromptContainerLauncher
import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchers
import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextSourceBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteExtension
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionAiBackend
import com.intellij.agent.workbench.prompt.ui.AGENT_PROMPT_PALETTE_PREFERRED_SIZE
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.agent.workbench.prompt.ui.AgentPromptTextField
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBTabbedPane
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
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
  fun providerReturnsNullWhenLauncherIsUnavailable() {
    val provider = AgentWorkbenchInlinePromptEmptyStateProvider()

    val component = AgentPromptLaunchers.withLauncherForTest(null) {
      runBlocking { provider.createComponent(fileEditorManagerFixture.get().mainSplitters) }
    }

    assertThat(component).isNull()
  }

  @Test
  fun providerCreatesShellWithoutPromptContent() {
    val component = createProviderComponentWithLauncher()
    try {
      assertThat(component.name).isEqualTo(INLINE_PROMPT_COMPONENT_NAME)
      assertThat(collectComponents(component, AgentPromptTextField::class.java)).isEmpty()
    }
    finally {
      Disposer.dispose(component)
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
        assertThat(component.preferredSize.height).isLessThan(AGENT_PROMPT_PALETTE_PREFERRED_SIZE.height)
        assertThat(tabbedPane.isVisible).isFalse()
        assertThat(promptArea.editor?.contentComponent?.accessibleContext?.accessibleName)
          .isEqualTo(AgentPromptBundle.message("inline.empty.state.prompt.accessible.name"))
        assertThat(promptArea.editor?.contentComponent?.accessibleContext?.accessibleDescription)
          .isEqualTo(AgentPromptBundle.message("inline.empty.state.prompt.accessible.description"))

        component.ensureContentInitialized()
        assertThat(collectComponents(component, AgentPromptTextField::class.java)).containsExactly(promptArea)
      }
      finally {
        Disposer.dispose(component)
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
        Disposer.dispose(component)
      }
    }
  }

  @Test
  fun disposeBeforeInitializationDoesNotCreatePromptContent() {
    runInEdtAndWait {
      val component = AgentWorkbenchInlinePromptEmptyStateComponent(ProjectManager.getInstance().defaultProject)

      Disposer.dispose(component)

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
    override fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
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
