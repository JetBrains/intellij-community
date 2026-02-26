// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderIcon
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsTreePopupActionsTest {
  @Test
  fun openActionVisibilityAndDispatchForProjectAndWorktreeThread() {
    var openedProjectPath: String? = null
    var openedThreadPath: String? = null
    var openedSubAgentPath: String? = null
    val openAction = AgentSessionsTreePopupOpenAction(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      openProject = { path -> openedProjectPath = path },
      openThread = { path, _, _ -> openedThreadPath = path },
      openSubAgent = { path, _, _, _ -> openedSubAgentPath = path },
    )

    val closedProject = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = false)
    val projectContext = popupContext(
      nodeId = SessionTreeId.Project("/work/project-a"),
      node = SessionTreeNode.Project(closedProject),
    )
    val projectEvent = popupEvent(openAction, projectContext)
    openAction.update(projectEvent)
    assertThat(projectEvent.presentation.isEnabledAndVisible).isTrue()
    openAction.actionPerformed(projectEvent)
    assertThat(openedProjectPath).isEqualTo("/work/project-a")

    val openProjectContext = popupContext(
      nodeId = SessionTreeId.Project("/work/project-open"),
      node = SessionTreeNode.Project(AgentProjectSessions(path = "/work/project-open", name = "Open", isOpen = true)),
    )
    val openProjectEvent = popupEvent(openAction, openProjectContext)
    openAction.update(openProjectEvent)
    assertThat(openProjectEvent.presentation.isEnabledAndVisible).isFalse()

    val threadProject = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val thread = thread(id = "thread-1", provider = AgentSessionProvider.CODEX)
    val threadContext = popupContext(
      nodeId = SessionTreeId.WorktreeThread(
        projectPath = "/work/project-a",
        worktreePath = "/work/project-feature",
        provider = AgentSessionProvider.CODEX,
        threadId = "thread-1",
      ),
      node = SessionTreeNode.Thread(project = threadProject, thread = thread),
    )
    val threadEvent = popupEvent(openAction, threadContext)
    openAction.update(threadEvent)
    assertThat(threadEvent.presentation.isEnabledAndVisible).isTrue()
    openAction.actionPerformed(threadEvent)
    assertThat(openedThreadPath).isEqualTo("/work/project-feature")
    assertThat(openedSubAgentPath).isNull()
  }

  @Test
  fun moreActionUsesNodeSpecificCommandAndLabel() {
    var showMoreProjectsCalls = 0
    var showMoreThreadsPath: String? = null
    val moreAction = AgentSessionsTreePopupMoreAction(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      showMoreProjects = { showMoreProjectsCalls++ },
      showMoreThreads = { path -> showMoreThreadsPath = path },
    )

    val moreProjectsContext = popupContext(
      nodeId = SessionTreeId.MoreProjects,
      node = SessionTreeNode.MoreProjects(hiddenCount = 2),
    )
    val projectsEvent = popupEvent(moreAction, moreProjectsContext)
    moreAction.update(projectsEvent)
    assertThat(projectsEvent.presentation.isEnabledAndVisible).isTrue()
    assertThat(projectsEvent.presentation.text).isEqualTo("More (2)")
    moreAction.actionPerformed(projectsEvent)
    assertThat(showMoreProjectsCalls).isEqualTo(1)

    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val moreThreadsContext = popupContext(
      nodeId = SessionTreeId.WorktreeMoreThreads(
        projectPath = "/work/project-a",
        worktreePath = "/work/project-feature",
      ),
      node = SessionTreeNode.MoreThreads(project = project, hiddenCount = 4),
    )
    val threadsEvent = popupEvent(moreAction, moreThreadsContext)
    moreAction.update(threadsEvent)
    assertThat(threadsEvent.presentation.isEnabledAndVisible).isTrue()
    assertThat(threadsEvent.presentation.text).isEqualTo("More (4)")
    moreAction.actionPerformed(threadsEvent)
    assertThat(showMoreThreadsPath).isEqualTo("/work/project-feature")
  }

  @Test
  fun archiveActionUsesCapabilityGateAndSelectedCountLabel() {
    var archivedTargets: List<ArchiveThreadTarget>? = null
    val archiveAction = AgentSessionsArchiveThreadAction(
      resolveTreeContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      resolveEditorContext = { null },
      canArchiveProvider = { provider -> provider == AgentSessionProvider.CODEX },
      archiveThreads = { targets -> archivedTargets = targets },
    )

    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val codexTarget = ArchiveThreadTarget(
      path = "/work/project-a",
      provider = AgentSessionProvider.CODEX,
      threadId = "codex-1",
    )
    val claudeTarget = ArchiveThreadTarget(
      path = "/work/project-a",
      provider = AgentSessionProvider.CLAUDE,
      threadId = "claude-1",
    )
    val archiveContext = popupContext(
      nodeId = SessionTreeId.Thread(
        projectPath = "/work/project-a",
        provider = AgentSessionProvider.CODEX,
        threadId = "codex-1",
      ),
      node = SessionTreeNode.Thread(project = project, thread = thread(id = "codex-1", provider = AgentSessionProvider.CODEX)),
      archiveTargets = listOf(codexTarget, claudeTarget),
    )
    val archiveEvent = popupEvent(archiveAction, archiveContext)
    archiveAction.update(archiveEvent)
    assertThat(archiveEvent.presentation.isEnabledAndVisible).isTrue()
    assertThat(archiveEvent.presentation.text).isEqualTo("Archive Selected (2)")

    archiveAction.actionPerformed(archiveEvent)
    assertThat(archivedTargets).containsExactly(codexTarget, claudeTarget)

    val unsupportedContext = popupContext(
      nodeId = SessionTreeId.Thread(
        projectPath = "/work/project-a",
        provider = AgentSessionProvider.CLAUDE,
        threadId = "claude-1",
      ),
      node = SessionTreeNode.Thread(project = project, thread = thread(id = "claude-1", provider = AgentSessionProvider.CLAUDE)),
      archiveTargets = listOf(claudeTarget),
    )
    val unsupportedEvent = popupEvent(archiveAction, unsupportedContext)
    archiveAction.update(unsupportedEvent)
    assertThat(unsupportedEvent.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun archiveActionPrefersTreeContextWhenTreeAndEditorContextsAreBothAvailable() {
    var archivedTargets: List<ArchiveThreadTarget>? = null
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val treeTarget = ArchiveThreadTarget(
      path = "/work/project-a",
      provider = AgentSessionProvider.CODEX,
      threadId = "tree-1",
    )
    val treeContext = popupContext(
      nodeId = SessionTreeId.Thread(
        projectPath = "/work/project-a",
        provider = AgentSessionProvider.CODEX,
        threadId = "tree-1",
      ),
      node = SessionTreeNode.Thread(project = project, thread = thread(id = "tree-1", provider = AgentSessionProvider.CODEX)),
      archiveTargets = listOf(treeTarget),
    )
    val editorContext = AgentChatEditorTabActionContext(
      project = ProjectManager.getInstance().defaultProject,
      path = "/work/project-from-editor",
      threadIdentity = "codex:editor-1",
      threadId = "editor-1",
      provider = AgentSessionProvider.CODEX,
      sessionId = "editor-1",
    )
    val archiveAction = AgentSessionsArchiveThreadAction(
      resolveTreeContext = { treeContext },
      resolveEditorContext = { editorContext },
      canArchiveProvider = { true },
      archiveThreads = { targets -> archivedTargets = targets },
    )

    archiveAction.actionPerformed(TestActionEvent.createTestEvent(archiveAction))

    assertThat(archivedTargets).containsExactly(treeTarget)
  }

  @Test
  fun newThreadGroupVisibilityChildrenAndDispatch() {
    var launchedPath: String? = null
    var launchedProvider: AgentSessionProvider? = null
    var launchedMode: AgentSessionLaunchMode? = null
    var launchedProject: Project? = null
    val codexBridge = PopupTestProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val claudeBridge = PopupTestProviderBridge(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = false,
    )
    val group = AgentSessionsTreePopupNewThreadGroup(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      allBridges = { listOf(codexBridge, claudeBridge) },
      createNewSession = { path, provider, mode, project ->
        launchedPath = path
        launchedProvider = provider
        launchedMode = mode
        launchedProject = project
      },
    )

    val threadNode = SessionTreeNode.Thread(
      project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true),
      thread = thread(id = "thread-1", provider = AgentSessionProvider.CODEX),
    )
    val hiddenContext = popupContext(
      nodeId = SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-1"),
      node = threadNode,
    )
    val hiddenEvent = popupEvent(group, hiddenContext)
    group.update(hiddenEvent)
    assertThat(hiddenEvent.presentation.isEnabledAndVisible).isFalse()

    val projectContext = popupContext(
      nodeId = SessionTreeId.Project("/work/project-a"),
      node = SessionTreeNode.Project(AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)),
    )
    val projectEvent = popupEvent(group, projectContext)
    group.update(projectEvent)
    assertThat(projectEvent.presentation.isEnabledAndVisible).isTrue()

    val children = group.getChildren(projectEvent)
    assertThat(children).hasSize(5)

    val claudeAction = children.first { action ->
      action.templatePresentation.text == AgentSessionsBundle.message("toolwindow.action.new.session.claude")
    }
    val claudeEvent = popupEvent(claudeAction, projectContext)
    claudeAction.update(claudeEvent)
    assertThat(claudeEvent.presentation.isEnabled).isTrue()

    val yoloAction = children.first { action ->
      action.templatePresentation.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo")
    }
    yoloAction.actionPerformed(popupEvent(yoloAction, projectContext))

    assertThat(launchedPath).isEqualTo("/work/project-a")
    assertThat(launchedProvider).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(launchedMode).isEqualTo(AgentSessionLaunchMode.YOLO)
    assertThat(launchedProject).isEqualTo(projectContext.project)
  }
}

