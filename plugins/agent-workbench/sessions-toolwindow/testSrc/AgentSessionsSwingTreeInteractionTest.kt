// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.chat.AgentChatOpenTabsPresentationState
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSubAgent
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.state.InMemorySessionTreeUiState
import com.intellij.agent.workbench.sessions.toolwindow.actions.createAgentSessionsTreePopupActionContext
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.buildSessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.isSelectableSessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.pathForMoreThreadsNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.sessionTreeNodeSearchText
import com.intellij.agent.workbench.sessions.toolwindow.tree.shouldExpandOnDoubleClick
import com.intellij.agent.workbench.sessions.toolwindow.tree.shouldHandleSingleClick
import com.intellij.agent.workbench.sessions.toolwindow.tree.shouldOpenOnActivation
import com.intellij.agent.workbench.sessions.toolwindow.tree.shouldRetargetSelectionForContextMenu
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsTreeRowActionsOverlay
import com.intellij.agent.workbench.sessions.toolwindow.ui.filterSelectableSessionTreeSelectionPaths
import com.intellij.agent.workbench.sessions.toolwindow.ui.pathForSessionTreeContextMenuRow
import com.intellij.agent.workbench.sessions.toolwindow.ui.resolveArchiveActionContext
import com.intellij.agent.workbench.sessions.toolwindow.ui.sessionTreeHoverRow
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.treeStructure.Tree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Rectangle
import java.util.concurrent.TimeUnit
import javax.swing.JTree
import javax.swing.tree.TreePath

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionsSwingTreeInteractionTest {
  @Test
  fun singleClickActionIsReservedForMoreRows() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)

    assertThat(shouldHandleSingleClick(SessionTreeNode.MoreProjects(hiddenCount = 2))).isTrue()
    assertThat(shouldHandleSingleClick(SessionTreeNode.MoreThreads(project = project, hiddenCount = 4))).isTrue()
    assertThat(shouldHandleSingleClick(SessionTreeNode.Project(project))).isFalse()
    assertThat(shouldHandleSingleClick(SessionTreeNode.Warning("warning"))).isFalse()
  }

  @Test
  fun activationOpenPolicyIncludesProjectAndWorktreeRows() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = false)
    val worktree = AgentWorktree(
      path = "/work/project-a-feature",
      name = "project-a-feature",
      branch = "feature",
      isOpen = false,
    )
    val thread =
      AgentSessionThread(id = "thread-1", title = "Thread 1", updatedAt = 100, archived = false, provider = AgentSessionProvider.from("codex"))
    val pendingThread =
      AgentSessionThread(id = "new-1", title = "New Thread", updatedAt = 100, archived = false, provider = AgentSessionProvider.from("codex"))
    val subAgent = AgentSubAgent(id = "sub-1", name = "Sub Agent")

    assertThat(shouldOpenOnActivation(SessionTreeNode.Project(project))).isTrue()
    assertThat(shouldOpenOnActivation(SessionTreeNode.Worktree(project, worktree))).isTrue()
    assertThat(shouldOpenOnActivation(SessionTreeNode.Thread(project, thread))).isTrue()
    assertThat(shouldOpenOnActivation(SessionTreeNode.Thread(project, pendingThread))).isFalse()
    assertThat(shouldOpenOnActivation(SessionTreeNode.SubAgent(project, thread, subAgent))).isTrue()
    assertThat(shouldOpenOnActivation(SessionTreeNode.MoreProjects(hiddenCount = 1))).isFalse()
    assertThat(shouldOpenOnActivation(SessionTreeNode.MoreThreads(project, hiddenCount = 1))).isFalse()
    assertThat(shouldOpenOnActivation(SessionTreeNode.Warning("warning"))).isFalse()
  }

  @Test
  fun doubleClickExpandPolicyPrefersOpenForOpenableRows() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = false)
    val worktree = AgentWorktree(
      path = "/work/project-a-feature",
      name = "project-a-feature",
      branch = "feature",
      isOpen = false,
    )
    val thread =
      AgentSessionThread(id = "thread-1", title = "Thread 1", updatedAt = 100, archived = false, provider = AgentSessionProvider.from("codex"))
    val pendingThread =
      AgentSessionThread(id = "new-1", title = "New Thread", updatedAt = 100, archived = false, provider = AgentSessionProvider.from("codex"))
    val subAgent = AgentSubAgent(id = "sub-1", name = "Sub Agent")

    assertThat(shouldExpandOnDoubleClick(SessionTreeNode.Project(project))).isFalse()
    assertThat(shouldExpandOnDoubleClick(SessionTreeNode.Worktree(project, worktree))).isFalse()
    assertThat(shouldExpandOnDoubleClick(SessionTreeNode.Thread(project, thread))).isFalse()
    assertThat(shouldExpandOnDoubleClick(SessionTreeNode.SubAgent(project, thread, subAgent))).isFalse()
    assertThat(shouldExpandOnDoubleClick(SessionTreeNode.Thread(project, pendingThread))).isTrue()
    assertThat(shouldExpandOnDoubleClick(SessionTreeNode.MoreProjects(hiddenCount = 1))).isTrue()
    assertThat(shouldExpandOnDoubleClick(SessionTreeNode.MoreThreads(project, hiddenCount = 1))).isTrue()
    assertThat(shouldExpandOnDoubleClick(SessionTreeNode.Warning("warning"))).isTrue()
  }

  @Test
  fun rowNewThreadActionCanBeSuppressedForSingleProjectPresentation() {
    val overlay = AgentSessionsTreeRowActionsOverlay(
      project = ProjectManager.getInstance().defaultProject,
      tree = Tree(),
      nodeResolver = { null },
      isNewThreadActionAvailable = { false },
    )
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)

    assertThat(
      overlay.rowActionPresentation(row = 0, treeNode = SessionTreeNode.Project(project), selected = true)
    ).isNull()
  }

  @Test
  fun resolvesMoreThreadPathForProjectAndWorktreeRows() {
    assertThat(pathForMoreThreadsNode(SessionTreeId.MoreThreads(projectPath = "/work/project-a")))
      .isEqualTo("/work/project-a")
    assertThat(
      pathForMoreThreadsNode(
        SessionTreeId.WorktreeMoreThreads(
          projectPath = "/work/project-a",
          worktreePath = "/work/project-feature",
        )
      )
    ).isEqualTo("/work/project-feature")
    assertThat(pathForMoreThreadsNode(SessionTreeId.MoreProjects)).isNull()
  }

  @Test
  fun contextMenuSelectionRetargetPolicyMatchesIjTreeConventions() {
    assertThat(shouldRetargetSelectionForContextMenu(isClickedPathSelected = false)).isTrue()
    assertThat(shouldRetargetSelectionForContextMenu(isClickedPathSelected = true)).isFalse()
  }

  @Test
  fun contextMenuPathResolvesWholeVisibleRow() {
    val firstPath = treePath(SessionTreeId.Project("/work/project-a"))
    val secondPath = treePath(SessionTreeId.Project("/work/project-b"))
    val tree = RowMappedTree(
      paths = listOf(firstPath, secondPath),
      rowBounds = listOf(
        Rectangle(24, 10, 90, 20),
        Rectangle(24, 35, 90, 20),
      ),
    )

    assertThat(pathForSessionTreeContextMenuRow(tree, y = 10)).isEqualTo(firstPath)
    assertThat(pathForSessionTreeContextMenuRow(tree, y = 29)).isEqualTo(firstPath)
    assertThat(pathForSessionTreeContextMenuRow(tree, y = 35)).isEqualTo(secondPath)
    assertThat(pathForSessionTreeContextMenuRow(tree, y = -1)).isNull()
    assertThat(pathForSessionTreeContextMenuRow(tree, y = 30)).isNull()
    assertThat(pathForSessionTreeContextMenuRow(tree, y = 55)).isNull()
  }

  @Test
  fun flatPinnedSectionRowsAreStructuralAndNotSearchable() {
    val model = pinnedSectionModel(currentProjectScopeActive = true)
    val provider = AgentSessionProvider.from("codex")
    val pinnedThreadId = SessionTreeId.Thread(PROJECT_PATH, provider, PINNED_THREAD_ID)
    val recentThreadId = SessionTreeId.Thread(PROJECT_PATH, provider, RECENT_THREAD_ID)

    assertThat(model.rootIds).containsExactly(SessionTreeId.Pinned, pinnedThreadId, SessionTreeId.PinnedSeparator, recentThreadId)
    assertThat(isSelectableSessionTreeId(model, SessionTreeId.Pinned)).isFalse()
    assertThat(isSelectableSessionTreeId(model, SessionTreeId.PinnedSeparator)).isFalse()
    assertThat(isSelectableSessionTreeId(model, pinnedThreadId)).isTrue()
    assertThat(sessionTreeNodeSearchText(model, SessionTreeId.Pinned)).isEmpty()
    assertThat(sessionTreeNodeSearchText(model, SessionTreeId.PinnedSeparator)).isEmpty()
  }

  @Test
  fun flatPinnedSectionRowsDoNotGetHoverBackground() {
    val model = pinnedSectionModel(currentProjectScopeActive = true)
    val provider = AgentSessionProvider.from("codex")
    val pinnedThreadId = SessionTreeId.Thread(PROJECT_PATH, provider, PINNED_THREAD_ID)
    val recentThreadId = SessionTreeId.Thread(PROJECT_PATH, provider, RECENT_THREAD_ID)
    val tree = RowMappedTree(
      paths = model.rootIds.map(::treePath),
      rowBounds = model.rootIds.indices.map { row -> Rectangle(0, row * 20, 160, 20) },
    )
    val isHoverableTreeId = { id: SessionTreeId -> isSelectableSessionTreeId(model, id) }

    assertThat(sessionTreeHoverRow(tree, x = 8, y = 10, isHoverableTreeId = isHoverableTreeId)).isEqualTo(-1)
    assertThat(sessionTreeHoverRow(tree, x = 8, y = 30, isHoverableTreeId = isHoverableTreeId)).isEqualTo(1)
    assertThat(sessionTreeHoverRow(tree, x = 8, y = 50, isHoverableTreeId = isHoverableTreeId)).isEqualTo(-1)
    assertThat(sessionTreeHoverRow(tree, x = 8, y = 70, isHoverableTreeId = isHoverableTreeId)).isEqualTo(3)
    assertThat(model.rootIds).containsExactly(SessionTreeId.Pinned, pinnedThreadId, SessionTreeId.PinnedSeparator, recentThreadId)
  }

  @Test
  fun globalPinnedSectionParentRemainsSelectableAndSearchable() {
    val model = pinnedSectionModel(currentProjectScopeActive = false)

    assertThat(model.rootIds.first()).isEqualTo(SessionTreeId.Pinned)
    assertThat(model.entriesById.getValue(SessionTreeId.Pinned).childIds).isNotEmpty()
    assertThat(isSelectableSessionTreeId(model, SessionTreeId.Pinned)).isTrue()
    assertThat(sessionTreeNodeSearchText(model, SessionTreeId.Pinned))
      .isEqualTo(AgentSessionsBundle.message("toolwindow.section.pinned"))
  }

  @Test
  fun globalPinnedSectionParentKeepsHoverBackground() {
    val model = pinnedSectionModel(currentProjectScopeActive = false)
    val tree = RowMappedTree(
      paths = model.rootIds.map(::treePath),
      rowBounds = model.rootIds.indices.map { row -> Rectangle(0, row * 20, 160, 20) },
    )
    val isHoverableTreeId = { id: SessionTreeId -> isSelectableSessionTreeId(model, id) }

    assertThat(sessionTreeHoverRow(tree, x = 8, y = 10, isHoverableTreeId = isHoverableTreeId)).isEqualTo(0)
  }

  @Test
  fun structuralSelectionSkipsPinnedSectionRowsInNavigationDirection() {
    val model = pinnedSectionModel(currentProjectScopeActive = true)
    val provider = AgentSessionProvider.from("codex")
    val pinnedHeaderPath = treePath(SessionTreeId.Pinned)
    val pinnedThreadPath = treePath(SessionTreeId.Thread(PROJECT_PATH, provider, PINNED_THREAD_ID))
    val separatorPath = treePath(SessionTreeId.PinnedSeparator)
    val recentThreadPath = treePath(SessionTreeId.Thread(PROJECT_PATH, provider, RECENT_THREAD_ID))
    val tree = RowMappedTree(listOf(pinnedHeaderPath, pinnedThreadPath, separatorPath, recentThreadPath))

    assertThat(
      filterSelectableSessionTreeSelectionPaths(
        tree = tree,
        model = model,
        selectionPaths = arrayOf(separatorPath),
        oldLeadSelectionPath = pinnedThreadPath,
        newLeadSelectionPath = separatorPath,
      )
    ).containsExactly(recentThreadPath)
    assertThat(
      filterSelectableSessionTreeSelectionPaths(
        tree = tree,
        model = model,
        selectionPaths = arrayOf(separatorPath),
        oldLeadSelectionPath = recentThreadPath,
        newLeadSelectionPath = separatorPath,
      )
    ).containsExactly(pinnedThreadPath)
    assertThat(
      filterSelectableSessionTreeSelectionPaths(
        tree = tree,
        model = model,
        selectionPaths = arrayOf(pinnedHeaderPath),
        oldLeadSelectionPath = pinnedThreadPath,
        newLeadSelectionPath = pinnedHeaderPath,
      )
    ).containsExactly(pinnedThreadPath)
  }

  @Test
  fun archiveActionContextPrefersPopupContextAndFallsBackToSelection() {
    val project = ProjectManager.getInstance().defaultProject
    val projectSessions = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val popupThread =
      AgentSessionThread(id = "popup-1", title = "Popup", updatedAt = 100, archived = false, provider = AgentSessionProvider.from("codex"))
    val selectedThread =
      AgentSessionThread(id = "selected-1", title = "Selected", updatedAt = 100, archived = false, provider = AgentSessionProvider.from("claude"))
    val popupTarget = ArchiveThreadTarget.Thread(path = "/work/project-a", provider = popupThread.provider, threadId = popupThread.id)
    val selectedTarget =
      ArchiveThreadTarget.Thread(path = "/work/project-b", provider = selectedThread.provider, threadId = selectedThread.id)
    val popupContext = checkNotNull(createAgentSessionsTreePopupActionContext(
      project = project,
      nodeId = SessionTreeId.Thread("/work/project-a", AgentSessionProvider.from("codex"), "popup-1"),
      node = SessionTreeNode.Thread(projectSessions, popupThread),
      archiveTargets = listOf(popupTarget),
    ))

    val contextFromPopup = resolveArchiveActionContext(
      popupActionContext = popupContext,
      project = project,
      selectedTreeId = SessionTreeId.Thread("/work/project-b", AgentSessionProvider.from("claude"), "selected-1"),
      selectedTreeNode = SessionTreeNode.Thread(projectSessions, selectedThread),
      selectedArchiveTargets = listOf(selectedTarget),
    )
    assertThat(contextFromPopup).isEqualTo(popupContext)

    val contextFromSelection = resolveArchiveActionContext(
      popupActionContext = null,
      project = project,
      selectedTreeId = SessionTreeId.Thread("/work/project-b", AgentSessionProvider.from("claude"), "selected-1"),
      selectedTreeNode = SessionTreeNode.Thread(projectSessions, selectedThread),
      selectedArchiveTargets = listOf(selectedTarget),
    )
    assertThat(contextFromSelection).isEqualTo(
      createAgentSessionsTreePopupActionContext(
        project = project,
        nodeId = SessionTreeId.Thread("/work/project-b", AgentSessionProvider.from("claude"), "selected-1"),
        node = SessionTreeNode.Thread(projectSessions, selectedThread),
        archiveTargets = listOf(selectedTarget),
      )
    )

    val missingSelectionContext = resolveArchiveActionContext(
      popupActionContext = null,
      project = project,
      selectedTreeId = null,
      selectedTreeNode = null,
      selectedArchiveTargets = listOf(selectedTarget),
    )
    assertThat(missingSelectionContext).isNull()
  }
}

