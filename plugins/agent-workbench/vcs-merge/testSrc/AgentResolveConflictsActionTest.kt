// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.vcs.merge

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vcs.merge.MergeData
import com.intellij.openapi.vcs.merge.MergeDialogContext
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeProvider
import com.intellij.openapi.vcs.merge.MergeResolveActionContext
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.BasicOptionButtonUI
import com.intellij.ui.components.JBOptionButton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager
import javax.swing.plaf.metal.MetalIconFactory

@TestApplication
internal class AgentResolveConflictsActionTest {
  @BeforeEach
  fun setUpOptionButtonUi() {
    UIManager.getDefaults()["OptionButtonUI"] = BasicOptionButtonUI::class.java.name
  }

  @Test
  fun launchAgentMergeResolutionClosesDialogBeforeStartingSession() {
    val events = mutableListOf<String>()
    var startedRequest: AgentVcsMergeLaunchRequest? = null

    launchAgentMergeResolution(
      project = ProjectManager.getInstance().defaultProject,
      request = createLaunchRequest(),
      closeDialog = { events += "close" },
      item = createMenuItem(provider = AgentSessionProvider.CLAUDE, mode = AgentSessionLaunchMode.YOLO),
    ) { _, request ->
      events += "start"
      startedRequest = request
    }

    assertThat(events).containsExactly("close", "start")
    assertThat(startedRequest?.agentProvider).isEqualTo(AgentSessionProvider.CLAUDE)
    assertThat(startedRequest?.launchMode).isEqualTo(AgentSessionLaunchMode.YOLO)
  }

  @Test
  fun launchAgentMergeResolutionStartsSessionWithoutCloseCallback() {
    val events = mutableListOf<String>()

    launchAgentMergeResolution(
      project = ProjectManager.getInstance().defaultProject,
      request = createLaunchRequest(),
      closeDialog = null,
      item = createMenuItem(provider = AgentSessionProvider.CODEX, mode = AgentSessionLaunchMode.STANDARD),
    ) { _, _ ->
      events += "start"
    }

    assertThat(events).containsExactly("start")
  }

  @Test
  fun oneShotDialogUsesOptionButtonWhenSeveralProviderEntriesAreAvailable() {
    val action = AgentResolveConflictsAction(
      allProviders = {
        listOf(
          TestAgentSessionProviderDescriptor(AgentSessionProvider.CODEX, setOf(AgentSessionLaunchMode.STANDARD)),
          TestAgentSessionProviderDescriptor(AgentSessionProvider.CLAUDE, setOf(AgentSessionLaunchMode.STANDARD)),
        )
      },
      lastUsedProvider = { AgentSessionProvider.CLAUDE },
      lastUsedLaunchMode = { AgentSessionLaunchMode.STANDARD },
    )

    val component = createOneShotDialogComponent(action)

    assertThat(component).isInstanceOf(JBOptionButton::class.java)
    assertThat((component as JBOptionButton).text).isEqualTo("Resolve with Agent")
    assertThat(component.isSimpleButton).isFalse()
    assertThat(component.isEnabled).isTrue()
  }

  @Test
  fun oneShotDialogUsesOptionButtonWithoutRememberedProviderWhenSeveralEntriesAreAvailable() {
    val action = AgentResolveConflictsAction(
      allProviders = {
        listOf(
          TestAgentSessionProviderDescriptor(AgentSessionProvider.CODEX, setOf(AgentSessionLaunchMode.STANDARD)),
          TestAgentSessionProviderDescriptor(AgentSessionProvider.CLAUDE, setOf(AgentSessionLaunchMode.STANDARD)),
        )
      },
      lastUsedProvider = { null },
      lastUsedLaunchMode = { null },
    )

    val component = createOneShotDialogComponent(action)

    assertThat(component).isInstanceOf(JBOptionButton::class.java)
    assertThat((component as JBOptionButton).text).isEqualTo("Resolve with Agent")
    assertThat(component.isSimpleButton).isFalse()
  }

  @Test
  fun oneShotDialogUsesSimpleOptionButtonWhenOnlyOneProviderEntryIsAvailable() {
    val action = AgentResolveConflictsAction(
      allProviders = {
        listOf(TestAgentSessionProviderDescriptor(AgentSessionProvider.CODEX, setOf(AgentSessionLaunchMode.STANDARD)))
      },
      lastUsedProvider = { null },
      lastUsedLaunchMode = { null },
    )

    val component = createOneShotDialogComponent(action)

    assertThat(component).isInstanceOf(JBOptionButton::class.java)
    assertThat((component as JBOptionButton).text).isEqualTo("Resolve with Agent")
    assertThat(component.isSimpleButton).isTrue()
    assertThat(component.isEnabled).isTrue()
  }

  @Test
  fun resolveContextUsesDirectSelectionHintFiles() {
    val project = ProjectManager.getInstance().defaultProject
    val allFiles = listOf(
      LightVirtualFile("first.txt", "content"),
      LightVirtualFile("second.txt", "content"),
    )
    val selectedFiles = listOf(allFiles[1])
    val mergeContext = MergeResolveActionContext(
      project = project,
      selectionHintFilesProvider = { selectedFiles },
    )
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(MergeResolveActionContext.KEY, mergeContext)
      .build()

    val request = resolveRequest(AgentResolveConflictsAction(), dataContext)

    assertThat(request.selectionHintFiles).containsExactlyElementsOf(selectedFiles)
  }

