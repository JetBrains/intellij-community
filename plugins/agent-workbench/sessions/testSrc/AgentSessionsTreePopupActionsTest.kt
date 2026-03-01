// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.actions.AgentSessionsTreePopupActionContext
import com.intellij.agent.workbench.sessions.actions.AgentSessionsTreePopupArchiveThreadAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsTreePopupDataKeys
import com.intellij.agent.workbench.sessions.actions.AgentSessionsTreePopupMoreAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsTreePopupNewThreadGroup
import com.intellij.agent.workbench.sessions.actions.AgentSessionsTreePopupOpenAction
import com.intellij.agent.workbench.sessions.actions.resolveAgentSessionsTreePopupActionContext
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.tree.SessionTreeNode
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
    val archiveAction = AgentSessionsTreePopupArchiveThreadAction(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
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
  fun archiveActionArchivesTreeContextTargets() {
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
    val archiveAction = AgentSessionsTreePopupArchiveThreadAction(
      resolveContext = { treeContext },
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
    val codexBridge = TestAgentSessionProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val claudeBridge = TestAgentSessionProviderBridge(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = false,
    )
    val group = AgentSessionsTreePopupNewThreadGroup(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      allBridges = { listOf(codexBridge, claudeBridge) },
      lastUsedProvider = { AgentSessionProvider.CLAUDE },
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
    assertThat(projectEvent.presentation.isPerformGroup).isTrue()
    assertThat(projectEvent.presentation.isPopupGroup).isTrue()

    group.actionPerformed(projectEvent)
    assertThat(launchedPath).isEqualTo("/work/project-a")
    assertThat(launchedProvider).isEqualTo(AgentSessionProvider.CLAUDE)
    assertThat(launchedMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(launchedProject).isEqualTo(projectContext.project)

    launchedPath = null
    launchedProvider = null
    launchedMode = null
    launchedProject = null

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

  @Test
  fun newThreadGroupFallsBackToFirstStandardWhenLastUsedProviderIsNotEligibleForQuickStart() {
    var launchedProvider: AgentSessionProvider? = null
    var launchedMode: AgentSessionLaunchMode? = null
    val fallbackProvider = AgentSessionProvider.from("fallback")
    val codexYoloOnlyBridge = TestAgentSessionProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val fallbackBridge = TestAgentSessionProviderBridge(
      provider = fallbackProvider,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val group = AgentSessionsTreePopupNewThreadGroup(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      allBridges = { listOf(codexYoloOnlyBridge, fallbackBridge) },
      lastUsedProvider = { AgentSessionProvider.CODEX },
      createNewSession = { _, provider, mode, _ ->
        launchedProvider = provider
        launchedMode = mode
      },
    )
    val projectContext = popupContext(
      nodeId = SessionTreeId.Project("/work/project-a"),
      node = SessionTreeNode.Project(AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)),
    )
    val event = popupEvent(group, projectContext)

    group.update(event)
    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.isPerformGroup).isTrue()

    group.actionPerformed(event)

    assertThat(launchedProvider).isEqualTo(fallbackProvider)
    assertThat(launchedMode).isEqualTo(AgentSessionLaunchMode.STANDARD)

    val children = group.getChildren(event)
    assertThat(children).hasSize(4)

    val fallbackAction = children.first { action ->
      action.templatePresentation.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex")
    }
    fallbackAction.actionPerformed(popupEvent(fallbackAction, projectContext))

    assertThat(launchedProvider).isEqualTo(fallbackProvider)
    assertThat(launchedMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
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
