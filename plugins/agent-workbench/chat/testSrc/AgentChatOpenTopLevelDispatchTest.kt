package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.buildAgentThreadIdentity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentOpenTopLevelThreadDispatchService
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
class AgentChatOpenTopLevelDispatchTest {
  companion object {
    private val CUSTOM_AGENT_CHAT_EDITOR_KEY: Key<Boolean> = Key.create("agent.workbench.chat.openTabDispatch.customEditor")

    @Volatile
    private var customFileEditorFactory: ((Project, AgentChatVirtualFile) -> FileEditor)? = null
  }

  private val projectFixture = projectFixture(openAfterCreation = true)
  private val project get() = projectFixture.get()
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()

  private val projectPath = "/work/project-a"
  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking {
    runInUi {
      fileEditorManagerFixture.get()
      FileEditorProvider.EP_FILE_EDITOR_PROVIDER.point.registerExtension(
        OpenTabDispatchChatFileEditorProvider(),
        LoadingOrder.FIRST,
        project,
      )
    }
  }

  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking {
    customFileEditorFactory = null
    withTimeout(30.seconds) {
      project.waitForSmartMode()
    }
  }

  @Test
  fun dispatchOpenTopLevelThreadIfPresentFlushesToMatchingEditor(): Unit = timeoutRunBlocking {
    val terminalTabs = OpenTabDispatchFakeAgentChatTerminalTabs()
    customFileEditorFactory = { editorProject, file ->
      AgentChatFileEditor(
        project = editorProject,
        file = file,
        terminalTabs = terminalTabs,
        tabSnapshotWriter = AgentChatTabSnapshotWriter { snapshot ->
          editorProject.service<AgentChatTabsService>().upsert(snapshot)
        },
      ).also { editor ->
        editor.putUserData(CUSTOM_AGENT_CHAT_EDITOR_KEY, true)
      }
    }

    openChatInModal(
      threadIdentity = codexThreadIdentity("thread-open-dispatch"),
      shellCommand = codexResumeCommand("thread-open-dispatch"),
      threadId = "thread-open-dispatch",
      threadTitle = "Dispatch thread",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    val editor = runInUi {
      FileEditorManager.getInstance(project).getAllEditors(file)
        .filterIsInstance<AgentChatFileEditor>()
        .single { candidate -> candidate.getUserData(CUSTOM_AGENT_CHAT_EDITOR_KEY) == true }
    }
    runInUi {
      editor.selectNotify()
    }
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)

    val dispatched = service<AgentOpenTopLevelThreadDispatchService>().dispatchIfPresent(
      projectPath = projectPath,
      provider = AgentSessionProvider.CODEX,
      threadId = "thread-open-dispatch",
      launchSpec = AgentSessionTerminalLaunchSpec(command = codexResumeCommand("thread-open-dispatch")),
      initialMessageDispatchPlan = AgentInitialMessageDispatchPlan(
        postStartDispatchSteps = listOf(AgentInitialMessageDispatchStep(text = "Dispatch through helper")),
        initialMessageToken = "dispatch-open-token",
      ),
    )

    assertThat(dispatched).isTrue()
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(OpenTabDispatchSentTerminalText("Dispatch through helper", shouldExecute = true))

    val persisted = checkNotNull(service<AgentChatTabsService>().load(file.tabKey))
    assertThat(persisted.runtime.initialMessageSent).isTrue()
  }

  @Test
  fun dispatchOpenTopLevelThreadIfPresentSkipsSubAgentTab(): Unit = timeoutRunBlocking {
    val terminalTabs = OpenTabDispatchFakeAgentChatTerminalTabs()
    customFileEditorFactory = { editorProject, file ->
      AgentChatFileEditor(
        project = editorProject,
        file = file,
        terminalTabs = terminalTabs,
        tabSnapshotWriter = AgentChatTabSnapshotWriter { snapshot ->
          editorProject.service<AgentChatTabsService>().upsert(snapshot)
        },
      ).also { editor ->
        editor.putUserData(CUSTOM_AGENT_CHAT_EDITOR_KEY, true)
      }
    }

    openChatInModal(
      threadIdentity = codexThreadIdentity("thread-sub-agent-only"),
      shellCommand = codexResumeCommand("thread-sub-agent-only"),
      threadId = "thread-sub-agent-only",
      threadTitle = "Sub-agent thread",
      subAgentId = "worker-1",
    )

    val dispatched = service<AgentOpenTopLevelThreadDispatchService>().dispatchIfPresent(
      projectPath = projectPath,
      provider = AgentSessionProvider.CODEX,
      threadId = "thread-sub-agent-only",
      launchSpec = AgentSessionTerminalLaunchSpec(command = codexResumeCommand("thread-sub-agent-only")),
      initialMessageDispatchPlan = AgentInitialMessageDispatchPlan(
        postStartDispatchSteps = listOf(AgentInitialMessageDispatchStep(text = "Should not dispatch")),
      ),
    )

    assertThat(dispatched).isFalse()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()
  }