  @Test
  fun resolveContextUsesDialogSelectionHintFiles() {
    val project = ProjectManager.getInstance().defaultProject
    val allFiles = listOf(
      LightVirtualFile("first.txt", "content"),
      LightVirtualFile("second.txt", "content"),
    )
    val selectedFiles = listOf(allFiles[1])
    val mergeContext = MergeDialogContext(
      project = project,
      mergeProvider = TestMergeProvider(),
      mergeDialogCustomizer = MergeDialogCustomizer(),
      getSelectionHintFiles = { selectedFiles },
      isModalDialogProvider = { false },
      closeDialogHandler = null,
    )
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(MergeDialogContext.KEY, mergeContext)
      .build()

    val request = resolveRequest(AgentResolveConflictsAction(), dataContext)

    assertThat(request.selectionHintFiles).containsExactlyElementsOf(selectedFiles)
  }

  private fun createLaunchRequest(): AgentVcsMergeLaunchRequest {
    return AgentVcsMergeLaunchRequest(
      selectionHintFiles = listOf(LightVirtualFile("conflicts.txt", "content")),
      agentProvider = AgentSessionProvider.CODEX,
      launchMode = AgentSessionLaunchMode.STANDARD,
    )
  }

  private fun createMenuItem(provider: AgentSessionProvider, mode: AgentSessionLaunchMode): AgentSessionProviderMenuItem {
    return AgentSessionProviderMenuItem(
      bridge = TestAgentSessionProviderDescriptor(provider),
      mode = mode,
      labelKey = "label.$provider.$mode",
      isEnabled = true,
    )
  }

  private fun createOneShotDialogComponent(action: AgentResolveConflictsAction): JComponent {
    val project = ProjectManager.getInstance().defaultProject
    val file = LightVirtualFile("conflicts.txt", "content")
    val mergeContext = MergeResolveActionContext(
      project = project,
      selectionHintFilesProvider = { listOf(file) },
    )
    action.templatePresentation.text = "Resolve with Agent"
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(MergeResolveActionContext.KEY, mergeContext)
      .build()
    val presentation = action.templatePresentation.clone()
    action.update(AnActionEvent.createEvent(dataContext, presentation, "Merge.OneShotDialog", ActionUiKind.NONE, null))

    return action.createCustomComponent(presentation, "Merge.OneShotDialog").also { component ->
      action.updateCustomComponent(component, presentation)
    }
  }

  private fun resolveRequest(action: AgentResolveConflictsAction, dataContext: DataContext): AgentVcsMergeLaunchRequest {
    val method = AgentResolveConflictsAction::class.java.getDeclaredMethod("resolveContext", DataContext::class.java)
    method.isAccessible = true
    val resolveWithAgentContext = checkNotNull(method.invoke(action, dataContext))
    val requestMethod = resolveWithAgentContext.javaClass.getDeclaredMethod("getRequest")
    requestMethod.isAccessible = true
    return requestMethod.invoke(resolveWithAgentContext) as AgentVcsMergeLaunchRequest
  }
}

private class TestMergeProvider : MergeProvider {
  override fun loadRevisions(file: com.intellij.openapi.vfs.VirtualFile): MergeData {
    error("Not needed for this test")
  }

  override fun conflictResolvedForFile(file: com.intellij.openapi.vfs.VirtualFile) {
  }

  override fun isBinary(file: com.intellij.openapi.vfs.VirtualFile): Boolean = false
}

private class TestAgentSessionProviderDescriptor(
  override val provider: AgentSessionProvider,
  private val launchModes: Set<AgentSessionLaunchMode> = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
) : AgentSessionProviderDescriptor {
  private val providerId: String = provider.value

  override val displayNameKey: String
    get() = "toolwindow.provider.$providerId"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.$providerId"

  override val yoloSessionLabelKey: String
    get() = "toolwindow.action.new.session.$providerId.yolo"

  override val icon: Icon
    get() = MetalIconFactory.getTreeLeafIcon()

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = launchModes

  override val sessionSource: AgentSessionSource = object : AgentSessionSource {
    override val provider: AgentSessionProvider
      get() = this@TestAgentSessionProviderDescriptor.provider

    override suspend fun listThreadsFromOpenProject(path: String, project: Project) =
      emptyList<com.intellij.agent.workbench.common.session.AgentSessionThread>()

    override suspend fun listThreadsFromClosedProject(path: String) =
      emptyList<com.intellij.agent.workbench.common.session.AgentSessionThread>()
  }

  override val cliMissingMessageKey: String
    get() = "cli.missing"

  override suspend fun isCliAvailable(): Boolean = true

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("test", "resume", sessionId))
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("test", "new", mode.name))
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    return AgentInitialMessagePlan.composeDefault(request)
  }

  override fun createToolWindowNorthComponent(project: Project): JComponent? = null
}
