// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.codex.common.CodexAppServerNotification
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadActivitySnapshot
import com.intellij.agent.workbench.codex.common.CodexThreadStatusKind
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.state.InMemorySessionTreeUiState
import com.intellij.agent.workbench.sessions.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.tree.buildSessionTreeModel
import com.intellij.agent.workbench.sessions.ui.SessionTreeCellRenderer
import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.IconManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import javax.swing.JTree

@TestApplication
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
    val source = createCodexSource(
      backendThreads = listOf(
        CodexBackendThread(
          thread = CodexThread(
            id = "thread-1",
            title = "Thread 1",
            updatedAt = 1_000L,
            archived = false,
            cwd = PROJECT_PATH,
            statusKind = CodexThreadStatusKind.IDLE,
          )
        )
      ),
      snapshotsByThreadId = mapOf(
        "thread-1" to CodexThreadActivitySnapshot(
          threadId = "thread-1",
          updatedAt = 1_000L,
          statusKind = CodexThreadStatusKind.ACTIVE,
          hasUnreadAssistantMessage = true,
        )
      ),
    )
    assertThat(
      source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        knownThreadIdsByPath = mapOf(PROJECT_PATH to setOf("thread-1")),
      ).getValue(PROJECT_PATH).activityByThreadId
    ).containsExactlyEntriesOf(mapOf("thread-1" to AgentThreadActivity.PROCESSING))

    withService(
      sessionSourcesProvider = { listOf(source) },
      projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
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
        .doesNotContain(CodexThreadStatusKind.ACTIVE.name)

      val readyRenderer = createRenderer { id ->
        when (id) {
          threadId -> processingNode.copy(thread = processingNode.thread.copy(activity = AgentThreadActivity.READY))
          else -> model.entriesById[id]?.node
        }
      }
      readyRenderer.getTreeCellRendererComponent(tree, descriptorValue(threadId), false, false, true, 0, false)
      val readyIcon = readyRenderer.icon
      assertThat(readyIcon).isNotNull()
      assertThat(processingIcon).isNotSameAs(readyIcon)
    }
  }

  private fun createRenderer(nodeResolver: (SessionTreeId) -> SessionTreeNode?): SessionTreeCellRenderer {
    return SessionTreeCellRenderer(
      nowProvider = { 2_000L },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = nodeResolver,
      providerIconProvider = { AllIcons.Toolwindows.ToolWindowMessages },
    )
  }

  // The real Codex source/hint classes are internal to a sibling module, so tests bridge them reflectively.
  private fun createCodexSource(
    backendThreads: List<CodexBackendThread>,
    snapshotsByThreadId: Map<String, CodexThreadActivitySnapshot>,
  ): AgentSessionSource {
    val refreshHintsProviderClass = Class.forName("com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider")
    val sourceClass = Class.forName("com.intellij.agent.workbench.codex.sessions.CodexSessionSource")
    val constructor = sourceClass.getDeclaredConstructor(
      CodexSessionBackend::class.java,
      refreshHintsProviderClass,
      refreshHintsProviderClass,
    )
    constructor.isAccessible = true
    return constructor.newInstance(
      staticBackend(backendThreads),
      createAppServerRefreshHintsProvider(snapshotsByThreadId),
      createEmptyRefreshHintsProvider(refreshHintsProviderClass),
    ) as AgentSessionSource
  }

  private fun staticBackend(backendThreads: List<CodexBackendThread>): CodexSessionBackend {
    return object : CodexSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread> {
        return if (path == PROJECT_PATH) backendThreads else emptyList()
      }
    }
  }

  private fun createAppServerRefreshHintsProvider(snapshotsByThreadId: Map<String, CodexThreadActivitySnapshot>): Any {
    val providerClass = Class.forName("com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerRefreshHintsProvider")
    val constructor = providerClass.declaredConstructors.single { it.parameterCount == 2 }
    constructor.isAccessible = true
    val snapshotReader: suspend (String) -> CodexThreadActivitySnapshot? = { threadId -> snapshotsByThreadId[threadId] }
    return constructor.newInstance(snapshotReader, emptyFlow<CodexAppServerNotification>())
  }

  private fun createEmptyRefreshHintsProvider(refreshHintsProviderClass: Class<*>): Any {
    return Proxy.newProxyInstance(
      refreshHintsProviderClass.classLoader,
      arrayOf(refreshHintsProviderClass),
    ) { proxy, method, args ->
      when (method.name) {
        "getUpdates" -> emptyFlow<Unit>()
        "prefetchRefreshHints" -> emptyMap<String, Any>()
        "toString" -> "EmptyCodexRefreshHintsProvider"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> throw UnsupportedOperationException("Unexpected method: ${method.name}")
      }
    }
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
