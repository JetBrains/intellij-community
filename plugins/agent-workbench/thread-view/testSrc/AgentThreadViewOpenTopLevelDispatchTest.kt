package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.buildAgentThreadIdentity
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItem
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItemKind
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSessionThreadOutline
import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextToTargetResult
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadPresentationModel
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.platform.ai.agent.sessions.core.providers.AgentOpenTopLevelThreadDispatchService
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionOutlineForkResult
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadOutlineForkSource
import com.intellij.platform.ai.agent.sessions.core.providers.InMemoryAgentSessionProviderRegistry
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
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryPlan
import com.intellij.terminal.frontend.view.TerminalInputInterceptor
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.InplaceButton
import com.intellij.util.ui.EmptyIcon
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
class AgentThreadViewOpenTopLevelDispatchTest {
  companion object {
    private val CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY: Key<Boolean> = Key.create("agent.workbench.threadView.openTabDispatch.customEditor")

    @Volatile
    private var customFileEditorFactory: ((Project, AgentThreadViewVirtualFile) -> FileEditor)? = null
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
        OpenTabDispatchThreadViewFileEditorProvider(),
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
    val terminalTabs = OpenTabDispatchFakeAgentThreadViewTerminalTabs()
    customFileEditorFactory = { editorProject, file ->
      AgentThreadViewFileEditor(
        project = editorProject,
        file = file,
        terminalTabs = terminalTabs,
        tabSnapshotWriter = AgentThreadViewTabSnapshotWriter { snapshot ->
          editorProject.service<AgentThreadViewTabsService>().upsert(snapshot)
        },
      ).also { editor ->
        editor.putUserData(CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY, true)
      }
    }

    openThreadViewInModal(
      threadIdentity = codexThreadIdentity("thread-open-dispatch"),
      shellCommand = codexResumeCommand("thread-open-dispatch"),
      threadId = "thread-open-dispatch",
      threadTitle = "Dispatch thread",
      subAgentId = null,
    )

    val file = openedThreadViewFiles().single()
    val editor = runInUi {
      FileEditorManager.getInstance(project).getAllEditors(file)
        .filterIsInstance<AgentThreadViewFileEditor>()
        .single { candidate -> candidate.getUserData(CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY) == true }
    }
    activateEditorForTests(editor, terminalTabs)
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)

    val dispatched = service<AgentOpenTopLevelThreadDispatchService>().dispatchIfPresent(
      projectPath = projectPath,
      provider = AgentSessionProvider.from("codex"),
      threadId = "thread-open-dispatch",
      launchSpec = AgentSessionTerminalLaunchSpec(command = codexResumeCommand("thread-open-dispatch")),
      initialMessageDispatchPlan = AgentInitialPromptDeliveryPlan(
        postStartDispatchSteps = listOf(AgentInitialMessageDispatchStep(text = "Dispatch through helper")),
        initialMessageToken = "dispatch-open-token",
      ),
    )

