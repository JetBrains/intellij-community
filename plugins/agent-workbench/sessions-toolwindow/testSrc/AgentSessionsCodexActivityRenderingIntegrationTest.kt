// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.codex.common.CodexAppServerNotification
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadActivitySnapshot
import com.intellij.agent.workbench.codex.common.CodexThreadStatusKind
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionCostKind
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.openTestProjectEntry
import com.intellij.agent.workbench.sessions.state.InMemorySessionTreeUiState
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.lang.reflect.Proxy
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
    assumeTrue(isCodexSessionsAvailable(), "Codex sessions module is not available")

    val source = createCodexSource(
      backendThreads = listOf(
        createCodexBackendThread(
          CodexThread(
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
        refreshThreadSeedsByPath = mapOf(
          PROJECT_PATH to setOf(AgentSessionRefreshThreadSeed(threadId = "thread-1", updatedAt = 1_000L))
        ),
      ).getValue(PROJECT_PATH).activityUpdatesByThreadId.mapValues { (_, update) -> update.activityReport.rowActivity }
    ).containsExactlyEntriesOf(mapOf("thread-1" to AgentThreadActivity.PROCESSING))

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
        .doesNotContain(CodexThreadStatusKind.ACTIVE.name)

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

  private fun createCodexSource(
    backendThreads: List<Any>,
    snapshotsByThreadId: Map<String, CodexThreadActivitySnapshot>,
  ): AgentSessionSource {
    val refreshHintsProviderClass = Class.forName("com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider")
    val backendClass = Class.forName("com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend")
    val sourceClass = Class.forName("com.intellij.agent.workbench.codex.sessions.CodexSessionSource")
    val constructor = sourceClass.declaredConstructors.firstOrNull { candidate ->
      val parameterTypes = candidate.parameterTypes
      !candidate.isSynthetic &&
      parameterTypes.size >= 3 &&
      parameterTypes[0] == backendClass &&
      parameterTypes[1] == refreshHintsProviderClass &&
      parameterTypes[2] == refreshHintsProviderClass &&
      parameterTypes.drop(3).all { parameterType ->
        parameterType == backendClass ||
        Function1::class.java.isAssignableFrom(parameterType) ||
        parameterType.name == "com.intellij.agent.workbench.codex.sessions.CodexThreadPathIndex" ||
        parameterType.name == "com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexExactRolloutThreadLoader"
      }
    } ?: error("Unsupported CodexSessionSource constructor shape")
    constructor.isAccessible = true
    val backend = createStaticBackend(backendClass, backendThreads)
    val appServerRefreshHintsProvider = createAppServerRefreshHintsProvider(snapshotsByThreadId)
    val rolloutRefreshHintsProvider = createEmptyRefreshHintsProvider(refreshHintsProviderClass)
    val constructorArgs = buildList {
      add(backend)
      add(appServerRefreshHintsProvider)
      add(rolloutRefreshHintsProvider)
      constructor.parameterTypes.drop(3).forEach { parameterType ->
        add(
          when {
            parameterType == backendClass -> null
            Function1::class.java.isAssignableFrom(parameterType) -> { _: Any? ->
              AgentSessionCost(amountUsd = null, kind = AgentSessionCostKind.UNAVAILABLE)
            }
            parameterType.name == "com.intellij.agent.workbench.codex.sessions.CodexThreadPathIndex" -> createInMemoryCodexThreadPathIndex()
            parameterType.name == "com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexExactRolloutThreadLoader" -> {
              createExactRolloutThreadLoader()
            }
            else -> error("Unsupported CodexSessionSource constructor parameter: ${parameterType.name}")
          }
        )
      }
    }
    return constructor.newInstance(*constructorArgs.toTypedArray()) as AgentSessionSource
  }

  private fun createInMemoryCodexThreadPathIndex(): Any {
    val indexClass = Class.forName("com.intellij.agent.workbench.codex.sessions.InMemoryCodexThreadPathIndex")
    val constructor = indexClass.declaredConstructors.firstOrNull { candidate -> !candidate.isSynthetic && candidate.parameterCount == 0 }
                      ?: error("Unsupported InMemoryCodexThreadPathIndex constructor shape")
    constructor.isAccessible = true
    return constructor.newInstance()
  }

  private fun createExactRolloutThreadLoader(): Any {
    val loaderClass = Class.forName("com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexExactRolloutThreadLoader")
    val constructor = loaderClass.declaredConstructors.firstOrNull { candidate -> !candidate.isSynthetic && candidate.parameterCount == 0 }
                      ?: loaderClass.declaredConstructors.firstOrNull { candidate ->
                        !candidate.isSynthetic && candidate.parameterCount == 1 && Function1::class.java.isAssignableFrom(candidate.parameterTypes[0])
                      }
                      ?: error("Unsupported CodexExactRolloutThreadLoader constructor shape")
    constructor.isAccessible = true
    return if (constructor.parameterCount == 0) {
      constructor.newInstance()
    }
    else {
      val parserStub: (Any?) -> Nothing? = { null }
      constructor.newInstance(parserStub)
    }
  }

  private fun createCodexBackendThread(thread: CodexThread): Any {
    val backendThreadClass = Class.forName("com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread")
    val activityClass = Class.forName("com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity")
    val readyActivity = activityClass.enumConstants.single { constant -> (constant as Enum<*>).name == "READY" }
    val constructor = backendThreadClass.declaredConstructors.firstOrNull { candidate ->
      val parameterTypes = candidate.parameterTypes
      parameterTypes.size in 4..5 &&
      parameterTypes[0] == CodexThread::class.java &&
      parameterTypes[1] == activityClass &&
      parameterTypes[2] == Boolean::class.javaPrimitiveType &&
      parameterTypes[3] == activityClass &&
      (parameterTypes.size == 4 || List::class.java.isAssignableFrom(parameterTypes[4]))
    } ?: error("Unsupported CodexBackendThread constructor shape")
    constructor.isAccessible = true
    return if (constructor.parameterCount == 5) {
      constructor.newInstance(thread, readyActivity, false, readyActivity, emptyList<Any>())
    }
    else {
      constructor.newInstance(thread, readyActivity, false, readyActivity)
    }
  }

  private fun createStaticBackend(backendClass: Class<*>, backendThreads: List<Any>): Any {
    return Proxy.newProxyInstance(backendClass.classLoader, arrayOf(backendClass)) { proxy, method, args ->
      when (method.name) {
        "listThreads" -> if (args?.firstOrNull() == PROJECT_PATH) backendThreads else emptyList<Any?>()
        "listArchivedThreads" -> emptyList<Any?>()
        "refreshThreads" -> null
        "getUpdates" -> emptyFlow<Unit>()
        "prefetchThreads" -> emptyMap<String, List<Any>>()
        "toString" -> "StaticCodexSessionBackend"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> throw UnsupportedOperationException("Unexpected method: ${method.name}")
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
        "getUpdateEvents" -> emptyFlow<Unit>()
        "prefetchRefreshHints" -> emptyMap<String, Any>()
        "toString" -> "EmptyCodexRefreshHintsProvider"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> throw UnsupportedOperationException("Unexpected method: ${method.name}")
      }
    }
  }

  private fun isCodexSessionsAvailable(): Boolean {
    return runCatching { Class.forName("com.intellij.agent.workbench.codex.sessions.CodexSessionSource") }.isSuccess
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