private fun popupContext(
  nodeId: SessionTreeId,
  node: SessionTreeNode,
  archiveTargets: List<ArchiveThreadTarget> = emptyList(),
): AgentSessionsTreePopupActionContext {
  return AgentSessionsTreePopupActionContext(
    project = ProjectManager.getInstance().defaultProject,
    nodeId = nodeId,
    node = node,
    archiveTargets = archiveTargets,
  )
}

private fun popupEvent(action: AnAction, context: AgentSessionsTreePopupActionContext): AnActionEvent {
  val dataContext = SimpleDataContext.builder()
    .add(CommonDataKeys.PROJECT, context.project)
    .add(AgentSessionsTreePopupDataKeys.CONTEXT, context)
    .build()
  return TestActionEvent.createTestEvent(action, dataContext)
}

private fun thread(id: String, provider: AgentSessionProvider): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = id,
    updatedAt = 100L,
    archived = false,
    provider = provider,
    subAgents = listOf(AgentSubAgent(id = "sub-$id", name = "Sub $id")),
  )
}

private class PopupTestProviderBridge(
  override val provider: AgentSessionProvider,
  private val supportedModes: Set<AgentSessionLaunchMode>,
  private val cliAvailable: Boolean,
  override val yoloSessionLabelKey: String? = null,
) : AgentSessionProviderBridge {
  override val displayNameKey: String
    get() = if (provider == AgentSessionProvider.CLAUDE) "toolwindow.provider.claude" else "toolwindow.provider.codex"

  override val newSessionLabelKey: String
    get() = if (provider == AgentSessionProvider.CLAUDE) "toolwindow.action.new.session.claude" else "toolwindow.action.new.session.codex"

  override val icon: AgentSessionProviderIcon
    get() = AgentSessionProviderIcon(path = "icons/codex@14x14.svg", iconClass = this::class.java)

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = supportedModes

  override val sessionSource: AgentSessionSource = object : AgentSessionSource {
    override val provider: AgentSessionProvider
      get() = this@PopupTestProviderBridge.provider

    override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> = emptyList()

    override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> = emptyList()
  }

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.cli"

  override fun isCliAvailable(): Boolean = cliAvailable

  override fun buildResumeCommand(sessionId: String): List<String> = listOf("test", "resume", sessionId)

  override fun buildNewSessionCommand(mode: AgentSessionLaunchMode): List<String> = listOf("test", "new", mode.name)

  override fun buildNewEntryCommand(): List<String> = listOf("test")

  override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
    return AgentSessionLaunchSpec(
      sessionId = null,
      command = listOf("test", "create", path, mode.name),
    )
  }
}