    assertThat(dispatched).isTrue()
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(OpenTabDispatchSentTerminalText("Dispatch through helper", shouldExecute = true))
    waitForCondition { file.initialMessageSent }
    assertThat(file.initialMessageSent).isTrue()
  }

  @Test
  fun addContextToOpenTopLevelAgentThreadViewAddsPendingContextWithoutSendingImmediately(): Unit = timeoutRunBlocking {
    val terminalTabs = OpenTabDispatchFakeAgentThreadViewTerminalTabs()
    customFileEditorFactory = { editorProject, file ->
      AgentThreadViewFileEditor(
        project = editorProject,
        file = file,
        terminalTabs = terminalTabs,
        tabSnapshotWriter = AgentThreadViewTabSnapshotWriter { snapshot ->
          editorProject.service<AgentThreadViewTabsService>().upsert(snapshot)
        },
      ).also { editor ->
        editor.putUserData(CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY, true)
      }
    }

    openThreadViewInModal(
      threadIdentity = codexThreadIdentity("thread-context-paste"),
      shellCommand = codexResumeCommand("thread-context-paste"),
      threadId = "thread-context-paste",
      threadTitle = "Context paste thread",
      subAgentId = null,
    )

    val file = openedThreadViewFiles().single()
    val editor = runInUi {
      FileEditorManager.getInstance(project).getAllEditors(file)
        .filterIsInstance<AgentThreadViewFileEditor>()
        .single { candidate -> candidate.getUserData(CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY) == true }
    }
    activateEditorForTests(editor, terminalTabs)

    val firstAdded = addContextToOpenTopLevelAgentThreadView(
      projectPath = projectPath,
      provider = AgentSessionProvider.from("codex"),
      threadId = "thread-context-paste",
      contextItems = listOf(contextItem("Main.kt", "file: Main.kt")),
    )
    val secondAdded = addContextToOpenTopLevelAgentThreadView(
      projectPath = projectPath,
      provider = AgentSessionProvider.from("codex"),
      threadId = "thread-context-paste",
      contextItems = listOf(contextItem("Util.kt", "file: Util.kt"), contextItem("Main.kt", "file: Main.kt")),
    )

    assertThat(firstAdded).isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_THREAD_VIEW)
    assertThat(secondAdded).isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_THREAD_VIEW)
    assertThat(terminalTabs.tab.sentTexts).isEmpty()
    assertThat(editor.pendingContextItemsForTests().map { it.title }).containsExactly("Main.kt", "Util.kt")
  }

  @Test
  fun addContextToOpenTopLevelAgentThreadViewReportsAlreadyAddedWhenAllItemsAreDuplicates(): Unit = timeoutRunBlocking {
    val terminalTabs = OpenTabDispatchFakeAgentThreadViewTerminalTabs()
    customFileEditorFactory = { editorProject, file ->
      AgentThreadViewFileEditor(
        project = editorProject,
        file = file,
        terminalTabs = terminalTabs,
        tabSnapshotWriter = AgentThreadViewTabSnapshotWriter { snapshot ->
          editorProject.service<AgentThreadViewTabsService>().upsert(snapshot)
        },
      ).also { editor ->
        editor.putUserData(CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY, true)
      }
    }
    val editor = openInitializedThreadViewEditor(
      terminalTabs = terminalTabs,
      threadId = "thread-context-duplicate",
      threadTitle = "Context duplicate thread",
    )
    val item = contextItem("Main.kt", "file: Main.kt")

    val firstAdded = addContextToOpenTopLevelAgentThreadView(
      projectPath = projectPath,
      provider = AgentSessionProvider.from("codex"),
      threadId = "thread-context-duplicate",
      contextItems = listOf(item),
    )
    val duplicateAdded = addContextToOpenTopLevelAgentThreadView(
      projectPath = projectPath,
      provider = AgentSessionProvider.from("codex"),
      threadId = "thread-context-duplicate",
      contextItems = listOf(item),
    )

    assertThat(firstAdded).isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_THREAD_VIEW)
    assertThat(duplicateAdded).isEqualTo(AgentPromptAddContextToTargetResult.ALREADY_ADDED_TO_THREAD_VIEW)
    assertThat(editor.pendingContextItemsForTests().map { it.title }).containsExactly("Main.kt")
    assertThat(terminalTabs.tab.sentTexts).isEmpty()
  }

  @Test
  fun pendingContextChipCloseButtonRemovesItem(): Unit = timeoutRunBlocking {
    runInUi {
      val panel = AgentThreadViewPendingContextPanel(projectPath)
      assertThat(panel.addItems(listOf(contextItem("Main.kt", "file: Main.kt")))).isTrue()

      val closeButton = findChildComponent(panel.component, InplaceButton::class.java)
                        ?: error("Pending context chip close button was not found")
      closeButton.doClick()

      assertThat(panel.pendingItemsForTests()).isEmpty()
    }
  }

  @Test
  fun pendingContextIsSubmittedOnPlainEnterAndCleared(): Unit = timeoutRunBlocking {
    val terminalTabs = OpenTabDispatchFakeAgentThreadViewTerminalTabs()
    customFileEditorFactory = { editorProject, file ->
      AgentThreadViewFileEditor(
        project = editorProject,
        file = file,
        terminalTabs = terminalTabs,
        tabSnapshotWriter = AgentThreadViewTabSnapshotWriter { snapshot ->
          editorProject.service<AgentThreadViewTabsService>().upsert(snapshot)
        },
      ).also { editor ->
        editor.putUserData(CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY, true)
      }
    }

    openThreadViewInModal(
      threadIdentity = codexThreadIdentity("thread-context-submit"),
      shellCommand = codexResumeCommand("thread-context-submit"),
      threadId = "thread-context-submit",
      threadTitle = "Context submit thread",
      subAgentId = null,
    )
    val file = openedThreadViewFiles().single()
    val editor = runInUi {
      FileEditorManager.getInstance(project).getAllEditors(file)
        .filterIsInstance<AgentThreadViewFileEditor>()
        .single { candidate -> candidate.getUserData(CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY) == true }
    }
    activateEditorForTests(editor, terminalTabs)

    assertThat(addContextToOpenTopLevelAgentThreadView(projectPath,
                                                 AgentSessionProvider.from("codex"),
                                                 "thread-context-submit",
                                                 listOf(contextItem("Main.kt", "file: Main.kt"))))
      .isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_THREAD_VIEW)

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
    val terminalTabs = OpenTabDispatchFakeAgentThreadViewTerminalTabs()
    terminalTabs.tab.pendingContextSubmissionResult = AgentThreadViewPendingContextSubmissionResult.UNAVAILABLE
    customFileEditorFactory = { editorProject, file ->
      AgentThreadViewFileEditor(
        project = editorProject,
        file = file,
        terminalTabs = terminalTabs,
        tabSnapshotWriter = AgentThreadViewTabSnapshotWriter { snapshot ->
          editorProject.service<AgentThreadViewTabsService>().upsert(snapshot)
        },
      ).also { editor ->
        editor.putUserData(CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY, true)
      }
    }
    val editor = openInitializedThreadViewEditor(
      terminalTabs = terminalTabs,
      threadId = "thread-context-unavailable",
      threadTitle = "Context unavailable thread",
    )

    assertThat(addContextToOpenTopLevelAgentThreadView(projectPath,
                                                 AgentSessionProvider.from("codex"),
                                                 "thread-context-unavailable",
                                                 listOf(contextItem("Main.kt", "file: Main.kt"))))
      .isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_THREAD_VIEW)

    assertThat(terminalTabs.tab.pressPlainEnter()).isTrue()

    assertThat(terminalTabs.tab.sentTexts).isEmpty()
    assertThat(editor.pendingContextItemsForTests().map { it.title }).containsExactly("Main.kt")
  }

  @Test
  fun pendingContextSoftCapSendFullSubmitsOriginalContextAndClears(): Unit = timeoutRunBlocking {
    val terminalTabs = OpenTabDispatchFakeAgentThreadViewTerminalTabs()
    customFileEditorFactory = { editorProject, file ->
      AgentThreadViewFileEditor(
        project = editorProject,
        file = file,
        terminalTabs = terminalTabs,
        tabSnapshotWriter = AgentThreadViewTabSnapshotWriter { snapshot ->
          editorProject.service<AgentThreadViewTabsService>().upsert(snapshot)
        },
      ).also { editor ->
        editor.putUserData(CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY, true)
      }
    }
    val editor = openInitializedThreadViewEditor(
      terminalTabs = terminalTabs,
      threadId = "thread-context-send-full",
      threadTitle = "Context send full thread",
    )
    val largeBody = largeContextBody()

    assertThat(addContextToOpenTopLevelAgentThreadView(projectPath,
                                                 AgentSessionProvider.from("codex"),
                                                 "thread-context-send-full",
                                                 listOf(contextItem("Large.kt", largeBody))))
      .isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_THREAD_VIEW)

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
    val terminalTabs = OpenTabDispatchFakeAgentThreadViewTerminalTabs()
    customFileEditorFactory = { editorProject, file ->
      AgentThreadViewFileEditor(
        project = editorProject,
        file = file,
        terminalTabs = terminalTabs,
        tabSnapshotWriter = AgentThreadViewTabSnapshotWriter { snapshot ->
          editorProject.service<AgentThreadViewTabsService>().upsert(snapshot)
        },
      ).also { editor ->
        editor.putUserData(CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY, true)
      }
    }
    val editor = openInitializedThreadViewEditor(
      terminalTabs = terminalTabs,
      threadId = "thread-context-auto-trim",
      threadTitle = "Context auto-trim thread",
    )
    val largeBody = largeContextBody()

    assertThat(addContextToOpenTopLevelAgentThreadView(projectPath,
                                                 AgentSessionProvider.from("codex"),
                                                 "thread-context-auto-trim",
                                                 listOf(contextItem("Large.kt", largeBody))))
      .isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_THREAD_VIEW)

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
    val terminalTabs = OpenTabDispatchFakeAgentThreadViewTerminalTabs()
    customFileEditorFactory = { editorProject, file ->
      AgentThreadViewFileEditor(
        project = editorProject,
        file = file,
        terminalTabs = terminalTabs,
        tabSnapshotWriter = AgentThreadViewTabSnapshotWriter { snapshot ->
          editorProject.service<AgentThreadViewTabsService>().upsert(snapshot)
        },
      ).also { editor ->
        editor.putUserData(CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY, true)
      }
    }
    val editor = openInitializedThreadViewEditor(
      terminalTabs = terminalTabs,
      threadId = "thread-context-cancel",
      threadTitle = "Context cancel thread",
    )

    assertThat(addContextToOpenTopLevelAgentThreadView(projectPath,
                                                 AgentSessionProvider.from("codex"),
                                                 "thread-context-cancel",
                                                 listOf(contextItem("Large.kt", largeContextBody()))))
      .isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_THREAD_VIEW)

    withTestDialogChoice(2) {
      assertThat(terminalTabs.tab.pressPlainEnter()).isTrue()
    }

    assertThat(terminalTabs.tab.sentTexts).isEmpty()
    assertThat(editor.pendingContextItemsForTests().map { it.title }).containsExactly("Large.kt")
  }

  @Test
  fun dispatchOpenTopLevelThreadIfPresentSkipsSubAgentTab(): Unit = timeoutRunBlocking {
    val terminalTabs = OpenTabDispatchFakeAgentThreadViewTerminalTabs()
    customFileEditorFactory = { editorProject, file ->
      AgentThreadViewFileEditor(
        project = editorProject,
        file = file,
        terminalTabs = terminalTabs,
        tabSnapshotWriter = AgentThreadViewTabSnapshotWriter { snapshot ->
          editorProject.service<AgentThreadViewTabsService>().upsert(snapshot)
        },
      ).also { editor ->
        editor.putUserData(CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY, true)
      }
    }

    openThreadViewInModal(
      threadIdentity = codexThreadIdentity("thread-sub-agent-only"),
      shellCommand = codexResumeCommand("thread-sub-agent-only"),
      threadId = "thread-sub-agent-only",
      threadTitle = "Sub-agent thread",
      subAgentId = "worker-1",
    )

    val dispatched = service<AgentOpenTopLevelThreadDispatchService>().dispatchIfPresent(
      projectPath = projectPath,
      provider = AgentSessionProvider.from("codex"),
      threadId = "thread-sub-agent-only",
      launchSpec = AgentSessionTerminalLaunchSpec(command = codexResumeCommand("thread-sub-agent-only")),
      initialMessageDispatchPlan = AgentInitialPromptDeliveryPlan(
        postStartDispatchSteps = listOf(AgentInitialMessageDispatchStep(text = "Should not dispatch")),
      ),
    )

    assertThat(dispatched).isFalse()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()
  }

  @Test
  fun structureViewForkOpensForkedPiThreadViewTab() {
    val terminalTabs = OpenTabDispatchFakeAgentThreadViewTerminalTabs()
    customFileEditorFactory = { editorProject, file ->
      AgentThreadViewFileEditor(
        project = editorProject,
        file = file,
        terminalTabs = terminalTabs,
        tabSnapshotWriter = AgentThreadViewTabSnapshotWriter { snapshot ->
          editorProject.service<AgentThreadViewTabsService>().upsert(snapshot)
        },
      ).also { editor ->
        editor.putUserData(CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY, true)
      }
    }
    val source = OpenTabDispatchPiForkSource()
    val descriptor = OpenTabDispatchPiProviderDescriptor(source)

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      timeoutRunBlocking {
        openThreadViewInModal(
          threadIdentity = piThreadIdentity("thread-outline-fork"),
          shellCommand = piResumeCommand("thread-outline-fork"),
          threadId = "thread-outline-fork",
          threadTitle = "Original Pi thread",
          subAgentId = null,
        )
        val file = openedThreadViewFiles().single { threadViewFile -> threadViewFile.threadId == "thread-outline-fork" }
        val startupLaunchSpecCountBeforeFork = terminalTabs.startupLaunchSpecs.size
        val item = AgentSessionOutlineItem(
          id = "entry-fork",
          kind = AgentSessionOutlineItemKind.USER_PROMPT,
          title = "Fork from here",
        )

        val forked = forkAgentThreadViewThreadOutlineTarget(
          project = project,
          target = AgentThreadViewThreadOutlineTarget(file = file, source = source, item = item),
        )

        assertThat(forked).isTrue()
        assertThat(source.forkCalls).containsExactly(
          OpenTabDispatchPiForkCall(
            path = projectPath,
            threadId = "thread-outline-fork",
            itemId = "entry-fork",
            subAgentId = null,
            tabKey = file.tabKey,
          )
        )
        assertThat(file.threadIdentity).isEqualTo(piThreadIdentity("thread-outline-fork"))
        assertThat(file.threadId).isEqualTo("thread-outline-fork")
        assertThat(file.threadTitle).isEqualTo("Original Pi thread")
        assertThat(file.newThreadRebindRequestedAtMs).isNull()

        val files = openedThreadViewFiles()
        assertThat(files.map { it.threadId }).containsExactlyInAnyOrder("thread-outline-fork", "thread-outline-forked")
        val forkedFile = files.single { threadViewFile -> threadViewFile.threadId == "thread-outline-forked" }
        assertThat(forkedFile.threadIdentity).isEqualTo(piThreadIdentity("thread-outline-forked"))
        assertThat(forkedFile.threadTitle).isEqualTo("Forked Pi thread")
        assertThat(forkedFile.threadActivity).isEqualTo(AgentThreadActivity.PROCESSING)
        assertThat(forkedFile.newThreadRebindRequestedAtMs).isNull()
        val forkedEditor = runInUi {
          FileEditorManager.getInstance(project).getAllEditors(forkedFile)
            .filterIsInstance<AgentThreadViewFileEditor>()
            .single { candidate -> candidate.getUserData(CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY) == true }
        }
        activateEditorForTests(forkedEditor, terminalTabs)
        waitForCondition {
          terminalTabs.startupLaunchSpecs.size > startupLaunchSpecCountBeforeFork
        }
        val forkedLaunchSpec = terminalTabs.startupLaunchSpecs.last()
        assertThat(forkedLaunchSpec.command)
          .containsExactly("pi", "--session", "thread-outline-forked", "--from-outline-fork")
        assertThat(forkedLaunchSpec.envVariables).containsEntry("FORK_OVERRIDE", "1")
        val selectedFile = runInUi {
          FileEditorManager.getInstance(project).selectedFiles.singleOrNull()
        }
        assertThat(selectedFile).isSameAs(forkedFile)

        val forkedAgain = forkAgentThreadViewThreadOutlineTarget(
          project = project,
          target = AgentThreadViewThreadOutlineTarget(file = file, source = source, item = item),
        )

        assertThat(forkedAgain).isTrue()
        assertThat(openedThreadViewFiles().map { it.threadId })
          .containsExactlyInAnyOrder("thread-outline-fork", "thread-outline-forked")
      }
    }
  }

  private suspend fun openedThreadViewFiles(): List<AgentThreadViewVirtualFile> {
    return runInUi {
      FileEditorManager.getInstance(project).openFiles.filterIsInstance<AgentThreadViewVirtualFile>()
    }
  }

  private suspend fun openThreadViewInModal(
    threadIdentity: String,
    shellCommand: List<String>,
    threadId: String,
    threadTitle: String,
    subAgentId: String?,
  ) {
    openThreadView(
      project = project,
      projectPath = projectPath,
      threadIdentity = threadIdentity,
      shellCommand = shellCommand,
      threadId = threadId,
      threadTitle = threadTitle,
      subAgentId = subAgentId,
    )
    waitForCondition(timeoutMs = 10_000) {
      openedThreadViewFiles().any { file ->
        file.threadIdentity == threadIdentity &&
        file.subAgentId == subAgentId &&
        file.threadId == threadId &&
        file.threadTitle == threadTitle
      }
    }
  }

  private suspend fun openInitializedThreadViewEditor(
    terminalTabs: OpenTabDispatchFakeAgentThreadViewTerminalTabs,
    threadId: String,
    threadTitle: String,
  ): AgentThreadViewFileEditor {
    openThreadViewInModal(
      threadIdentity = codexThreadIdentity(threadId),
      shellCommand = codexResumeCommand(threadId),
      threadId = threadId,
      threadTitle = threadTitle,
      subAgentId = null,
    )
    val file = openedThreadViewFiles().single()
    val editor = runInUi {
      FileEditorManager.getInstance(project).getAllEditors(file)
        .filterIsInstance<AgentThreadViewFileEditor>()
        .single { candidate -> candidate.getUserData(CUSTOM_AGENT_THREAD_VIEW_EDITOR_KEY) == true }
    }
    activateEditorForTests(editor, terminalTabs)
    return editor
  }

  private suspend fun activateEditorForTests(
    editor: AgentThreadViewFileEditor,
    terminalTabs: OpenTabDispatchFakeAgentThreadViewTerminalTabs,
  ) {
    runInUi {
      editor.selectNotify()
      editor.showComponentForTests()
    }
    waitForCondition(timeoutMs = 10_000) {
      terminalTabs.tab.hasInputInterceptorsForTests()
    }
  }

  private class OpenTabDispatchThreadViewFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
      return file is AgentThreadViewVirtualFile
    }

    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
      val threadViewFile = file as AgentThreadViewVirtualFile
      return customFileEditorFactory?.invoke(project, threadViewFile)
             ?: LightweightTestFileEditor(file, editorName = "AgentThreadViewOpenTabDispatchTestEditor")
    }

    override fun getEditorTypeId(): String = "agent.workbench-threadView-open-tab-dispatch-test"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
  }
}

