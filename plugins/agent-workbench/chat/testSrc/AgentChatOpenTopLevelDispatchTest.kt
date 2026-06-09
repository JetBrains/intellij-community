package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.buildAgentThreadIdentity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextToTargetResult
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationModel
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentOpenTopLevelThreadDispatchService
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.Disposable
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
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.frontend.view.TerminalInputInterceptor
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.InplaceButton
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
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.awt.Container
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
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
    service<AgentSessionThreadPresentationModel>().clearForTests()
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
    activateEditorForTests(editor, terminalTabs)
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
  fun addContextToOpenTopLevelAgentChatAddsPendingContextWithoutSendingImmediately(): Unit = timeoutRunBlocking {
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
      threadIdentity = codexThreadIdentity("thread-context-paste"),
      shellCommand = codexResumeCommand("thread-context-paste"),
      threadId = "thread-context-paste",
      threadTitle = "Context paste thread",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    val editor = runInUi {
      FileEditorManager.getInstance(project).getAllEditors(file)
        .filterIsInstance<AgentChatFileEditor>()
        .single { candidate -> candidate.getUserData(CUSTOM_AGENT_CHAT_EDITOR_KEY) == true }
    }
    activateEditorForTests(editor, terminalTabs)

    val firstAdded = addContextToOpenTopLevelAgentChat(
      projectPath = projectPath,
      provider = AgentSessionProvider.CODEX,
      threadId = "thread-context-paste",
      contextItems = listOf(contextItem("Main.kt", "file: Main.kt")),
    )
    val secondAdded = addContextToOpenTopLevelAgentChat(
      projectPath = projectPath,
      provider = AgentSessionProvider.CODEX,
      threadId = "thread-context-paste",
      contextItems = listOf(contextItem("Util.kt", "file: Util.kt"), contextItem("Main.kt", "file: Main.kt")),
    )

    assertThat(firstAdded).isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_CHAT)
    assertThat(secondAdded).isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_CHAT)
    assertThat(terminalTabs.tab.sentTexts).isEmpty()
    assertThat(editor.pendingContextItemsForTests().map { it.title }).containsExactly("Main.kt", "Util.kt")
  }

  @Test
  fun addContextToOpenTopLevelAgentChatReportsAlreadyAddedWhenAllItemsAreDuplicates(): Unit = timeoutRunBlocking {
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
    val editor = openInitializedChatEditor(
      terminalTabs = terminalTabs,
      threadId = "thread-context-duplicate",
      threadTitle = "Context duplicate thread",
    )
    val item = contextItem("Main.kt", "file: Main.kt")

    val firstAdded = addContextToOpenTopLevelAgentChat(
      projectPath = projectPath,
      provider = AgentSessionProvider.CODEX,
      threadId = "thread-context-duplicate",
      contextItems = listOf(item),
    )
    val duplicateAdded = addContextToOpenTopLevelAgentChat(
      projectPath = projectPath,
      provider = AgentSessionProvider.CODEX,
      threadId = "thread-context-duplicate",
      contextItems = listOf(item),
    )

    assertThat(firstAdded).isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_CHAT)
    assertThat(duplicateAdded).isEqualTo(AgentPromptAddContextToTargetResult.ALREADY_ADDED_TO_CHAT)
    assertThat(editor.pendingContextItemsForTests().map { it.title }).containsExactly("Main.kt")
    assertThat(terminalTabs.tab.sentTexts).isEmpty()
  }

  @Test
  fun pendingContextChipCloseButtonRemovesItem(): Unit = timeoutRunBlocking {
    runInUi {
      val panel = AgentChatPendingContextPanel(projectPath)
      assertThat(panel.addItems(listOf(contextItem("Main.kt", "file: Main.kt")))).isTrue()

      val closeButton = findChildComponent(panel.component, InplaceButton::class.java)
                        ?: error("Pending context chip close button was not found")
      closeButton.doClick()

      assertThat(panel.pendingItemsForTests()).isEmpty()
    }
  }

  @Test
  fun pendingContextIsSubmittedOnPlainEnterAndCleared(): Unit = timeoutRunBlocking {
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
      threadIdentity = codexThreadIdentity("thread-context-submit"),
      shellCommand = codexResumeCommand("thread-context-submit"),
      threadId = "thread-context-submit",
      threadTitle = "Context submit thread",
      subAgentId = null,
    )
    val file = openedChatFiles().single()
    val editor = runInUi {
      FileEditorManager.getInstance(project).getAllEditors(file)
        .filterIsInstance<AgentChatFileEditor>()
        .single { candidate -> candidate.getUserData(CUSTOM_AGENT_CHAT_EDITOR_KEY) == true }
    }
    activateEditorForTests(editor, terminalTabs)

    assertThat(addContextToOpenTopLevelAgentChat(projectPath,
                                                 AgentSessionProvider.CODEX,
                                                 "thread-context-submit",
                                                 listOf(contextItem("Main.kt", "file: Main.kt"))))
      .isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_CHAT)

    assertThat(terminalTabs.tab.pressPlainEnter()).isTrue()

    assertThat(terminalTabs.tab.sentTexts).hasSize(1)
    val sentText = terminalTabs.tab.sentTexts.single()
    assertThat(sentText.shouldExecute).isTrue()
    assertThat(sentText.pendingContextSubmission).isTrue()
    assertThat(sentText.sendEndKeyBeforeText).isTrue()
    assertThat(sentText.requireBracketedPasteMode).isTrue()
    assertThat(sentText.text).startsWith("\n\n")
    assertThat(sentText.text).contains("### IDE Context")
    assertThat(sentText.text).contains("file: Main.kt")
    assertThat(editor.pendingContextItemsForTests()).isEmpty()
  }

  @Test
  fun pendingContextSubmitUnavailableKeepsContextAndSendsNothing(): Unit = timeoutRunBlocking {
    val terminalTabs = OpenTabDispatchFakeAgentChatTerminalTabs()
    terminalTabs.tab.pendingContextSubmissionResult = AgentChatPendingContextSubmissionResult.UNAVAILABLE
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
    val editor = openInitializedChatEditor(
      terminalTabs = terminalTabs,
      threadId = "thread-context-unavailable",
      threadTitle = "Context unavailable thread",
    )

    assertThat(addContextToOpenTopLevelAgentChat(projectPath,
                                                 AgentSessionProvider.CODEX,
                                                 "thread-context-unavailable",
                                                 listOf(contextItem("Main.kt", "file: Main.kt"))))
      .isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_CHAT)

    assertThat(terminalTabs.tab.pressPlainEnter()).isTrue()

    assertThat(terminalTabs.tab.sentTexts).isEmpty()
    assertThat(editor.pendingContextItemsForTests().map { it.title }).containsExactly("Main.kt")
  }

  @Test
  fun pendingContextSoftCapSendFullSubmitsOriginalContextAndClears(): Unit = timeoutRunBlocking {
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
    val editor = openInitializedChatEditor(
      terminalTabs = terminalTabs,
      threadId = "thread-context-send-full",
      threadTitle = "Context send full thread",
    )
    val largeBody = largeContextBody()

    assertThat(addContextToOpenTopLevelAgentChat(projectPath,
                                                 AgentSessionProvider.CODEX,
                                                 "thread-context-send-full",
                                                 listOf(contextItem("Large.kt", largeBody))))
      .isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_CHAT)

    withTestDialogChoice(0) {
      assertThat(terminalTabs.tab.pressPlainEnter()).isTrue()
    }

    val sentText = terminalTabs.tab.sentTexts.single().text
    assertThat(sentText).contains("soft-cap: limit=${AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS} auto-trim=no")
    assertThat(sentText).contains(largeBody)
    assertThat(editor.pendingContextItemsForTests()).isEmpty()
  }

  @Test
  fun pendingContextSoftCapAutoTrimSubmitsTrimmedContextAndClears(): Unit = timeoutRunBlocking {
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
    val editor = openInitializedChatEditor(
      terminalTabs = terminalTabs,
      threadId = "thread-context-auto-trim",
      threadTitle = "Context auto-trim thread",
    )
    val largeBody = largeContextBody()

    assertThat(addContextToOpenTopLevelAgentChat(projectPath,
                                                 AgentSessionProvider.CODEX,
                                                 "thread-context-auto-trim",
                                                 listOf(contextItem("Large.kt", largeBody))))
      .isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_CHAT)

    withTestDialogChoice(1) {
      assertThat(terminalTabs.tab.pressPlainEnter()).isTrue()
    }

    val sentText = terminalTabs.tab.sentTexts.single().text
    assertThat(sentText).contains("soft-cap: limit=${AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS} auto-trim=yes")
    assertThat(sentText.length).isLessThan(largeBody.length)
    assertThat(editor.pendingContextItemsForTests()).isEmpty()
  }

  @Test
  fun pendingContextSoftCapCancelKeepsContextAndDoesNotSubmit(): Unit = timeoutRunBlocking {
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
    val editor = openInitializedChatEditor(
      terminalTabs = terminalTabs,
      threadId = "thread-context-cancel",
      threadTitle = "Context cancel thread",
    )

    assertThat(addContextToOpenTopLevelAgentChat(projectPath,
                                                 AgentSessionProvider.CODEX,
                                                 "thread-context-cancel",
                                                 listOf(contextItem("Large.kt", largeContextBody()))))
      .isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_CHAT)

    withTestDialogChoice(2) {
      assertThat(terminalTabs.tab.pressPlainEnter()).isTrue()
    }

    assertThat(terminalTabs.tab.sentTexts).isEmpty()
    assertThat(editor.pendingContextItemsForTests().map { it.title }).containsExactly("Large.kt")
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
        file.threadTitle == threadTitle
      }
    }
  }

  private suspend fun openInitializedChatEditor(
    terminalTabs: OpenTabDispatchFakeAgentChatTerminalTabs,
    threadId: String,
    threadTitle: String,
  ): AgentChatFileEditor {
    openChatInModal(
      threadIdentity = codexThreadIdentity(threadId),
      shellCommand = codexResumeCommand(threadId),
      threadId = threadId,
      threadTitle = threadTitle,
      subAgentId = null,
    )
    val file = openedChatFiles().single()
    val editor = runInUi {
      FileEditorManager.getInstance(project).getAllEditors(file)
        .filterIsInstance<AgentChatFileEditor>()
        .single { candidate -> candidate.getUserData(CUSTOM_AGENT_CHAT_EDITOR_KEY) == true }
    }
    activateEditorForTests(editor, terminalTabs)
    return editor
  }

  private suspend fun activateEditorForTests(
    editor: AgentChatFileEditor,
    terminalTabs: OpenTabDispatchFakeAgentChatTerminalTabs,
  ) {
    runInUi {
      editor.selectNotify()
      editor.showComponentForTests()
    }
    waitForCondition(timeoutMs = 10_000) {
      terminalTabs.tab.hasInputInterceptorsForTests()
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

  override fun createTab(
    project: Project,
    file: AgentChatVirtualFile,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentChatTerminalTab {
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
  var pendingContextSubmissionResult: AgentChatPendingContextSubmissionResult = AgentChatPendingContextSubmissionResult.SUBMITTED
  private val inputInterceptors: MutableList<TerminalInputInterceptor> = mutableListOf()

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

  override fun sendText(text: String, shouldExecute: Boolean, useBracketedPasteMode: Boolean) {
    sentTexts += OpenTabDispatchSentTerminalText(text, shouldExecute, useBracketedPasteMode)
  }

  override fun sendPendingContextAndExecute(text: String): AgentChatPendingContextSubmissionResult {
    if (pendingContextSubmissionResult != AgentChatPendingContextSubmissionResult.SUBMITTED) {
      return pendingContextSubmissionResult
    }
    sentTexts += OpenTabDispatchSentTerminalText(
      text = text,
      shouldExecute = true,
      useBracketedPasteMode = true,
      pendingContextSubmission = true,
      requireBracketedPasteMode = true,
      sendEndKeyBeforeText = true,
    )
    return AgentChatPendingContextSubmissionResult.SUBMITTED
  }

  override fun addInputInterceptor(parentDisposable: Disposable, interceptor: TerminalInputInterceptor): Boolean {
    inputInterceptors += interceptor
    return true
  }

  fun hasInputInterceptorsForTests(): Boolean = inputInterceptors.isNotEmpty()

  fun pressPlainEnter(): Boolean {
    val event = KeyEvent(component, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED)
    return inputInterceptors.any { interceptor -> interceptor.beforeTerminalInput(event) }
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

  override suspend fun readRecentOutputTail(): String = ""
}

private data class OpenTabDispatchSentTerminalText(
  @JvmField val text: String,
  @JvmField val shouldExecute: Boolean,
  @JvmField val useBracketedPasteMode: Boolean = true,
  @JvmField val pendingContextSubmission: Boolean = false,
  @JvmField val requireBracketedPasteMode: Boolean = false,
  @JvmField val sendEndKeyBeforeText: Boolean = false,
)

private fun largeContextBody(): String = "x".repeat(AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS * 2)

private suspend fun <T> withTestDialogChoice(choice: Int, action: suspend () -> T): T {
  val previous = TestDialogManager.setTestDialog { choice }
  try {
    return action()
  }
  finally {
    TestDialogManager.setTestDialog(previous)
  }
}

private fun contextItem(title: String, body: String): AgentPromptContextItem {
  return AgentPromptContextItem(
    rendererId = "test",
    title = title,
    body = body,
    source = "test",
  )
}

private fun <T : JComponent> findChildComponent(container: Container, componentClass: Class<T>): T? {
  for (component in container.components) {
    if (componentClass.isInstance(component)) {
      return componentClass.cast(component)
    }
    if (component is Container) {
      val child = findChildComponent(component, componentClass)
      if (child != null) {
        return child
      }
    }
  }
  return null
}

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