  private suspend fun openedChatFiles(): List<AgentChatVirtualFile> {
    return runInUi {
      FileEditorManager.getInstance(project).openFiles.filterIsInstance<AgentChatVirtualFile>()
    }
  }

  private suspend fun openChatInModal(
    threadIdentity: String,
    shellCommand: List<String>,
    threadId: String,
    threadTitle: String,
    subAgentId: String?,
  ) {
    openChat(
      project = project,
      projectPath = projectPath,
      threadIdentity = threadIdentity,
      shellCommand = shellCommand,
      threadId = threadId,
      threadTitle = threadTitle,
      subAgentId = subAgentId,
    )
    waitForCondition(timeoutMs = 10_000) {
      openedChatFiles().any { file ->
        file.threadIdentity == threadIdentity &&
        file.subAgentId == subAgentId &&
        file.threadId == threadId &&
        file.threadTitle == threadTitle &&
        file.shellCommand == shellCommand
      }
    }
  }

  private class OpenTabDispatchChatFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
      return file is AgentChatVirtualFile
    }

    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
      val chatFile = file as AgentChatVirtualFile
      return customFileEditorFactory?.invoke(project, chatFile)
             ?: LightweightTestFileEditor(file, editorName = "AgentChatOpenTabDispatchTestEditor")
    }

    override fun getEditorTypeId(): String = "agent.workbench-chat-open-tab-dispatch-test"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
  }
}

private class OpenTabDispatchFakeAgentChatTerminalTabs : AgentChatTerminalTabs {
  val tab = OpenTabDispatchFakeAgentChatTerminalTab()

  override fun createTab(project: Project, file: AgentChatVirtualFile): AgentChatTerminalTab {
    return tab
  }

  override fun closeTab(project: Project, tab: AgentChatTerminalTab) {
    (tab as? OpenTabDispatchFakeAgentChatTerminalTab)?.coroutineScope?.coroutineContext?.get(Job)?.cancel()
  }
}

private class OpenTabDispatchFakeAgentChatTerminalTab : AgentChatTerminalTab {
  override val component: JComponent = JPanel()
  override val preferredFocusableComponent: JComponent = JButton("focus")
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext = Job()
  }
  private val mutableSessionState: MutableStateFlow<TerminalViewSessionState> = MutableStateFlow(TerminalViewSessionState.NotStarted)
  override val sessionState: StateFlow<TerminalViewSessionState> = mutableSessionState
  override val keyEventsFlow: Flow<TerminalKeyEvent> = emptyFlow()

  val sentTexts: MutableList<OpenTabDispatchSentTerminalText> = mutableListOf()

  fun setSessionState(state: TerminalViewSessionState) {
    mutableSessionState.value = state
  }

  override suspend fun captureOutputCheckpoint(): AgentChatTerminalOutputCheckpoint {
    return AgentChatTerminalOutputCheckpoint(regularEndOffset = 0, alternativeEndOffset = 0)
  }

  override suspend fun awaitOutputObservation(
    checkpoint: AgentChatTerminalOutputCheckpoint,
    timeoutMs: Long,
    idleMs: Long,
  ): AgentChatTerminalOutputObservation {
    return AgentChatTerminalOutputObservation(
      readiness = if (sessionState.value == TerminalViewSessionState.Terminated) {
        AgentChatTerminalInputReadiness.TERMINATED
      }
      else {
        AgentChatTerminalInputReadiness.READY
      },
      text = "",
    )
  }

  override fun sendText(text: String, shouldExecute: Boolean) {
    sentTexts += OpenTabDispatchSentTerminalText(text, shouldExecute, useBracketedPasteMode = true)
  }

  override suspend fun awaitInitialMessageReadiness(
    timeoutMs: Long,
    idleMs: Long,
    checkpoint: AgentChatTerminalOutputCheckpoint?,
  ): AgentChatTerminalInputReadiness {
    return if (sessionState.value == TerminalViewSessionState.Terminated) {
      AgentChatTerminalInputReadiness.TERMINATED
    }
    else {
      AgentChatTerminalInputReadiness.READY
    }
  }
}

private data class OpenTabDispatchSentTerminalText(
  @JvmField val text: String,
  @JvmField val shouldExecute: Boolean,
  @JvmField val useBracketedPasteMode: Boolean = true,
)

private fun codexThreadIdentity(threadId: String): String {
  return buildAgentThreadIdentity(providerId = AgentSessionProvider.CODEX.value, threadId = threadId)
}

private fun codexResumeCommand(threadId: String): List<String> {
  return listOf("codex", "resume", threadId)
}

private suspend fun <T> runInUi(action: suspend () -> T): T {
  return withContext(Dispatchers.UiWithModelAccess) {
    action()
  }
}

private suspend fun waitForCondition(timeoutMs: Long = 5_000, condition: suspend () -> Boolean) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    if (condition()) {
      return
    }
    delay(20.milliseconds)
  }
  throw AssertionError("Condition was not satisfied within ${timeoutMs}ms")
}