private class OpenTabDispatchFakeAgentThreadViewTerminalTabs : AgentThreadViewTerminalTabs {
  val tab = OpenTabDispatchFakeAgentThreadViewTerminalTab()
  val startupLaunchSpecs: MutableList<AgentSessionTerminalLaunchSpec> = mutableListOf()

  override fun createTab(
    project: Project,
    file: AgentThreadViewVirtualFile,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentThreadViewTerminalTab {
    startupLaunchSpecs += startupLaunchSpec
    return tab
  }

  override fun closeTab(project: Project, tab: AgentThreadViewTerminalTab) {
    (tab as? OpenTabDispatchFakeAgentThreadViewTerminalTab)?.coroutineScope?.coroutineContext?.get(Job)?.cancel()
  }
}

private class OpenTabDispatchFakeAgentThreadViewTerminalTab : AgentThreadViewTerminalTab {
  override val component: JComponent = JPanel()
  override val preferredFocusableComponent: JComponent = JButton("focus")
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext = Job()
  }
  private val mutableSessionState: MutableStateFlow<TerminalViewSessionState> = MutableStateFlow(TerminalViewSessionState.NotStarted)
  override val sessionState: StateFlow<TerminalViewSessionState> = mutableSessionState
  override val keyEventsFlow: Flow<TerminalKeyEvent> = emptyFlow()

