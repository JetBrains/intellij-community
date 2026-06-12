// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.ScriptedSessionSource
import com.intellij.agent.workbench.sessions.openTestProjectEntry
import com.intellij.agent.workbench.sessions.state.InMemorySessionTreeUiState
import com.intellij.agent.workbench.sessions.thread
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.buildSessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.ui.SessionTreeCellRenderer
import com.intellij.agent.workbench.sessions.waitForCondition
import com.intellij.agent.workbench.sessions.withTestService
import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.IconManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import javax.swing.JTree

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionsCodexActivityRenderingIntegrationTest {
  @BeforeEach
  fun setUp() {
    IconLoader.activate()
    IconManager.activate(null)
  }

  @AfterEach
  fun tearDown() {
    IconManager.deactivate()
    IconLoader.deactivate()
  }

  @Test
  fun codexUnreadAssistantSnapshotRendersProcessingThreadBadge() = runBlocking(Dispatchers.Default) {
    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        processingThreadsForPath(path)
      },
      listFromClosedProject = { path ->
        processingThreadsForPath(path)
      },
    )

    withTestService(
      sessionSourcesProvider = { listOf(source) },
      projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.singleOrNull()
          ?.id == "thread-1"
      }

      service.refreshProviderForPath(PROJECT_PATH, AgentSessionProvider.CODEX)

      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.singleOrNull()
          ?.activity == AgentThreadActivity.PROCESSING
      }

      val state = service.state.value
      val project = state.projects.single { it.path == PROJECT_PATH }
      val thread = project.threads.single()
      assertThat(thread.activity).isEqualTo(AgentThreadActivity.PROCESSING)

      val model = buildSessionTreeModel(
        projects = state.projects,
        visibleClosedProjectCount = state.visibleClosedProjectCount,
        visibleThreadCounts = state.visibleThreadCounts,
        treeUiState = InMemorySessionTreeUiState(),
      )
      val threadId = SessionTreeId.Thread(project.path, AgentSessionProvider.CODEX, thread.id)
      val processingNode = model.entriesById.getValue(threadId).node as SessionTreeNode.Thread
      assertThat(processingNode.thread.activity).isEqualTo(AgentThreadActivity.PROCESSING)

      val tree = createTree()
      val processingRenderer = createRenderer { id -> model.entriesById[id]?.node }
      processingRenderer.getTreeCellRendererComponent(tree, descriptorValue(threadId), false, false, true, 0, false)
      val processingIcon = processingRenderer.icon
      assertThat(processingIcon).isNotNull()
      assertThat(processingRenderer.getCharSequence(true).toString())
        .doesNotContain(AgentSessionsBundle.message("toolwindow.thread.status.in.progress"))
        .doesNotContain("ACTIVE")

      val readyRenderer = createRenderer { id ->
        when (id) {
          threadId -> processingNode.copy(thread = processingNode.thread.copy(activityReport = AgentThreadActivityReport(AgentThreadActivity.READY)))
          else -> model.entriesById[id]?.node
        }
      }
      readyRenderer.getTreeCellRendererComponent(tree, descriptorValue(threadId), false, false, true, 0, false)
      val readyIcon = readyRenderer.icon
      assertThat(readyIcon).isNotNull()
      assertThat(processingIcon).isNotSameAs(readyIcon)
    }
  }

  @Test
  fun treeRowRendersActualActivityWhenSummaryActivityDoesNotContribute() {
    val thread = AgentSessionThread(
      id = "sub-agent-only",
      title = "Sub-agent only",
      updatedAt = 1_000L,
      archived = false,
      activity = AgentThreadActivity.UNREAD,
      provider = AgentSessionProvider.CODEX,
      summaryActivity = null,
    )
    val project = AgentProjectSessions(
      path = PROJECT_PATH,
      name = "Project A",
      isOpen = true,
      providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
      threads = listOf(thread),
    )
    val model = buildSessionTreeModel(
      projects = listOf(project),
      visibleClosedProjectCount = Int.MAX_VALUE,
      visibleThreadCounts = emptyMap(),
      treeUiState = InMemorySessionTreeUiState(),
    )
    val threadId = SessionTreeId.Thread(project.path, AgentSessionProvider.CODEX, thread.id)
    val unreadNode = model.entriesById.getValue(threadId).node as SessionTreeNode.Thread
    assertThat(unreadNode.thread.activity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(unreadNode.thread.summaryActivity).isNull()

    val tree = createTree()
    val unreadRenderer = createRenderer { id -> model.entriesById[id]?.node }
    unreadRenderer.getTreeCellRendererComponent(tree, descriptorValue(threadId), false, false, true, 0, false)
    val unreadIcon = unreadRenderer.icon
    assertThat(unreadIcon).isNotNull()

    val readyRenderer = createRenderer { id ->
      when (id) {
        threadId -> unreadNode.copy(thread = unreadNode.thread.copy(activityReport = AgentThreadActivityReport(AgentThreadActivity.READY)))
        else -> model.entriesById[id]?.node
      }
    }
    readyRenderer.getTreeCellRendererComponent(tree, descriptorValue(threadId), false, false, true, 0, false)
    assertThat(unreadIcon).isNotSameAs(readyRenderer.icon)
  }

  private fun createRenderer(nodeResolver: (SessionTreeId) -> SessionTreeNode?): SessionTreeCellRenderer {
    return SessionTreeCellRenderer(
      nowProvider = { 2_000L },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = nodeResolver,
      providerIconProvider = { AllIcons.Toolwindows.ToolWindowMessages },
    )
  }

  private fun processingThreadsForPath(path: String): List<AgentSessionThread> {
    if (path != PROJECT_PATH) {
      return emptyList()
    }
    return listOf(
      thread(
        id = "thread-1",
        title = "Thread 1",
        updatedAt = 1_000L,
        provider = AgentSessionProvider.CODEX,
        activity = AgentThreadActivity.PROCESSING,
        summaryActivity = null,
      )
    )
  }

  private fun createTree(): JTree {
    return JTree().apply {
      setSize(420, 320)
      doLayout()
    }
  }

  private fun descriptorValue(id: SessionTreeId): NodeDescriptor<Any?> = TestDescriptor(id)

  private class TestDescriptor(
    private val elementValue: Any?,
  ) : NodeDescriptor<Any?>(null, null) {
    override fun update(): Boolean = false

    override fun getElement(): Any? = elementValue
  }
}

private const val PROJECT_PATH = "/work/project-a"