private const val PROJECT_PATH = "/work/project-a"
private const val RECENT_THREAD_ID = "recent"
private const val PINNED_THREAD_ID = "pinned"

private fun pinnedSectionModel(currentProjectScopeActive: Boolean) = buildSessionTreeModel(
  projects = listOf(
    AgentProjectSessions(
      path = PROJECT_PATH,
      name = "Project A",
      isOpen = true,
      providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
      threads = listOf(
        AgentSessionThread(
          id = RECENT_THREAD_ID,
          title = "Recent thread",
          updatedAt = 300,
          archived = false,
          provider = AgentSessionProvider.from("codex"),
        ),
        AgentSessionThread(
          id = PINNED_THREAD_ID,
          title = "Pinned thread",
          updatedAt = 100,
          archived = false,
          provider = AgentSessionProvider.from("codex"),
        ),
      ),
    )
  ),
  visibleClosedProjectCount = Int.MAX_VALUE,
  visibleThreadCounts = emptyMap(),
  treeUiState = InMemorySessionTreeUiState(),
  currentProjectScopeActive = currentProjectScopeActive,
  openTabsPresentationState = AgentChatOpenTabsPresentationState(
    pinnedTopLevelThreadIdsByProvider = mapOf(AgentSessionProvider.from("codex") to mapOf(PROJECT_PATH to setOf(PINNED_THREAD_ID))),
  ),
)