  val sentTexts: MutableList<OpenTabDispatchSentTerminalText> = mutableListOf()
  var pendingContextSubmissionResult: AgentThreadViewPendingContextSubmissionResult = AgentThreadViewPendingContextSubmissionResult.SUBMITTED
  private val inputInterceptors: MutableList<TerminalInputInterceptor> = mutableListOf()

  fun setSessionState(state: TerminalViewSessionState) {
    mutableSessionState.value = state
  }

  override suspend fun captureOutputCheckpoint(): AgentThreadViewTerminalOutputCheckpoint {
    return AgentThreadViewTerminalOutputCheckpoint(regularEndOffset = 0, alternativeEndOffset = 0)
  }

  override suspend fun awaitOutputObservation(
    checkpoint: AgentThreadViewTerminalOutputCheckpoint,
    timeoutMs: Long,
    idleMs: Long,
  ): AgentThreadViewTerminalOutputObservation {
    return AgentThreadViewTerminalOutputObservation(
      readiness = if (sessionState.value == TerminalViewSessionState.Terminated) {
        AgentThreadViewTerminalInputReadiness.TERMINATED
      }
      else {
        AgentThreadViewTerminalInputReadiness.READY
      },
      text = "",
    )
  }

  override fun sendText(text: String, shouldExecute: Boolean, useBracketedPasteMode: Boolean) {
    sentTexts += OpenTabDispatchSentTerminalText(text, shouldExecute, useBracketedPasteMode)
  }

