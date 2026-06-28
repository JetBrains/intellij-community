// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSubAgent
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfileKind
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.TestAgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.builtInLaunchProfileId
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupActionContext
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupArchiveThreadAction
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupCopyThreadIdAction
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupCreateTaskFolderAction
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupDataKeys
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupMarkTaskFolderDoneAction
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupMoreAction
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupMoveToTaskFolderGroup
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupNewThreadGroup
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupOpenAction
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupRemoveFromTaskFolderAction
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupRenameThreadAction
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupToggleThreadPinAction
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupUnarchiveThreadAction
import com.intellij.agent.workbench.sessions.toolwindow.actions.createAgentSessionsTreePopupActionContext
import com.intellij.agent.workbench.sessions.toolwindow.actions.resolveAgentSessionsTreePopupActionContext
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.toolwindow.ui.archiveTargetsForTaskFolderAssignments
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveRequestResult
import com.intellij.platform.ai.agent.sessions.core.SessionActionTarget
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolder
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolderThreadAssignment
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionsTreePopupActionsTest {
  @BeforeEach
  fun clearProviderAvailabilityCache() {
    ProjectManager.getInstance().defaultProject.service<AgentSessionProviderAvailabilityService>().clearAvailabilityForTest()
  }

  @Test
  fun openActionVisibilityAndDispatchForProjectAndWorktreeThread() {
    var dedicatedFrame = false
    var openedProjectPath: String? = null
    var openedThreadPath: String? = null
    var openedSubAgentPath: String? = null
    var projectEntryPoint: AgentWorkbenchEntryPoint? = null
    var threadEntryPoint: AgentWorkbenchEntryPoint? = null
    var subAgentEntryPoint: AgentWorkbenchEntryPoint? = null
    val openAction = AgentSessionsTreePopupOpenAction(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      isDedicatedProject = { dedicatedFrame },
      openProject = { path, entryPoint ->
        openedProjectPath = path
        projectEntryPoint = entryPoint
      },
      openThread = { path, _, _, entryPoint ->
        openedThreadPath = path
        threadEntryPoint = entryPoint
      },
      openSubAgent = { path, _, _, _, entryPoint ->
        openedSubAgentPath = path
        subAgentEntryPoint = entryPoint
      },
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
    assertThat(projectEntryPoint).isEqualTo(AgentWorkbenchEntryPoint.TREE_POPUP)

    val openProjectContext = popupContext(
      nodeId = SessionTreeId.Project("/work/project-open"),
      node = SessionTreeNode.Project(AgentProjectSessions(path = "/work/project-open", name = "Open", isOpen = true)),
    )
    val openProjectEvent = popupEvent(openAction, openProjectContext)
    openAction.update(openProjectEvent)
    assertThat(openProjectEvent.presentation.isEnabledAndVisible).isFalse()

    dedicatedFrame = true
    openAction.update(openProjectEvent)
    assertThat(openProjectEvent.presentation.isEnabledAndVisible).isTrue()
    openedProjectPath = null
    openAction.actionPerformed(openProjectEvent)
    assertThat(openedProjectPath).isEqualTo("/work/project-open")
    assertThat(projectEntryPoint).isEqualTo(AgentWorkbenchEntryPoint.TREE_POPUP)

    val threadProject = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val thread = thread(id = "thread-1", provider = AgentSessionProvider.from("codex"))
    val threadContext = popupContext(
      nodeId = SessionTreeId.WorktreeThread(
        projectPath = "/work/project-a",
        worktreePath = "/work/project-feature",
        provider = AgentSessionProvider.from("codex"),
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
    assertThat(threadEntryPoint).isEqualTo(AgentWorkbenchEntryPoint.TREE_POPUP)
    assertThat(subAgentEntryPoint).isNull()
  }

  @Test
  fun togglePinActionPinsUnpinnedThreadByOpeningTab() {
    val provider = AgentSessionProvider.from("codex")
    val thread = thread(id = "thread-pin", provider = provider)
    val projectSessions = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val context = popupContext(
      nodeId = SessionTreeId.Thread(projectPath = "/work/project-a", provider = provider, threadId = thread.id),
      node = SessionTreeNode.Thread(project = projectSessions, thread = thread),
    )
    val openedFile = LightVirtualFile("thread-pin.chat")
    var openedTarget: SessionActionTarget.Thread? = null
    var pinnedProject: Project? = null
    var pinnedFile: VirtualFile? = null
    var pinnedValue: Boolean? = null
    val action = AgentSessionsTreePopupToggleThreadPinAction(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      isThreadPinned = { false },
      openThread = { _, target, openedChatHandler ->
        openedTarget = target
        runBlocking {
          openedChatHandler(context.project, openedFile)
        }
      },
      setOpenTabsPinned = { _, _ -> error("Unpin handler must not be called") },
      setOpenedFilePinned = { project, file, pinned ->
        pinnedProject = project
        pinnedFile = file
        pinnedValue = pinned
      },
    )
    val event = popupEvent(action, context)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.text)
      .isEqualTo(AgentSessionsBundle.message("action.AgentWorkbenchSessions.TreePopup.TogglePin.text"))

    action.actionPerformed(event)

    assertThat(openedTarget?.threadId).isEqualTo(thread.id)
    assertThat(pinnedProject).isSameAs(context.project)
    assertThat(pinnedFile).isSameAs(openedFile)
    assertThat(pinnedValue).isTrue()
  }

  @Test
  fun togglePinActionUnpinsPinnedThreadTabs() {
    val provider = AgentSessionProvider.from("codex")
    val thread = thread(id = "thread-unpin", provider = provider)
    val projectSessions = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val context = popupContext(
      nodeId = SessionTreeId.Thread(projectPath = "/work/project-a", provider = provider, threadId = thread.id),
      node = SessionTreeNode.Thread(project = projectSessions, thread = thread),
    )
    val unpinned = CompletableDeferred<Pair<String, Boolean>>()
    val action = AgentSessionsTreePopupToggleThreadPinAction(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      isThreadPinned = { true },
      openThread = { _, _, _ -> error("Open handler must not be called") },
      setOpenTabsPinned = { target, pinned ->
        unpinned.complete(target.threadId to pinned)
        1
      },
      setOpenedFilePinned = { _, _, _ -> error("Pin handler must not be called") },
    )
    val event = popupEvent(action, context)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.text)
      .isEqualTo(AgentSessionsBundle.message("action.AgentWorkbenchSessions.TreePopup.TogglePin.unpin.text"))

    val result = runBlocking {
      withContext(Dispatchers.EDT) {
        ActionUtil.performAction(action, event)
      }
      withTimeout(5.seconds) {
        unpinned.await()
      }
    }
    assertThat(result).isEqualTo(thread.id to false)
  }

  @Test
  fun togglePinActionIsHiddenForNonActiveTopLevelThreads() {
    val provider = AgentSessionProvider.from("codex")
    val projectSessions = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val action = AgentSessionsTreePopupToggleThreadPinAction(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      isThreadPinned = { false },
      openThread = { _, _, _ -> error("Open handler must not be called") },
      setOpenTabsPinned = { _, _ -> error("Unpin handler must not be called") },
      setOpenedFilePinned = { _, _, _ -> error("Pin handler must not be called") },
    )

    assertTogglePinHidden(action, popupContext(
      nodeId = SessionTreeId.Project("/work/project-a"),
      node = SessionTreeNode.Project(projectSessions),
    ))

    val thread = thread(id = "thread-hidden", provider = provider)
    assertTogglePinHidden(action, popupContext(
      nodeId = SessionTreeId.SubAgent(
        projectPath = "/work/project-a",
        provider = provider,
        threadId = thread.id,
        subAgentId = "sub-${thread.id}",
      ),
      node = SessionTreeNode.SubAgent(
        project = projectSessions,
        thread = thread,
        subAgent = thread.subAgents.single(),
      ),
    ))

    val archivedThread = thread(id = "thread-archived", provider = provider, archived = true)
    assertTogglePinHidden(action, popupContext(
      nodeId = SessionTreeId.Thread(projectPath = "/work/project-a", provider = provider, threadId = archivedThread.id),
      node = SessionTreeNode.Thread(project = projectSessions, thread = archivedThread),
    ))

    assertTogglePinHidden(action, AgentSessionsTreePopupActionContext(
      project = ProjectManager.getInstance().defaultProject,
      target = SessionActionTarget.Thread(
        path = "/work/project-a",
        provider = provider,
        threadId = "new-thread-hidden",
        title = "New Thread",
        thread = thread(id = "new-thread-hidden", provider = provider),
      ),
      archiveTargets = emptyList(),
    ))

    assertTogglePinHidden(action, popupContext(
      nodeId = SessionTreeId.MoreThreads("/work/project-a"),
      node = SessionTreeNode.MoreThreads(project = projectSessions, hiddenCount = 2),
    ))
  }

  @Test
  fun pendingThreadRowsDoNotCreatePopupActionContext() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val pendingThread = thread(id = "new-pending", provider = AgentSessionProvider.from("codex"))

    val projectThreadContext = createAgentSessionsTreePopupActionContext(
      project = ProjectManager.getInstance().defaultProject,
      nodeId = SessionTreeId.Thread(
        projectPath = "/work/project-a",
        provider = AgentSessionProvider.from("codex"),
        threadId = "new-pending",
      ),
      node = SessionTreeNode.Thread(project = project, thread = pendingThread),
      archiveTargets = emptyList(),
    )
    val worktreeThreadContext = createAgentSessionsTreePopupActionContext(
      project = ProjectManager.getInstance().defaultProject,
      nodeId = SessionTreeId.WorktreeThread(
        projectPath = "/work/project-a",
        worktreePath = "/work/project-a-feature",
        provider = AgentSessionProvider.from("codex"),
        threadId = "new-pending",
      ),
      node = SessionTreeNode.Thread(project = project, thread = pendingThread),
      archiveTargets = emptyList(),
    )

    assertThat(projectThreadContext).isNull()
    assertThat(worktreeThreadContext).isNull()
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
  fun copyThreadIdActionCopiesActiveAndArchivedThreadIds() {
    val copiedThreadIds = mutableListOf<String>()
    val action = AgentSessionsTreePopupCopyThreadIdAction(
      copyToClipboard = { threadId -> copiedThreadIds += threadId },
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
    )
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)

    val activeContext = popupContext(
      nodeId = SessionTreeId.Thread(
        projectPath = "/work/project-a",
        provider = AgentSessionProvider.from("codex"),
        threadId = "thread-1",
      ),
      node = SessionTreeNode.Thread(project = project, thread = thread(id = "thread-1", provider = AgentSessionProvider.from("codex"))),
    )
    val activeEvent = popupEvent(action, activeContext)
    action.update(activeEvent)
    assertThat(activeEvent.presentation.isEnabledAndVisible).isTrue()
    action.actionPerformed(activeEvent)

    val archivedContext = popupContext(
      nodeId = SessionTreeId.Thread(
        projectPath = "/work/project-a",
        provider = AgentSessionProvider.from("codex"),
        threadId = "archived-1",
      ),
      node = SessionTreeNode.Thread(
        project = project,
        thread = thread(id = "archived-1", provider = AgentSessionProvider.from("codex"), archived = true),
      ),
    )
    val archivedEvent = popupEvent(action, archivedContext)
    action.update(archivedEvent)
    assertThat(archivedEvent.presentation.isEnabledAndVisible).isTrue()
    action.actionPerformed(archivedEvent)

    val projectContext = popupContext(
      nodeId = SessionTreeId.Project("/work/project-a"),
      node = SessionTreeNode.Project(project),
    )
    val projectEvent = popupEvent(action, projectContext)
    action.update(projectEvent)
    assertThat(projectEvent.presentation.isEnabledAndVisible).isFalse()
    assertThat(copiedThreadIds).containsExactly("thread-1", "archived-1")
  }

  @Test
  fun archiveActionUsesCapabilityGateAndSelectedCountLabel() {
    var archivedTargets: List<ArchiveThreadTarget>? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val archiveAction = AgentSessionsTreePopupArchiveThreadAction(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      canArchiveProvider = { provider -> provider == AgentSessionProvider.from("codex") },
      archiveThreads = { targets, capturedEntryPoint ->
        archivedTargets = targets
        entryPoint = capturedEntryPoint
      },
    )

    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val codexTarget = ArchiveThreadTarget.Thread(
      path = "/work/project-a",
      provider = AgentSessionProvider.from("codex"),
      threadId = "codex-1",
    )
    val claudeTarget = ArchiveThreadTarget.Thread(
      path = "/work/project-a",
      provider = AgentSessionProvider.from("claude"),
      threadId = "claude-1",
    )
    val archiveContext = popupContext(
      nodeId = SessionTreeId.Thread(
        projectPath = "/work/project-a",
        provider = AgentSessionProvider.from("codex"),
        threadId = "codex-1",
      ),
      node = SessionTreeNode.Thread(project = project, thread = thread(id = "codex-1", provider = AgentSessionProvider.from("codex"))),
      archiveTargets = listOf(codexTarget, claudeTarget),
    )
    val archiveEvent = popupEvent(archiveAction, archiveContext)
    archiveAction.update(archiveEvent)
    assertThat(archiveEvent.presentation.isEnabledAndVisible).isTrue()
    assertThat(archiveEvent.presentation.text).isEqualTo("Archive Selected (2)")

    archiveAction.actionPerformed(archiveEvent)
    assertThat(archivedTargets).containsExactly(codexTarget, claudeTarget)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.TREE_POPUP)

    val unsupportedContext = popupContext(
      nodeId = SessionTreeId.Thread(
        projectPath = "/work/project-a",
        provider = AgentSessionProvider.from("claude"),
        threadId = "claude-1",
      ),
      node = SessionTreeNode.Thread(project = project, thread = thread(id = "claude-1", provider = AgentSessionProvider.from("claude"))),
      archiveTargets = listOf(claudeTarget),
    )
    val unsupportedEvent = popupEvent(archiveAction, unsupportedContext)
    archiveAction.update(unsupportedEvent)
    assertThat(unsupportedEvent.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun archiveActionArchivesTreeContextTargets() {
    var archivedTargets: List<ArchiveThreadTarget>? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val treeTarget = ArchiveThreadTarget.Thread(
      path = "/work/project-a",
      provider = AgentSessionProvider.from("codex"),
      threadId = "tree-1",
    )
    val treeContext = popupContext(
      nodeId = SessionTreeId.Thread(
        projectPath = "/work/project-a",
        provider = AgentSessionProvider.from("codex"),
        threadId = "tree-1",
      ),
      node = SessionTreeNode.Thread(project = project, thread = thread(id = "tree-1", provider = AgentSessionProvider.from("codex"))),
      archiveTargets = listOf(treeTarget),
    )
    val archiveAction = AgentSessionsTreePopupArchiveThreadAction(
      resolveContext = { treeContext },
      canArchiveProvider = { true },
      archiveThreads = { targets, capturedEntryPoint ->
        archivedTargets = targets
        entryPoint = capturedEntryPoint
      },
    )

    archiveAction.actionPerformed(TestActionEvent.createTestEvent(archiveAction))

    assertThat(archivedTargets).containsExactly(treeTarget)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.TREE_POPUP)
  }

  @Test
  fun createTaskFolderActionUsesProjectOrWorktreePath() {
    var createdPath: String? = null
    var createdName: String? = null
    val action = AgentSessionsTreePopupCreateTaskFolderAction(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      promptForName = { "Authentication rewrite" },
      createFolder = { path, name ->
        createdPath = path
        createdName = name
      },
    )

    val projectContext = popupContext(
      nodeId = SessionTreeId.Project("/work/project-a"),
      node = SessionTreeNode.Project(AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)),
    )
    val projectEvent = popupEvent(action, projectContext)
    action.update(projectEvent)
    assertThat(projectEvent.presentation.isEnabledAndVisible).isTrue()

    action.actionPerformed(projectEvent)

    assertThat(createdPath).isEqualTo("/work/project-a")
    assertThat(createdName).isEqualTo("Authentication rewrite")

    val threadContext = popupContext(
      nodeId = SessionTreeId.Thread("/work/project-a", AgentSessionProvider.from("codex"), "thread-1"),
      node = SessionTreeNode.Thread(
        project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true),
        thread = thread(id = "thread-1", provider = AgentSessionProvider.from("codex")),
      ),
    )
    val threadEvent = popupEvent(action, threadContext)
    action.update(threadEvent)
    assertThat(threadEvent.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun moveToTaskFolderGroupAssignsSelectedThreads() {
    val provider = AgentSessionProvider.from("codex")
    val folder = AgentTaskFolder(
      path = "/work/project-a",
      id = "folder-1",
      name = "Research",
      createdAt = 1,
      updatedAt = 1,
    )
    val firstTarget = SessionActionTarget.Thread(
      path = "/work/project-a",
      provider = provider,
      threadId = "thread-1",
      title = "Thread 1",
    )
    val secondTarget = firstTarget.copy(threadId = "thread-2", title = "Thread 2")
    val assigned = mutableListOf<Pair<String, String>>()
    val group = AgentSessionsTreePopupMoveToTaskFolderGroup(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      listFolders = { path -> if (path == "/work/project-a") listOf(folder) else emptyList() },
      assignThread = { target, targetFolder -> assigned += target.threadId to targetFolder.id },
    )
    val context = AgentSessionsTreePopupActionContext(
      project = ProjectManager.getInstance().defaultProject,
      target = firstTarget,
      archiveTargets = emptyList(),
      selectedThreadTargets = listOf(firstTarget, secondTarget),
    )
    val event = popupEvent(group, context)

    group.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    val child = group.getChildren(event).single()
    child.actionPerformed(popupEvent(child, context))

    assertThat(assigned).containsExactly("thread-1" to "folder-1", "thread-2" to "folder-1")
  }

  @Test
  fun removeFromTaskFolderActionUnassignsOnlyAssignedThreads() {
    val provider = AgentSessionProvider.from("codex")
    val assignedTarget = SessionActionTarget.Thread(
      path = "/work/project-a",
      provider = provider,
      threadId = "assigned",
      title = "Assigned",
    )
    val freeTarget = assignedTarget.copy(threadId = "free", title = "Free")
    val unassigned = mutableListOf<String>()
    val action = AgentSessionsTreePopupRemoveFromTaskFolderAction(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      isAssigned = { target -> target.threadId == "assigned" },
      unassignThread = { target -> unassigned += target.threadId },
    )
    val context = AgentSessionsTreePopupActionContext(
      project = ProjectManager.getInstance().defaultProject,
      target = assignedTarget,
      archiveTargets = emptyList(),
      selectedThreadTargets = listOf(assignedTarget, freeTarget),
    )
    val event = popupEvent(action, context)

    action.update(event)
    action.actionPerformed(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(unassigned).containsExactly("assigned")
  }

  @Test
  fun markTaskFolderDoneArchivesAssignedTargetsBeforeChangingStatus() {
    val provider = AgentSessionProvider.from("codex")
    val folderTarget = SessionActionTarget.TaskFolder(
      path = "/work/project-a",
      folderId = "folder-1",
      name = "Research",
      isDone = false,
    )
    val loadedArchiveTarget = ArchiveThreadTarget.Thread(path = "/work/project-a", provider = provider, threadId = "loaded-thread")
    val unloadedArchiveTarget = ArchiveThreadTarget.Thread(path = "/work/project-a", provider = provider, threadId = "unloaded-thread")
    var archivedTargets: List<ArchiveThreadTarget>? = null
    var doneTarget: SessionActionTarget.TaskFolder? = null
    val action = AgentSessionsTreePopupMarkTaskFolderDoneAction(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      canArchiveProvider = { true },
      archiveThreads = { targets, _, onComplete ->
        archivedTargets = targets
        onComplete(AgentSessionArchiveRequestResult(requestedCount = 2, archivedCount = 2))
      },
      setFolderDone = { target -> doneTarget = target },
    )
    val context = AgentSessionsTreePopupActionContext(
      project = ProjectManager.getInstance().defaultProject,
      target = folderTarget,
      archiveTargets = emptyList(),
      taskFolderArchiveTargets = listOf(loadedArchiveTarget, unloadedArchiveTarget),
    )
    val event = popupEvent(action, context)

    action.update(event)
    action.actionPerformed(event)

    assertThat(event.presentation.isEnabled).isTrue()
    assertThat(archivedTargets).containsExactly(loadedArchiveTarget, unloadedArchiveTarget)
    assertThat(doneTarget).isEqualTo(folderTarget)
  }

  @Test
  fun taskFolderArchiveTargetsIncludeAssignmentsAbsentFromLoadedRows() {
    val codex = AgentSessionProvider.from("codex")
    val claude = AgentSessionProvider.from("claude")
    val targets = archiveTargetsForTaskFolderAssignments(
      listOf(
        taskFolderAssignment(provider = codex, threadId = "loaded-thread", assignedAt = 1),
        taskFolderAssignment(provider = codex, threadId = "unloaded-thread", assignedAt = 2),
        taskFolderAssignment(provider = codex, threadId = "loaded-thread", assignedAt = 3),
        taskFolderAssignment(provider = claude, threadId = "unloaded-thread", assignedAt = 4),
      )
    )

    assertThat(targets).containsExactly(
      ArchiveThreadTarget.Thread(path = "/work/project-a", provider = codex, threadId = "loaded-thread"),
      ArchiveThreadTarget.Thread(path = "/work/project-a", provider = codex, threadId = "unloaded-thread"),
      ArchiveThreadTarget.Thread(path = "/work/project-a", provider = claude, threadId = "unloaded-thread"),
    )
  }

  @Test
  fun markTaskFolderDoneKeepsFolderInProgressWhenAssignedArchiveIsPartial() {
    val provider = AgentSessionProvider.from("codex")
    val folderTarget = SessionActionTarget.TaskFolder(
      path = "/work/project-a",
      folderId = "folder-1",
      name = "Research",
      isDone = false,
    )
    var doneTarget: SessionActionTarget.TaskFolder? = null
    val action = AgentSessionsTreePopupMarkTaskFolderDoneAction(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      canArchiveProvider = { true },
      archiveThreads = { _, _, onComplete ->
        onComplete(AgentSessionArchiveRequestResult(requestedCount = 2, archivedCount = 1))
      },
      setFolderDone = { target -> doneTarget = target },
    )
    val context = AgentSessionsTreePopupActionContext(
      project = ProjectManager.getInstance().defaultProject,
      target = folderTarget,
      archiveTargets = emptyList(),
      taskFolderArchiveTargets = listOf(
        ArchiveThreadTarget.Thread(path = "/work/project-a", provider = provider, threadId = "loaded-thread"),
        ArchiveThreadTarget.Thread(path = "/work/project-a", provider = provider, threadId = "unloaded-thread"),
      ),
    )

    action.actionPerformed(popupEvent(action, context))

    assertThat(doneTarget).isNull()
  }

  @Test
  fun markTaskFolderDoneIsDisabledWhenAssignedProviderCannotArchive() {
    val codex = AgentSessionProvider.from("codex")
    val claude = AgentSessionProvider.from("claude")
    val action = AgentSessionsTreePopupMarkTaskFolderDoneAction(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      canArchiveProvider = { provider -> provider == codex },
      archiveThreads = { _, _, _ -> },
      setFolderDone = {},
    )
    val context = AgentSessionsTreePopupActionContext(
      project = ProjectManager.getInstance().defaultProject,
      target = SessionActionTarget.TaskFolder(
        path = "/work/project-a",
        folderId = "folder-1",
        name = "Research",
        isDone = false,
      ),
      archiveTargets = emptyList(),
      taskFolderArchiveTargets = listOf(
        ArchiveThreadTarget.Thread(path = "/work/project-a", provider = codex, threadId = "codex-thread"),
        ArchiveThreadTarget.Thread(path = "/work/project-a", provider = claude, threadId = "claude-thread"),
      ),
    )
    val event = popupEvent(action, context)

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun unarchiveActionUsesCapabilityGateAndSelectedCountLabel() {
    var unarchivedTargets: List<ArchiveThreadTarget>? = null
    val unarchiveAction = AgentSessionsTreePopupUnarchiveThreadAction(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      canUnarchiveProvider = { provider -> provider == AgentSessionProvider.from("codex") },
      unarchiveThreads = { targets -> unarchivedTargets = targets },
    )

    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val codexTarget = ArchiveThreadTarget.Thread(
      path = "/work/project-a",
      provider = AgentSessionProvider.from("codex"),
      threadId = "codex-archived",
    )
    val claudeTarget = ArchiveThreadTarget.Thread(
      path = "/work/project-a",
      provider = AgentSessionProvider.from("claude"),
      threadId = "claude-archived",
    )
    val unarchiveContext = popupContext(
      nodeId = SessionTreeId.Thread(
        projectPath = "/work/project-a",
        provider = AgentSessionProvider.from("codex"),
        threadId = "codex-archived",
      ),
      node = SessionTreeNode.Thread(
        project = project,
        thread = thread(id = "codex-archived", provider = AgentSessionProvider.from("codex"), archived = true),
      ),
      unarchiveTargets = listOf(codexTarget, claudeTarget),
    )
    val unarchiveEvent = popupEvent(unarchiveAction, unarchiveContext)
    unarchiveAction.update(unarchiveEvent)
    assertThat(unarchiveEvent.presentation.isEnabledAndVisible).isTrue()
    assertThat(unarchiveEvent.presentation.text).isEqualTo("Unarchive Selected (2)")

    unarchiveAction.actionPerformed(unarchiveEvent)
    assertThat(unarchivedTargets).containsExactly(codexTarget, claudeTarget)

    val unsupportedContext = popupContext(
      nodeId = SessionTreeId.Thread(
        projectPath = "/work/project-a",
        provider = AgentSessionProvider.from("claude"),
        threadId = "claude-archived",
      ),
      node = SessionTreeNode.Thread(
        project = project,
        thread = thread(id = "claude-archived", provider = AgentSessionProvider.from("claude"), archived = true),
      ),
      unarchiveTargets = listOf(claudeTarget),
    )
    val unsupportedEvent = popupEvent(unarchiveAction, unsupportedContext)
    unarchiveAction.update(unsupportedEvent)
    assertThat(unsupportedEvent.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun renameActionVisibleOnlyForSupportedTopLevelThreadTargets() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val threadContext = popupContext(
      nodeId = SessionTreeId.Thread(
        projectPath = "/work/project-a",
        provider = AgentSessionProvider.from("codex"),
        threadId = "thread-1",
      ),
      node = SessionTreeNode.Thread(project = project, thread = thread(id = "thread-1", provider = AgentSessionProvider.from("codex"))),
    )

    val unsupported = AgentSessionsTreePopupRenameThreadAction(
      resolveContext = { threadContext },
      canRenameThread = { false },
      renameThread = { _, _ -> },
      promptForName = { _, _ -> null },
    )
    val unsupportedEvent = popupEvent(unsupported, threadContext)
    unsupported.update(unsupportedEvent)
    assertThat(unsupportedEvent.presentation.isVisible).isTrue()
    assertThat(unsupportedEvent.presentation.isEnabled).isFalse()

    val supported = AgentSessionsTreePopupRenameThreadAction(
      resolveContext = { threadContext },
      canRenameThread = { true },
      renameThread = { _, _ -> },
      promptForName = { _, _ -> null },
    )
    val supportedEvent = popupEvent(supported, threadContext)
    supported.update(supportedEvent)
    assertThat(supportedEvent.presentation.isVisible).isTrue()
    assertThat(supportedEvent.presentation.isEnabled).isTrue()

    val subAgentContext = popupContext(
      nodeId = SessionTreeId.SubAgent(
        projectPath = "/work/project-a",
        provider = AgentSessionProvider.from("codex"),
        threadId = "thread-1",
        subAgentId = "sub-thread-1",
      ),
      node = SessionTreeNode.SubAgent(
        project = project,
        thread = thread(id = "thread-1", provider = AgentSessionProvider.from("codex")),
        subAgent = AgentSubAgent(id = "sub-thread-1", name = "Sub thread 1"),
      ),
    )
    val hiddenAction = AgentSessionsTreePopupRenameThreadAction(
      resolveContext = { subAgentContext },
      canRenameThread = { true },
      renameThread = { _, _ -> },
      promptForName = { _, _ -> null },
    )
    val hiddenEvent = popupEvent(hiddenAction, subAgentContext)
    hiddenAction.update(hiddenEvent)
    assertThat(hiddenEvent.presentation.isVisible).isFalse()
    assertThat(hiddenEvent.presentation.isEnabled).isFalse()
  }

  @Test
  fun renameActionUsesTreeThreadTargetAndPromptValue() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val context = popupContext(
      nodeId = SessionTreeId.Thread(
        projectPath = "/work/project-a",
        provider = AgentSessionProvider.from("codex"),
        threadId = "thread-1",
      ),
      node = SessionTreeNode.Thread(project = project, thread = thread(id = "thread-1", provider = AgentSessionProvider.from("codex"))),
    )
    val target = context.target as SessionActionTarget.Thread
    var promptedProjectName: String? = null
    var promptedTitle: String? = null
    var renamedTarget: SessionActionTarget.Thread? = null
    var renamedTo: String? = null

    val action = AgentSessionsTreePopupRenameThreadAction(
      resolveContext = { context },
      canRenameThread = { true },
      renameThread = { capturedTarget, requestedName ->
        renamedTarget = capturedTarget
        renamedTo = requestedName
      },
      promptForName = { currentProject, currentTitle ->
        promptedProjectName = currentProject.name
        promptedTitle = currentTitle
        "Renamed thread"
      },
    )

    action.actionPerformed(popupEvent(action, context))

    assertThat(promptedProjectName).isEqualTo(context.project.name)
    assertThat(promptedTitle).isEqualTo(target.title)
    assertThat(renamedTarget).isEqualTo(target)
    assertThat(renamedTo).isEqualTo("Renamed thread")
  }

  @Test
  fun newThreadGroupHidesWhenContextDisallowsNewThread() {
    val group = AgentSessionsTreePopupNewThreadGroup(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
    )
    val projectContext = popupContext(
      nodeId = SessionTreeId.Project("/work/project-a"),
      node = SessionTreeNode.Project(AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)),
      newThreadActionAvailable = false,
    )
    val event = popupEvent(group, projectContext)

    group.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
    assertThat(group.getChildren(event)).isEmpty()
  }

  @Test
  fun newThreadGroupVisibilityAndDispatchUsesDefaultLaunchProfile() {
    var launchedPath: String? = null
    var launchedProfile: AgentPromptLaunchProfile? = null
    var launchedProject: Project? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val activeProfile = AgentPromptLaunchProfile(
      id = "user:careful-codex",
      name = "Careful Codex",
      providerId = AgentSessionProvider.from("codex").value,
      launchMode = AgentSessionLaunchMode.STANDARD,
      generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
    )
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val claudeBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("claude"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val group = AgentSessionsTreePopupNewThreadGroup(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      allBridges = { listOf(codexBridge, claudeBridge) },
      userLaunchProfiles = { listOf(activeProfile) },
      defaultLaunchProfileId = { activeProfile.id },
      createNewSession = { path, profile, project, capturedEntryPoint ->
        launchedPath = path
        launchedProfile = profile
        launchedProject = project
        entryPoint = capturedEntryPoint
      },
    )

    val threadNode = SessionTreeNode.Thread(
      project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true),
      thread = thread(id = "thread-1", provider = AgentSessionProvider.from("codex")),
    )
    val hiddenContext = popupContext(
      nodeId = SessionTreeId.Thread("/work/project-a", AgentSessionProvider.from("codex"), "thread-1"),
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
    assertThat(launchedProfile).isEqualTo(activeProfile)
    assertThat(launchedProject).isEqualTo(projectContext.project)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.TREE_POPUP)

    launchedPath = null
    launchedProfile = null
    launchedProject = null

    val children = group.getChildren(projectEvent)
    val visibleActions = children.filterNot { action -> action is Separator }
    assertThat(visibleActions).hasSize(5)
    assertThat(visibleActions.map { action -> action.templatePresentation.text }).contains(
      AgentSessionsBundle.message("toolwindow.action.new.session.codex"),
      AgentSessionsBundle.message("toolwindow.action.new.session.claude"),
      activeProfile.name,
      AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo"),
    )

    val activeProfileAction = children.first { action ->
      action.templatePresentation.text == activeProfile.name
    }
    val activeProfileEvent = popupEvent(activeProfileAction, projectContext)
    activeProfileAction.update(activeProfileEvent)
    assertThat(activeProfileEvent.presentation.isEnabled).isTrue()

    val yoloAction = children.first { action ->
      action.templatePresentation.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo")
    }
    yoloAction.actionPerformed(popupEvent(yoloAction, projectContext))

    assertThat(launchedPath).isEqualTo("/work/project-a")
    assertThat(launchedProfile?.providerId).isEqualTo(AgentSessionProvider.from("codex").value)
    assertThat(launchedProfile?.launchMode).isEqualTo(AgentSessionLaunchMode.YOLO)
    assertThat(launchedProfile?.kind).isEqualTo(AgentPromptLaunchProfileKind.BUILT_IN)
    assertThat(launchedProject).isEqualTo(projectContext.project)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.TREE_POPUP)
  }

  @Test
  fun newThreadGroupDoesNotPerformUnavailableDefaultLaunchProfile() {
    var launchedPath: String? = null
    var launchedProfile: AgentPromptLaunchProfile? = null
    var launchedProject: Project? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val unavailableProfile = AgentPromptLaunchProfile(
      id = "user:careful-claude",
      name = "Careful Claude",
      providerId = AgentSessionProvider.from("claude").value,
      launchMode = AgentSessionLaunchMode.STANDARD,
    )
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val claudeBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("claude"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = false,
    )
    val group = AgentSessionsTreePopupNewThreadGroup(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      allBridges = { listOf(codexBridge, claudeBridge) },
      userLaunchProfiles = { listOf(unavailableProfile) },
      defaultLaunchProfileId = { unavailableProfile.id },
      createNewSession = { path, profile, project, capturedEntryPoint ->
        launchedPath = path
        launchedProfile = profile
        launchedProject = project
        entryPoint = capturedEntryPoint
      },
    )
    val projectContext = popupContext(
      nodeId = SessionTreeId.Project("/work/project-a"),
      node = SessionTreeNode.Project(AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)),
    )
    val event = popupEvent(group, projectContext)
    projectContext.project.service<AgentSessionProviderAvailabilityService>().setAvailabilityForTest(
      mapOf(
        AgentSessionProvider.from("codex") to true,
        AgentSessionProvider.from("claude") to false,
      ),
    )

    group.update(event)
    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.isPerformGroup).isFalse()

    group.actionPerformed(event)
    assertThat(launchedPath).isNull()
    assertThat(launchedProfile).isNull()
    assertThat(launchedProject).isNull()
    assertThat(entryPoint).isNull()

    val children = group.getChildren(event)

    val claudeAction = children.first { action ->
      action.templatePresentation.text == unavailableProfile.name
    }
    val claudeEvent = popupEvent(claudeAction, projectContext)
    claudeAction.update(claudeEvent)
    assertThat(claudeEvent.presentation.isEnabled).isFalse()
    assertThat(claudeEvent.presentation.description).isEqualTo(AgentSessionsBundle.message("toolwindow.error.cli"))

    claudeAction.actionPerformed(claudeEvent)
    assertThat(launchedPath).isNull()
    assertThat(launchedProfile).isNull()
    assertThat(launchedProject).isNull()
  }

  @Test
  fun newThreadGroupDefaultsToFirstAvailableBuiltInLaunchProfile() {
    var launchedProfile: AgentPromptLaunchProfile? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val fallbackProvider = AgentSessionProvider.from("fallback")
    val codexYoloOnlyBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val fallbackBridge = TestAgentSessionProviderDescriptor(
      provider = fallbackProvider,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val group = AgentSessionsTreePopupNewThreadGroup(
      resolveContext = { event -> resolveAgentSessionsTreePopupActionContext(event) },
      allBridges = { listOf(codexYoloOnlyBridge, fallbackBridge) },
      defaultLaunchProfileId = { null },
      createNewSession = { _, profile, _, capturedEntryPoint ->
        launchedProfile = profile
        entryPoint = capturedEntryPoint
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

    assertThat(launchedProfile?.id).isEqualTo(builtInLaunchProfileId(fallbackProvider, AgentSessionLaunchMode.STANDARD))
    assertThat(launchedProfile?.providerId).isEqualTo(fallbackProvider.value)
    assertThat(launchedProfile?.launchMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.TREE_POPUP)

    val children = group.getChildren(event)
    val visibleActions = children.filterNot { action -> action is Separator }
    assertThat(visibleActions).hasSize(3)

    val fallbackAction = visibleActions.first { action ->
      action.templatePresentation.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex")
    }
    fallbackAction.actionPerformed(popupEvent(fallbackAction, projectContext))

    assertThat(launchedProfile?.id).isEqualTo(builtInLaunchProfileId(fallbackProvider, AgentSessionLaunchMode.STANDARD))
    assertThat(launchedProfile?.providerId).isEqualTo(fallbackProvider.value)
    assertThat(launchedProfile?.launchMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.TREE_POPUP)
  }

}

private fun assertTogglePinHidden(action: AgentSessionsTreePopupToggleThreadPinAction, context: AgentSessionsTreePopupActionContext) {
  val event = popupEvent(action, context)
  action.update(event)
  assertThat(event.presentation.isEnabledAndVisible).isFalse()
}

private fun popupContext(
  nodeId: SessionTreeId,
  node: SessionTreeNode,
  archiveTargets: List<ArchiveThreadTarget> = emptyList(),
  unarchiveTargets: List<ArchiveThreadTarget> = emptyList(),
  selectedThreadTargets: List<SessionActionTarget.Thread> = emptyList(),
  taskFolderArchiveTargets: List<ArchiveThreadTarget> = emptyList(),
  newThreadActionAvailable: Boolean = true,
): AgentSessionsTreePopupActionContext {
  return checkNotNull(createAgentSessionsTreePopupActionContext(
    project = ProjectManager.getInstance().defaultProject,
    nodeId = nodeId,
    node = node,
    archiveTargets = archiveTargets,
    unarchiveTargets = unarchiveTargets,
    selectedThreadTargets = selectedThreadTargets,
    taskFolderArchiveTargets = taskFolderArchiveTargets,
    newThreadActionAvailable = newThreadActionAvailable,
  ))
}

private fun popupEvent(action: AnAction, context: AgentSessionsTreePopupActionContext): AnActionEvent {
  val dataContext = SimpleDataContext.builder()
    .add(CommonDataKeys.PROJECT, context.project)
    .add(AgentSessionsTreePopupDataKeys.CONTEXT, context)
    .build()
  return TestActionEvent.createTestEvent(action, dataContext)
}

private fun taskFolderAssignment(
  provider: AgentSessionProvider,
  threadId: String,
  assignedAt: Long,
): AgentTaskFolderThreadAssignment {
  return AgentTaskFolderThreadAssignment(
    path = "/work/project-a",
    provider = provider,
    threadId = threadId,
    folderId = "folder-1",
    assignedAt = assignedAt,
  )
}

private fun thread(id: String, provider: AgentSessionProvider, archived: Boolean = false): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = id,
    updatedAt = 100L,
    archived = archived,
    provider = provider,
    subAgents = listOf(AgentSubAgent(id = "sub-$id", name = "Sub $id")),
  )
}