private fun treePath(id: SessionTreeId): TreePath = TreePath(arrayOf(TestDescriptor("root"), TestDescriptor(id)))

private class RowMappedTree(
  private val paths: List<TreePath>,
  private val rowBounds: List<Rectangle> = List(paths.size) { row -> Rectangle(0, row * 20, 100, 20) },
) : JTree() {
  override fun getRowCount(): Int = paths.size

  override fun getPathForRow(row: Int): TreePath? = paths.getOrNull(row)

  override fun getRowForPath(path: TreePath?): Int = paths.indexOf(path)

  override fun getRowBounds(row: Int): Rectangle? = rowBounds.getOrNull(row)?.let(::Rectangle)

  override fun getPathBounds(path: TreePath?): Rectangle? {
    return getRowBounds(getRowForPath(path))
  }

  override fun getClosestPathForLocation(x: Int, y: Int): TreePath? {
    return pathForLocation(x, y)
  }

  override fun getPathForLocation(x: Int, y: Int): TreePath? {
    return pathForLocation(x, y)
  }

  private fun pathForLocation(x: Int, y: Int): TreePath? {
    val row = rowBounds.indexOfFirst { bounds ->
      x >= bounds.x && x < bounds.x + bounds.width && y >= bounds.y && y < bounds.y + bounds.height
    }
    return paths.getOrNull(row)
  }
}

private class TestDescriptor(
  private val elementValue: Any?,
) : NodeDescriptor<Any?>(null, null) {
  override fun update(): Boolean = false

  override fun getElement(): Any? = elementValue
}