  override fun sendPendingContextAndExecute(text: String): AgentThreadViewPendingContextSubmissionResult {
    if (pendingContextSubmissionResult != AgentThreadViewPendingContextSubmissionResult.SUBMITTED) {
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
    return AgentThreadViewPendingContextSubmissionResult.SUBMITTED
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
    checkpoint: AgentThreadViewTerminalOutputCheckpoint?,
  ): AgentThreadViewTerminalInputReadiness {
    return if (sessionState.value == TerminalViewSessionState.Terminated) {
      AgentThreadViewTerminalInputReadiness.TERMINATED
    }
    else {
      AgentThreadViewTerminalInputReadiness.READY
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
  return buildAgentThreadIdentity(providerId = AgentSessionProvider.from("codex").value, threadId = threadId)
}

private fun piThreadIdentity(threadId: String): String {
  return buildAgentThreadIdentity(providerId = AgentSessionProvider.from("pi").value, threadId = threadId)
}

private fun codexResumeCommand(threadId: String): List<String> {
  return listOf("codex", "resume", threadId)
}

private fun piResumeCommand(threadId: String): List<String> {
  return listOf("pi", "--session", threadId)
}

private class OpenTabDispatchPiProviderDescriptor(
  override val sessionSource: AgentSessionSource,
) : AgentSessionProviderDescriptor {
  override val provider: AgentSessionProvider = AgentSessionProvider.from("pi")
  override val displayNameKey: String = "pi"
  override val newSessionLabelKey: String = "pi"
  override val icon = EmptyIcon.create(18, 18)
  override val cliMissingMessageKey: String = "pi"

  override suspend fun isCliAvailable(): Boolean = true

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = piResumeCommand(sessionId))
  }

  override val supportsGenerationModelSelection: Boolean
    get() = true

  override val resolvesGenerationModelCatalogForAutoSettings: Boolean
    get() = true

  override suspend fun listAvailableGenerationModels(project: Project?): List<AgentPromptGenerationModel> {
    return listOf(AgentPromptGenerationModel(id = "openai/gpt-5.4", displayName = "GPT 5.4"))
  }

  override fun applyGenerationModelCatalog(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    generationSettings: AgentPromptGenerationSettings,
    generationModelCatalog: List<AgentPromptGenerationModel>,
  ): AgentSessionTerminalLaunchSpec {
    if (generationModelCatalog.isEmpty()) {
      return baseLaunchSpec
    }
    return baseLaunchSpec.copy(
      command = baseLaunchSpec.command + listOf("--models", generationModelCatalog.joinToString(",") { model -> model.id }),
      envVariables = baseLaunchSpec.envVariables + mapOf("MODEL_COUNT" to generationModelCatalog.size.toString()),
    )
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("pi", "--session-id", "new-session"))
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    return AgentInitialMessagePlan.composeDefault(request)
  }
}

private class OpenTabDispatchPiForkSource : AgentSessionSource, AgentSessionThreadOutlineForkSource {
  val forkCalls = ArrayList<OpenTabDispatchPiForkCall>()

  override val provider: AgentSessionProvider = AgentSessionProvider.from("pi")

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> = emptyList()

  override suspend fun loadThreadOutline(path: String, threadId: String, subAgentId: String?): AgentSessionThreadOutline? = null

  override fun canForkThreadFromOutlineItem(
    path: String,
    threadId: String,
    itemId: String,
    subAgentId: String?,
    tabKey: String?,
  ): Boolean {
    return path == "/work/project-a" &&
           threadId == "thread-outline-fork" &&
           itemId == "entry-fork" &&
           subAgentId == null &&
           tabKey != null
  }

  override suspend fun forkThreadFromOutlineItem(
    project: Project,
    path: String,
    threadId: String,
    itemId: String,
    subAgentId: String?,
    tabKey: String?,
  ): AgentSessionOutlineForkResult? {
    if (!canForkThreadFromOutlineItem(path, threadId, itemId, subAgentId, tabKey)) {
      return null
    }
    forkCalls += OpenTabDispatchPiForkCall(path, threadId, itemId, subAgentId, tabKey)
    return AgentSessionOutlineForkResult(
      thread = AgentSessionThread(
        id = "thread-outline-forked",
        title = "Forked Pi thread",
        updatedAt = 5_000L,
        archived = false,
        activityReport = AgentThreadActivityReport(AgentThreadActivity.PROCESSING),
        provider = AgentSessionProvider.from("pi"),
      ),
      launchSpecOverride = AgentSessionTerminalLaunchSpec(
        command = listOf("pi", "--session", "thread-outline-forked", "--from-outline-fork"),
        envVariables = mapOf("FORK_OVERRIDE" to "1"),
      ),
    )
  }
}

private data class OpenTabDispatchPiForkCall(
  @JvmField val path: String,
  @JvmField val threadId: String,
  @JvmField val itemId: String,
  @JvmField val subAgentId: String?,
  @JvmField val tabKey: String?,
)

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
