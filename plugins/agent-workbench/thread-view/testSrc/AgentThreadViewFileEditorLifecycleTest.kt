// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.buildAgentThreadIdentity
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.platform.ai.agent.sessions.core.launch.AGENT_SESSION_SURFACE_ACP
import com.intellij.platform.ai.agent.sessions.core.launch.AGENT_SESSION_SURFACE_TERMINAL
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionSurfaces
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageMode
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryChannel
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryStatus
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptRecord
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionMenuCommand
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionArchivedSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.platform.ai.agent.sessions.core.providers.AgentTerminalPromptDispatch
import com.intellij.platform.ai.agent.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorComposite
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorNavigatable
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Component
import java.awt.Container
import java.awt.event.KeyEvent
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.min

private val editorsToDispose = CopyOnWriteArrayList<AgentThreadViewFileEditor>()

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentThreadViewFileEditorLifecycleTest {
  @AfterEach
  fun tearDown() {
    editorsToDispose.asReversed().forEach { editor ->
      if (editor.isValid) {
        Disposer.dispose(editor)
      }
    }
    editorsToDispose.clear()
  }

  @Test
  fun editorTabActionGroupWrapperIsDumbAware() {
    val firstAction = DumbAwareAction.create("First") { }
    val secondAction = DumbAwareAction.create("Second") { }

    val emptyGroup = buildAgentThreadViewEditorTabActionGroup(emptyList())
    val wrappedSingleActionGroup = buildAgentThreadViewEditorTabActionGroup(listOf(firstAction))
    val existingGroup = object : DefaultActionGroup(firstAction), DumbAware {}
    val reusedGroup = buildAgentThreadViewEditorTabActionGroup(listOf(existingGroup))
    val wrappedMultiActionGroup = buildAgentThreadViewEditorTabActionGroup(listOf(firstAction, secondAction))

    assertThat(emptyGroup).isNull()
    assertThat(wrappedSingleActionGroup).isNotNull
    assertThat(checkNotNull(wrappedSingleActionGroup)).isInstanceOf(DumbAware::class.java)
    assertThat((wrappedSingleActionGroup as DefaultActionGroup).getChildActionsOrStubs())
      .containsExactly(firstAction)
    assertThat(reusedGroup).isSameAs(existingGroup)
    assertThat(wrappedMultiActionGroup).isNotNull
    assertThat(checkNotNull(wrappedMultiActionGroup)).isInstanceOf(DumbAware::class.java)
    assertThat((wrappedMultiActionGroup as DefaultActionGroup).getChildActionsOrStubs())
      .containsExactly(firstAction, secondAction)
  }

  @Test
  fun terminalTitleRebindsPendingTabToObservedThreadId() {
    val threadId = "018f4b30-f1b2-7000-9b4d-abcdef123456"
    val file = pendingTestFile()
    val title = TerminalTitle()
    val snapshotWriter = RecordingSnapshotWriter()
    val requests = mutableListOf<AgentThreadViewPendingTabRebindRequest>()
    val refreshThreadIds = mutableListOf<String?>()
    val controllerScope = unconfinedTestScope()
    val controller = AgentThreadViewTerminalTitleThreadRebindController(
      file = file,
      contributor = terminalTitleThreadRebindContributor(),
      tabSnapshotWriter = snapshotWriter,
      rebindPendingTabs = { provider, requestsByPath ->
        assertThat(provider.value).isEqualTo(AgentSessionProvider.from("codex").value)
        val request = requestsByPath.getValue(file.projectPath).single()
        requests += request
        file.rebindPendingThread(
          threadIdentity = request.target.threadIdentity,
          threadId = request.target.threadId,
          threadTitle = request.target.threadTitle,
          threadActivity = request.target.threadActivity,
        )
        AgentThreadViewPendingTabRebindReport(
          requestedBindings = 1,
          reboundBindings = 1,
          reboundFiles = 1,
          updatedPresentations = 1,
          outcomesByPath = emptyMap(),
        )
      },
      notifyRefresh = { _, _, refreshedThreadId, _, _ ->
        refreshThreadIds += refreshedThreadId
      },
    )

    try {
      controller.attach(terminalTitle = title, parentScope = controllerScope)
      title.change { applicationTitle = terminalTitle(threadId) }

      assertThat(requests).hasSize(1)
      val request = requests.single()
      assertThat(request.pendingTabKey).isEqualTo(file.tabKey)
      assertThat(request.pendingThreadIdentity).isEqualTo("codex:new-thread")
      assertThat(request.target.threadIdentity).isEqualTo("codex:$threadId")
      assertThat(request.target.threadId).isEqualTo(threadId)
      assertThat(file.threadIdentity).isEqualTo("codex:$threadId")
      assertThat(file.threadId).isEqualTo(threadId)
      assertThat(file.isPendingThread).isFalse()
      assertThat(snapshotWriter.snapshots.single().identity.threadIdentity).isEqualTo("codex:$threadId")
      assertThat(refreshThreadIds).containsExactly(threadId)

      title.change { applicationTitle = terminalTitle(threadId) }

      assertThat(requests).hasSize(1)
    }
    finally {
      controller.dispose()
      controllerScope.cancel()
    }
  }

  @Test
  fun terminalTitleRebindRetriesPendingTabWhenFirstAttemptDoesNotRebind() {
    val threadId = "018f4b30-f1b2-7000-9b4d-abcdef123456"
    val file = pendingTestFile()
    val snapshotWriter = RecordingSnapshotWriter()
    val requests = mutableListOf<AgentThreadViewPendingTabRebindRequest>()
    val refreshThreadIds = mutableListOf<String?>()
    var attempts = 0
    val controllerScope = unconfinedTestScope()
    val controller = AgentThreadViewTerminalTitleThreadRebindController(
      file = file,
      contributor = terminalTitleThreadRebindContributor(),
      tabSnapshotWriter = snapshotWriter,
      rebindPendingTabs = { _, requestsByPath ->
        val request = requestsByPath.getValue(file.projectPath).single()
        requests += request
        attempts++
        if (attempts == 1) {
          pendingRebindReport(file.projectPath, request, AgentThreadViewPendingTabRebindStatus.PENDING_TAB_NOT_OPEN)
        }
        else {
          file.rebindPendingThread(
            threadIdentity = request.target.threadIdentity,
            threadId = request.target.threadId,
            threadTitle = request.target.threadTitle,
            threadActivity = request.target.threadActivity,
          )
          pendingRebindReport(file.projectPath, request, AgentThreadViewPendingTabRebindStatus.REBOUND)
        }
      },
      notifyRefresh = { _, _, refreshedThreadId, _, _ ->
        refreshThreadIds += refreshedThreadId
      },
    )

    try {
      assertThat(controller.bindFromApplicationTitle(terminalTitle(threadId), controllerScope)).isTrue()

      assertThat(requests).hasSize(1)
      assertThat(file.isPendingThread).isTrue()
      assertThat(snapshotWriter.snapshots).isEmpty()
      assertThat(refreshThreadIds).containsExactly(threadId)

      assertThat(controller.bindFromApplicationTitle(terminalTitle(threadId), controllerScope)).isTrue()

      assertThat(requests).hasSize(2)
      assertThat(file.threadIdentity).isEqualTo("codex:$threadId")
      assertThat(file.threadId).isEqualTo(threadId)
      assertThat(file.isPendingThread).isFalse()
      assertThat(snapshotWriter.snapshots.single().identity.threadIdentity).isEqualTo("codex:$threadId")
      assertThat(refreshThreadIds).containsExactly(threadId, threadId)
    }
    finally {
      controller.dispose()
      controllerScope.cancel()
    }
  }

  @Test
  fun terminalTitleRebindKeepsPendingPostStartInitialMessageDispatch() {
    val threadId = "018f4b30-f1b2-7000-9b4d-abcdef123456"
    val initialMessage = "Refactor selected code"
    val file = pendingTestFile()
    file.updateInitialPromptDelivery(
      promptRecord = AgentInitialPromptRecord(
        message = initialMessage,
        mode = AgentInitialMessageMode.PLAN,
        token = "token-1",
        deliveryStatus = AgentInitialPromptDeliveryStatus.PENDING,
        deliveryChannel = AgentInitialPromptDeliveryChannel.APP_SERVER,
      ),
      terminalDispatch = AgentTerminalPromptDispatch(
        steps = codexPlanDispatchSteps(initialMessage),
        stepIndex = 0,
      ),
    )
    val title = TerminalTitle()
    val snapshotWriter = RecordingSnapshotWriter()
    val controllerScope = unconfinedTestScope()
    val controller = AgentThreadViewTerminalTitleThreadRebindController(
      file = file,
      contributor = terminalTitleThreadRebindContributor(),
      tabSnapshotWriter = snapshotWriter,
      rebindPendingTabs = { _, requestsByPath ->
        val request = requestsByPath.getValue(file.projectPath).single()
        file.rebindPendingThread(
          threadIdentity = request.target.threadIdentity,
          threadId = request.target.threadId,
          threadTitle = request.target.threadTitle,
          threadActivity = request.target.threadActivity,
        )
        AgentThreadViewPendingTabRebindReport(
          requestedBindings = 1,
          reboundBindings = 1,
          reboundFiles = 1,
          updatedPresentations = 1,
          outcomesByPath = emptyMap(),
        )
      },
    )

    try {
      controller.attach(terminalTitle = title, parentScope = controllerScope)
      title.change { applicationTitle = terminalTitle(threadId) }

      assertThat(file.threadIdentity).isEqualTo("codex:$threadId")
      assertThat(file.hasPendingInitialMessageForDispatch()).isTrue()
      assertThat(file.initialMessageToken).isEqualTo("token-1")
      assertThat(file.initialComposedMessage).isEqualTo(initialMessage)
      val snapshot = snapshotWriter.snapshots.single()
      assertThat(snapshot.identity.threadIdentity).isEqualTo("codex:$threadId")
      assertThat(snapshot.runtime.initialMessageDispatchSteps.map { it.action }).containsExactly(
        AgentInitialMessageDispatchAction.PROVIDER,
      )
      assertThat(snapshot.runtime.initialMessageDispatchSteps.map { it.text }).containsExactly(initialMessage)
      assertThat(snapshot.runtime.initialMessageToken).isEqualTo("token-1")
      assertThat(snapshot.runtime.initialMessageSent).isFalse()
    }
    finally {
      controller.dispose()
      controllerScope.cancel()
    }
  }

  @Test
  fun suppressedStartupCommandDispatchKeepsDeliveredInitialPromptBeforeRebindSnapshot() {
    val initialMessage = "Refactor selected code"
    val file = pendingTestFile()
    file.setStartupLaunchSpecOverride(
      AgentSessionTerminalLaunchSpec(command = listOf("codex", "--", initialMessage)),
      suppressInitialMessageDispatch = true,
    )
    file.updateInitialMessageMetadata(
      initialMessageDispatchSteps = listOf(
        AgentInitialMessageDispatchStep(
          text = initialMessage,
          timeoutPolicy = AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK,
        )
      ),
      initialMessageDispatchStepIndex = 0,
      initialMessageToken = "token-startup",
      initialMessageSent = false,
    )
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.tab.sentTexts).isEmpty()
    assertThat(file.hasPendingInitialMessageForDispatch()).isFalse()
    assertThat(file.initialMessageDispatchSteps).isEmpty()
    assertThat(file.initialComposedMessage).isEqualTo(initialMessage)
    assertThat(file.initialMessageToken).isEqualTo("token-startup")
    assertThat(file.initialMessageSent).isTrue()
    val runtime = file.toSnapshot().runtime
    assertThat(runtime.terminalPromptDispatch).isNull()
    assertThat(runtime.initialPromptRecord?.message).isEqualTo(initialMessage)
    assertThat(runtime.initialPromptRecord?.token).isEqualTo("token-startup")
    assertThat(runtime.initialPromptRecord?.deliveryStatus).isEqualTo(AgentInitialPromptDeliveryStatus.DELIVERED)
    assertThat(runtime.initialPromptRecord?.deliveryChannel).isEqualTo(AgentInitialPromptDeliveryChannel.STARTUP_COMMAND)
  }

  @Test
  fun startupLaunchSpecOverrideInitializesTerminalWithoutResolvingNewSessionIntent() {
    val preallocatedSessionId = "terminal-session-1"
    val startupLaunchSpec = AgentSessionTerminalLaunchSpec(
      command = emptyList(),
      useTerminalDefaultShell = true,
      preallocatedSessionId = preallocatedSessionId,
    )
    val file = testFile(
      threadIdentity = buildAgentThreadIdentity(AgentSessionProvider.from("terminal").value, preallocatedSessionId),
      shellCommand = emptyList(),
    )
    file.updateStartupIntent(
      AgentThreadViewStartupIntent.NewSession(
        provider = AgentSessionProvider.from("terminal"),
        launchMode = AgentSessionLaunchMode.STANDARD,
      )
    )
    file.setStartupLaunchSpecOverride(startupLaunchSpec)
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.lastStartupLaunchSpec).isEqualTo(startupLaunchSpec)
    assertThat(file.startupIntent()).isNull()
  }

  @Test
  fun deferredStartStateRendersCustomContentUntilReadyToStart() {
    val startupLaunchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex", "resume", "thread-1"))
    val file = testFile()
    file.setStartupLaunchSpecOverride(startupLaunchSpec)
    file.updateDeferredStartState(
      AgentThreadViewDeferredStartState(
        phase = AgentThreadViewDeferredStartPhase.WAITING,
        title = "Waiting",
      )
    )
    val customContent = JPanel()
    val focusComponent = JButton("Prompt")
    var disposed = false
    file.replaceDeferredStartContent(
      AgentThreadViewDeferredStartContent(
        component = customContent,
        preferredFocusedComponent = focusComponent,
        disposeContent = { disposed = true },
      )
    )
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()

    assertThat(editor.component.components).containsExactly(customContent)
    assertThat(editor.preferredFocusedComponent).isSameAs(focusComponent)
    assertThat(terminalTabs.createCalls).isZero()
    assertThat(disposed).isFalse()

    file.updateDeferredStartState(
      AgentThreadViewDeferredStartState(
        phase = AgentThreadViewDeferredStartPhase.READY_TO_START,
        title = "Ready",
      )
    )
    editor.refreshForFileStateChange()

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.lastStartupLaunchSpec).isEqualTo(startupLaunchSpec)
    assertThat(disposed).isTrue()
    assertThat(editor.preferredFocusedComponent).isSameAs(terminalTabs.tab.preferredFocusableComponent)
  }

  @Test
  fun providerCustomContentWaitsForDeferredStartStateToBecomeReady() {
    val provider = AgentSessionProvider.from("custom")
    val file = testFile(
      threadIdentity = buildAgentThreadIdentity(provider.value, "new-thread"),
      shellCommand = emptyList(),
    ).also { file ->
      file.updateThreadId("new-thread")
      file.updateDeferredStartState(
        AgentThreadViewDeferredStartState(
          phase = AgentThreadViewDeferredStartPhase.WAITING,
          title = "Waiting",
        )
      )
    }
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val customContent = JPanel()
    val createdThreadIds = ArrayList<String>()
    val resolvedContexts = ArrayList<AgentThreadViewContentContext>()
    val customContentProvider = object : AgentThreadViewCustomContentProvider {
      override val provider: AgentSessionProvider = provider

      override fun createComponent(project: Project, threadIdentity: String, threadId: String, parent: Disposable): JComponent {
        createdThreadIds += threadId
        return customContent
      }
    }
    val editor = testEditor(
      file = file,
      terminalTabs = terminalTabs,
      customContentProviderResolver = { candidate ->
        resolvedContexts += candidate
        if (candidate.provider == provider) customContentProvider else null
      },
    )

    editor.selectNotify()

    assertThat(createdThreadIds).isEmpty()
    assertThat(editor.component.components).doesNotContain(customContent)
    assertThat(terminalTabs.createCalls).isZero()

    file.updateDeferredStartState(
      AgentThreadViewDeferredStartState(
        phase = AgentThreadViewDeferredStartPhase.READY_TO_START,
        title = "Ready",
      )
    )
    editor.refreshForFileStateChange()

    assertThat(resolvedContexts.single().surfaceId).isEqualTo(AgentSessionSurfaces.TERMINAL)
    assertThat(createdThreadIds).containsExactly("new-thread")
    assertThat(editor.component.components).containsExactly(customContent)
    assertThat(file.deferredStartState).isNull()
    assertThat(terminalTabs.createCalls).isZero()
  }

  @Test
  fun providerCustomContentSuppliesPreferredFocusedComponent() {
    val provider = AgentSessionProvider.from("custom")
    val file = testFile(
      threadIdentity = buildAgentThreadIdentity(provider.value, "thread-1"),
      shellCommand = emptyList(),
    ).also { file ->
      file.updateThreadId("thread-1")
    }
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val inputComponent = JTextArea()
    val customContent = object : JPanel(), AgentThreadViewPreferredFocusableContent {
      override val preferredFocusedComponent: JComponent = inputComponent
    }
    val customContentProvider = object : AgentThreadViewCustomContentProvider {
      override val provider: AgentSessionProvider = provider

      override fun createComponent(project: Project, threadIdentity: String, threadId: String, parent: Disposable): JComponent {
        return customContent
      }
    }
    val editor = testEditor(
      file = file,
      terminalTabs = terminalTabs,
      customContentProviderResolver = { candidate ->
        if (candidate.provider == provider) customContentProvider else null
      },
    )

    editor.selectNotify()

    assertThat(editor.component.components).containsExactly(customContent)
    assertThat(editor.preferredFocusedComponent).isSameAs(inputComponent)
    assertThat(terminalTabs.createCalls).isZero()
  }

  @Test
  fun customContentResolverUsesSurfaceContext() {
    val provider = AgentSessionProvider.from("acp")
    val customContent = JPanel()
    val terminalFile = testFile(
      threadIdentity = buildAgentThreadIdentity(provider.value, "terminal-thread"),
      shellCommand = emptyList(),
    ).also { file ->
      file.updateThreadId("terminal-thread")
      file.updateSurfaceId(AGENT_SESSION_SURFACE_TERMINAL)
      file.setStartupLaunchSpecOverride(AgentSessionTerminalLaunchSpec(command = listOf("acp", "resume", "terminal-thread")))
    }
    val acpFile = testFile(
      threadIdentity = buildAgentThreadIdentity(provider.value, "acp-thread"),
      shellCommand = emptyList(),
    ).also { file ->
      file.updateThreadId("acp-thread")
      file.updateSurfaceId(AGENT_SESSION_SURFACE_ACP)
    }
    val customContentProvider = object : AgentThreadViewCustomContentProvider {
      override val provider: AgentSessionProvider = provider

      override fun handles(context: AgentThreadViewContentContext): Boolean {
        return context.provider == provider && context.surfaceId == AgentSessionSurfaces.ACP
      }

      override fun createComponent(project: Project, threadIdentity: String, threadId: String, parent: Disposable): JComponent {
        return customContent
      }
    }

    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val terminalEditor = testEditor(
      file = terminalFile,
      terminalTabs = terminalTabs,
      customContentProviderResolver = { context -> customContentProvider.takeIf { it.handles(context) } },
    )
    terminalEditor.selectNotify()

    assertThat(terminalEditor.component.components).doesNotContain(customContent)
    assertThat(terminalTabs.createCalls).isEqualTo(1)

    val acpTerminalTabs = FakeAgentThreadViewTerminalTabs()
    val acpEditor = testEditor(
      file = acpFile,
      terminalTabs = acpTerminalTabs,
      customContentProviderResolver = { context -> customContentProvider.takeIf { it.handles(context) } },
    )
    acpEditor.selectNotify()

    assertThat(acpEditor.component.components).containsExactly(customContent)
    assertThat(acpTerminalTabs.createCalls).isZero()
  }

  @Test
  fun defaultDeferredStartWaitingStateUsesCenteredDelayedProgress() {
    val file = testFile()
    file.updateDeferredStartState(
      AgentThreadViewDeferredStartState(
        phase = AgentThreadViewDeferredStartPhase.WAITING,
        title = "Starting new thread…",
      )
    )
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()

    editor.component.setSize(600, 400)
    layoutRecursively(editor.component)
    val progressIcon = collectAgentThreadViewStartProgressComponents(editor.component).single()
    val title = collectComponentsOfType(editor.component, JTextArea::class.java).single()
    assertThat(title.text).isEqualTo("Starting new thread…")
    assertThat(title.font.isBold).isFalse()
    assertThat(progressIcon.isVisible).isFalse()
    assertThat(abs(yCenterInRoot(title, editor.component) - editor.component.height / 2)).isLessThan(48)
    assertThat(abs(xCenterInRoot(title, editor.component) - editor.component.width / 2)).isLessThan(96)

    waitForCondition(timeoutMs = 1_000) { progressIcon.isVisible }
    assertThat(terminalTabs.createCalls).isZero()
  }

  @Test
  fun defaultDeferredStartFailureStateDoesNotShowProgressIcon() {
    val file = testFile()
    file.updateDeferredStartState(
      AgentThreadViewDeferredStartState(
        phase = AgentThreadViewDeferredStartPhase.FAILURE_NO_START,
        title = "Couldn't start agent",
        message = "Try again.",
      )
    )
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()

    assertThat(collectComponentsOfType(editor.component, AsyncProcessIcon::class.java)).isEmpty()
    val messages = collectComponentsOfType(editor.component, JTextArea::class.java)
    assertThat(messages.map { it.text })
      .containsExactly("Couldn't start agent", "Try again.")
    assertThat(messages.first().font.isBold).isFalse()
    assertThat(messages.last().foreground).isEqualTo(UIUtil.getContextHelpForeground())
    assertThat(terminalTabs.createCalls).isZero()
  }

  @Test
  fun restartForFileStateChangeReplacesRetainedTerminalWithNewLaunchSpec() {
    val project = testProject()
    val file = testFile()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val liveTerminalStore = AgentThreadViewLiveTerminalStore()
    val editor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = TestAgentThreadViewLiveTerminalRegistry(project = project, store = liveTerminalStore),
    )

    editor.selectNotify()

    val firstTab = terminalTabs.createdTabs.single()
    val restartLaunchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex", "resume", "thread-fork"))

    runBlocking(Dispatchers.Default) {
      val replaced = editor.restartForFileStateChange(
        startupLaunchSpec = restartLaunchSpec,
        replaceRetainedTerminal = true,
      )

      assertThat(replaced).isTrue()
    }

    assertThat(terminalTabs.createCalls).isEqualTo(2)
    assertThat(terminalTabs.closeCalls).isEqualTo(1)
    assertThat(terminalTabs.createdTabs).hasSize(2)
    assertThat(terminalTabs.createdTabs[0]).isSameAs(firstTab)
    assertThat(terminalTabs.createdTabs[1]).isNotSameAs(firstTab)
    assertThat(terminalTabs.lastStartupLaunchSpec).isEqualTo(restartLaunchSpec)
    assertThat(editor.preferredFocusedComponent).isSameAs(terminalTabs.createdTabs[1].preferredFocusableComponent)
  }

  @Test
  fun terminalTitleRebindDoesNotPersistClearedStartupCommandFallback() {
    val threadId = "018f4b30-f1b2-7000-9b4d-abcdef123456"
    val file = pendingTestFile()
    file.updateInitialMessageMetadata(
      initialMessageDispatchSteps = listOf(
        AgentInitialMessageDispatchStep(
          text = "Refactor selected code",
          timeoutPolicy = AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK,
        )
      ),
      initialMessageDispatchStepIndex = 0,
      initialMessageToken = "token-startup",
      initialMessageSent = false,
    )
    file.clearInitialMessageDispatchMetadata()
    val title = TerminalTitle()
    val snapshotWriter = RecordingSnapshotWriter()
    val controllerScope = unconfinedTestScope()
    val controller = AgentThreadViewTerminalTitleThreadRebindController(
      file = file,
      contributor = terminalTitleThreadRebindContributor(),
      tabSnapshotWriter = snapshotWriter,
      rebindPendingTabs = { _, requestsByPath ->
        val request = requestsByPath.getValue(file.projectPath).single()
        file.rebindPendingThread(
          threadIdentity = request.target.threadIdentity,
          threadId = request.target.threadId,
          threadTitle = request.target.threadTitle,
          threadActivity = request.target.threadActivity,
        )
        AgentThreadViewPendingTabRebindReport(
          requestedBindings = 1,
          reboundBindings = 1,
          reboundFiles = 1,
          updatedPresentations = 1,
          outcomesByPath = emptyMap(),
        )
      },
    )

    try {
      controller.attach(terminalTitle = title, parentScope = controllerScope)
      title.change { applicationTitle = terminalTitle(threadId) }

      val snapshot = snapshotWriter.snapshots.single()
      assertThat(snapshot.identity.threadIdentity).isEqualTo("codex:$threadId")
      assertThat(snapshot.runtime.initialMessageDispatchSteps).isEmpty()
      assertThat(snapshot.runtime.initialMessageToken).isNull()
      assertThat(snapshot.runtime.initialMessageSent).isFalse()
    }
    finally {
      controller.dispose()
      controllerScope.cancel()
    }
  }

  @Test
  fun terminalTitleRebindsConcreteTabAfterNewThreadCommand() {
    assertTerminalTitleRebindsConcreteTabAfterConcreteThreadCommand("Fresh /new thread")
  }

  @Test
  fun terminalTitleRebindsConcreteTabAfterForkCommand() {
    assertTerminalTitleRebindsConcreteTabAfterConcreteThreadCommand("Fresh /fork thread")
  }

  private fun assertTerminalTitleRebindsConcreteTabAfterConcreteThreadCommand(threadTitle: String) {
    val threadId = "018f4b30-f1b2-7000-9b4d-abcdef123456"
    val file = testFile()
    file.updateNewThreadRebindRequestedAtMs(2_000L)
    val title = TerminalTitle()
    val snapshotWriter = RecordingSnapshotWriter()
    val requests = mutableListOf<AgentThreadViewConcreteTabRebindRequest>()
    val refreshSignals = mutableListOf<Pair<String?, String?>>()
    val controllerScope = unconfinedTestScope()
    val controller = AgentThreadViewTerminalTitleThreadRebindController(
      file = file,
      contributor = terminalTitleThreadRebindContributor(),
      tabSnapshotWriter = snapshotWriter,
      rebindConcreteTabs = { provider, requestsByPath ->
        assertThat(provider.value).isEqualTo(AgentSessionProvider.from("codex").value)
        val request = requestsByPath.getValue(file.projectPath).single()
        requests += request
        file.rebindConcreteThread(
          threadIdentity = request.target.threadIdentity,
          threadId = request.target.threadId,
          threadTitle = request.target.threadTitle,
          threadActivity = request.target.threadActivity,
        )
        AgentThreadViewConcreteTabRebindReport(
          requestedBindings = 1,
          reboundBindings = 1,
          reboundFiles = 1,
          updatedPresentations = 1,
          outcomesByPath = emptyMap(),
        )
      },
      notifyRefresh = { _, _, refreshedThreadId, refreshedThreadTitle, _ ->
        refreshSignals += refreshedThreadId to refreshedThreadTitle
      },
      currentTimeProvider = { 2_100L },
    )

    try {
      controller.attach(terminalTitle = title, parentScope = controllerScope)
      title.change { applicationTitle = terminalTitle(threadId, threadTitle) }

      assertThat(requests).hasSize(1)
      val request = requests.single()
      assertThat(request.tabKey).isEqualTo(file.tabKey)
      assertThat(request.currentThreadIdentity).isEqualTo("CODEX:thread-1")
      assertThat(request.newThreadRebindRequestedAtMs).isEqualTo(2_000L)
      assertThat(request.target.threadIdentity).isEqualTo("codex:$threadId")
      assertThat(request.target.threadTitle).isEqualTo(threadTitle)
      assertThat(file.threadIdentity).isEqualTo("codex:$threadId")
      assertThat(file.threadId).isEqualTo(threadId)
      assertThat(file.threadTitle).isEqualTo(threadTitle)
      assertThat(file.newThreadRebindRequestedAtMs).isNull()
      assertThat(snapshotWriter.snapshots.single().identity.threadIdentity).isEqualTo("codex:$threadId")
      assertThat(refreshSignals).containsExactly(threadId to threadTitle)
    }
    finally {
      controller.dispose()
      controllerScope.cancel()
    }
  }

  @Test
  fun terminalTitleRebindCompletesWhenConcreteTabRestartDisposesController() {
    val threadId = "018f4b30-f1b2-7000-9b4d-abcdef123456"
    val threadTitle = "Fresh /new thread"
    val file = testFile()
    file.updateNewThreadRebindRequestedAtMs(2_000L)
    val snapshotWriter = RecordingSnapshotWriter()
    val requests = mutableListOf<AgentThreadViewConcreteTabRebindRequest>()
    val refreshSignals = mutableListOf<Pair<String?, String?>>()
    val controllerScope = unconfinedTestScope()
    lateinit var controller: AgentThreadViewTerminalTitleThreadRebindController
    controller = AgentThreadViewTerminalTitleThreadRebindController(
      file = file,
      contributor = terminalTitleThreadRebindContributor(),
      tabSnapshotWriter = snapshotWriter,
      rebindConcreteTabs = { _, requestsByPath ->
        val request = requestsByPath.getValue(file.projectPath).single()
        requests += request
        file.rebindConcreteThread(
          threadIdentity = request.target.threadIdentity,
          threadId = request.target.threadId,
          threadTitle = request.target.threadTitle,
          threadActivity = request.target.threadActivity,
        )
        controller.dispose()
        withContext(Dispatchers.Default) {}
        concreteRebindReport(file.projectPath, request, AgentThreadViewConcreteTabRebindStatus.REBOUND)
      },
      notifyRefresh = { _, _, refreshedThreadId, refreshedThreadTitle, _ ->
        refreshSignals += refreshedThreadId to refreshedThreadTitle
      },
      currentTimeProvider = { 2_100L },
    )

    try {
      assertThat(controller.bindFromApplicationTitle(terminalTitle(threadId, threadTitle), controllerScope)).isTrue()

      waitForCondition { snapshotWriter.snapshots.isNotEmpty() && refreshSignals.isNotEmpty() }

      assertThat(requests).hasSize(1)
      assertThat(file.threadIdentity).isEqualTo("codex:$threadId")
      assertThat(file.threadTitle).isEqualTo(threadTitle)
      assertThat(snapshotWriter.snapshots.single().identity.threadIdentity).isEqualTo("codex:$threadId")
      assertThat(refreshSignals).containsExactly(threadId to threadTitle)
    }
    finally {
      controller.dispose()
      controllerScope.cancel()
    }
  }

  @Test
  fun terminalTitleRebindRetriesConcreteTabAfterNewThreadCommandWhenFirstAttemptDoesNotRebind() {
    val threadId = "018f4b30-f1b2-7000-9b4d-abcdef123456"
    val file = testFile()
    file.updateNewThreadRebindRequestedAtMs(2_000L)
    val snapshotWriter = RecordingSnapshotWriter()
    val requests = mutableListOf<AgentThreadViewConcreteTabRebindRequest>()
    val refreshThreadIds = mutableListOf<String?>()
    var attempts = 0
    val controllerScope = unconfinedTestScope()
    val controller = AgentThreadViewTerminalTitleThreadRebindController(
      file = file,
      contributor = terminalTitleThreadRebindContributor(),
      tabSnapshotWriter = snapshotWriter,
      rebindConcreteTabs = { _, requestsByPath ->
        val request = requestsByPath.getValue(file.projectPath).single()
        requests += request
        attempts++
        if (attempts == 1) {
          concreteRebindReport(file.projectPath, request, AgentThreadViewConcreteTabRebindStatus.CONCRETE_TAB_NOT_OPEN)
        }
        else {
          file.rebindConcreteThread(
            threadIdentity = request.target.threadIdentity,
            threadId = request.target.threadId,
            threadTitle = request.target.threadTitle,
            threadActivity = request.target.threadActivity,
          )
          concreteRebindReport(file.projectPath, request, AgentThreadViewConcreteTabRebindStatus.REBOUND)
        }
      },
      notifyRefresh = { _, _, refreshedThreadId, _, _ ->
        refreshThreadIds += refreshedThreadId
      },
      currentTimeProvider = { 2_100L },
    )

    try {
      assertThat(controller.bindFromApplicationTitle(terminalTitle(threadId), controllerScope)).isTrue()

      assertThat(requests).hasSize(1)
      assertThat(file.threadIdentity).isEqualTo("CODEX:thread-1")
      assertThat(file.threadId).isEqualTo("thread-1")
      assertThat(file.newThreadRebindRequestedAtMs).isEqualTo(2_000L)
      assertThat(snapshotWriter.snapshots).isEmpty()
      assertThat(refreshThreadIds).containsExactly(threadId)

      assertThat(controller.bindFromApplicationTitle(terminalTitle(threadId), controllerScope)).isTrue()

      assertThat(requests).hasSize(2)
      assertThat(file.threadIdentity).isEqualTo("codex:$threadId")
      assertThat(file.threadId).isEqualTo(threadId)
      assertThat(file.newThreadRebindRequestedAtMs).isNull()
      assertThat(snapshotWriter.snapshots.single().identity.threadIdentity).isEqualTo("codex:$threadId")
      assertThat(refreshThreadIds).containsExactly(threadId, threadId)
    }
    finally {
      controller.dispose()
      controllerScope.cancel()
    }
  }

  @Test
  fun terminalTitleDoesNotRebindConcreteTabAfterNewThreadCommandAnchorExpires() {
    val threadId = "018f4b30-f1b2-7000-9b4d-abcdef123456"
    val file = testFile()
    file.updateNewThreadRebindRequestedAtMs(2_000L)
    val title = TerminalTitle()
    val snapshotWriter = RecordingSnapshotWriter()
    val refreshThreadIds = mutableListOf<String?>()
    val controllerScope = unconfinedTestScope()
    val controller = AgentThreadViewTerminalTitleThreadRebindController(
      file = file,
      contributor = terminalTitleThreadRebindContributor(),
      tabSnapshotWriter = snapshotWriter,
      rebindConcreteTabs = { _, _ ->
        error("Expired /new anchor must not rebind a concrete threadView tab")
      },
      notifyRefresh = { _, _, refreshedThreadId, _, _ ->
        refreshThreadIds += refreshedThreadId
      },
      currentTimeProvider = { 2_000L + AgentSessionThreadRebindPolicy.CONCRETE_NEW_THREAD_REBIND_MAX_AGE_MS },
    )

    try {
      controller.attach(terminalTitle = title, parentScope = controllerScope)
      title.change { applicationTitle = terminalTitle(threadId) }

      assertThat(file.threadIdentity).isEqualTo("CODEX:thread-1")
      assertThat(file.threadId).isEqualTo("thread-1")
      assertThat(file.newThreadRebindRequestedAtMs).isEqualTo(2_000L)
      assertThat(snapshotWriter.snapshots).isEmpty()
      assertThat(refreshThreadIds).isEmpty()
    }
    finally {
      controller.dispose()
      controllerScope.cancel()
    }
  }

  @Test
  fun preferredFocusedComponentDoesNotStartTerminalInitialization() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val editor = testEditor(terminalTabs = terminalTabs)

    val preferred = editor.preferredFocusedComponent

    assertThat(preferred).isSameAs(editor.component)
    assertThat(terminalTabs.createCalls).isEqualTo(0)
  }

  @Test
  fun selectNotifyWaitsUntilEditorComponentIsShown() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val editor = testEditor(terminalTabs = terminalTabs, showComponent = false)

    editor.selectNotify()
    editor.selectNotify()

    assertThat(terminalTabs.createCalls).isEqualTo(0)
    assertThat(editor.preferredFocusedComponent).isSameAs(editor.component)

    editor.showComponentForTests()

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(editor.preferredFocusedComponent).isSameAs(terminalTabs.tab.preferredFocusableComponent)
  }

  @Test
  fun selectNotifyFocusesTerminalAfterEditorComponentIsShown() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val editor = testEditor(terminalTabs = terminalTabs, showComponent = false)

    editor.selectNotify()

    assertThat(terminalTabs.createCalls).isEqualTo(0)
    assertThat(terminalTabs.tab.focusRequests).isEqualTo(0)

    editor.showComponentForTests()

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.tab.focusRequests).isEqualTo(1)
  }

  @Test
  fun pendingContextCanBeQueuedBeforeAsyncTerminalInitialization() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val editorScope = object : CoroutineScope {
      override val coroutineContext = Job() + PausedCoroutineDispatcher
    }
    val editor = testEditor(
      file = restoredConcreteTestFile(),
      terminalTabs = terminalTabs,
      editorCoroutineScope = editorScope,
    )

    try {
      val added = editor.addPendingContextItems(listOf(testContextItem()))

      assertThat(added).isTrue()
      assertThat(terminalTabs.createCalls).isEqualTo(0)
      assertThat(editor.pendingContextItemsForTests().map { it.title }).containsExactly("Queued.kt")
    }
    finally {
      editorScope.cancel()
    }
  }

  @Test
  fun nonRestorableFileProducesEmptyEditorState() {
    val file = pendingTestFile()
    file.updateRestoreOnRestart(false)
    val editor = testEditor(file = file)

    val state = editor.getState(FileEditorStateLevel.FULL) as AgentThreadViewFileEditorState

    assertThat(state.snapshot).isNull()
    assertThat(state.startupIntent).isNull()
  }

  @Test
  fun restoredArchivedThreadClosesWithoutStartingTerminal() {
    val file = restoredConcreteTestFile()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val closedFiles = CopyOnWriteArrayList<AgentThreadViewVirtualFile>()
    val descriptor = ArchivedThreadsProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      archivedThreads = listOf(
        AgentSessionThread(
          id = "thread-restored",
          title = "Restored thread",
          updatedAt = 1L,
          archived = true,
          activityReport = AgentThreadActivityReport(AgentThreadActivity.READY),
          provider = AgentSessionProvider.from("codex"),
        )
      ),
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      val editor = testEditor(
        file = file,
        terminalTabs = terminalTabs,
        archivedRestoreHandler = AgentThreadViewArchivedRestoreHandler { closedFile -> closedFiles += closedFile },
        providerDescriptorResolver = { provider -> if (provider == descriptor.provider) descriptor else null },
      )

      editor.selectNotify()
    }

    assertThat(terminalTabs.createCalls).isEqualTo(0)
    assertThat(closedFiles).containsExactly(file)
    assertThat(file.shouldRestoreOnRestart()).isFalse()
  }

  @Test
  fun editorShellCreationDoesNotResolveLiveTerminalRegistry() {
    val editor = AgentThreadViewFileEditor(
      project = testProject(),
      file = claudeLifecycleTestFile(),
      liveTerminalRegistry = object : AgentThreadViewLiveTerminalRegistry {
        override fun acquireOrCreate(
          file: AgentThreadViewVirtualFile,
          terminalTabs: AgentThreadViewTerminalTabs,
          startupLaunchSpec: AgentSessionTerminalLaunchSpec,
        ): AgentThreadViewTerminalTab {
          throw AssertionError("Live terminal registry must not be used while creating the editor shell")
        }

        override fun replace(
          file: AgentThreadViewVirtualFile,
          terminalTabs: AgentThreadViewTerminalTabs,
          startupLaunchSpec: AgentSessionTerminalLaunchSpec,
        ): AgentThreadViewTerminalTab {
          throw AssertionError("Live terminal registry must not be used while creating the editor shell")
        }
      },
    ).also(editorsToDispose::add)

    assertThat(editor.component).isNotNull
    assertThat(editor.preferredFocusedComponent).isSameAs(editor.component)
  }

  @Test
  fun selectNotifyResolvesLiveTerminalRegistryWhenTerminalIsInitialized() {
    val project = testProject()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val liveTerminalStore = AgentThreadViewLiveTerminalStore()
    val registryAcquisitions = AtomicLong()
    val editor = AgentThreadViewFileEditor(
      project = project,
      file = claudeLifecycleTestFile(),
      terminalTabs = terminalTabs,
      editorCoroutineScope = unconfinedTestScope(),
      liveTerminalRegistry = object : AgentThreadViewLiveTerminalRegistry {
        override fun acquireOrCreate(
          file: AgentThreadViewVirtualFile,
          terminalTabs: AgentThreadViewTerminalTabs,
          startupLaunchSpec: AgentSessionTerminalLaunchSpec,
        ): AgentThreadViewTerminalTab {
          registryAcquisitions.incrementAndGet()
          return liveTerminalStore.acquireOrCreate(project, file, terminalTabs, startupLaunchSpec)
        }

        override fun replace(
          file: AgentThreadViewVirtualFile,
          terminalTabs: AgentThreadViewTerminalTabs,
          startupLaunchSpec: AgentSessionTerminalLaunchSpec,
        ): AgentThreadViewTerminalTab {
          return liveTerminalStore.replace(project, file, terminalTabs, startupLaunchSpec)
        }
      },
    ).also(editorsToDispose::add)

    assertThat(registryAcquisitions.get()).isEqualTo(0)

    editor.showComponentForTests()
    editor.selectNotify()

    assertThat(registryAcquisitions.get()).isEqualTo(1)
    assertThat(terminalTabs.createCalls).isEqualTo(1)

    liveTerminalStore.dispose(project)
  }

  @Test
  fun deferredStartBlocksTerminalInitializationUntilReleased() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = testFile().also {
      it.updateDeferredStartState(
        AgentThreadViewDeferredStartState(
          phase = AgentThreadViewDeferredStartPhase.WAITING,
          title = "Preparing merge resolution",
          message = "Still preparing conflicts",
        )
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()

    assertThat(terminalTabs.createCalls).isEqualTo(0)
    assertThat(editor.preferredFocusedComponent).isSameAs(editor.component)

    file.updateDeferredStartState(AgentThreadViewDeferredStartState(AgentThreadViewDeferredStartPhase.READY_TO_START, title = ""))
    editor.refreshForFileStateChange()

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(editor.preferredFocusedComponent).isSameAs(terminalTabs.tab.preferredFocusableComponent)
  }

  @Test
  fun disposeKeepsInitializedTerminalTabAliveUntilFileClose() {
    val project = testProject()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentThreadViewLiveTerminalStore()
    val editor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = TestAgentThreadViewLiveTerminalRegistry(project, liveTerminalStore),
    )

    editor.selectNotify()
    Disposer.dispose(editor)
    Disposer.dispose(editor)

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isTrue()
    assertThat(terminalTabs.closeCalls).isEqualTo(0)

    liveTerminalStore.dispose(project)
  }

  @Test
  fun recreatedEditorReusesInitializedTerminalTab() {
    val project = testProject()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentThreadViewLiveTerminalStore()
    val liveTerminalRegistry = TestAgentThreadViewLiveTerminalRegistry(project, liveTerminalStore)
    val firstEditor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = liveTerminalRegistry,
    )

    firstEditor.selectNotify()
    Disposer.dispose(firstEditor)
    file.setStartupLaunchSpecOverride(AgentSessionTerminalLaunchSpec(command = listOf("claude", "--resume", "session-1")))

    val secondEditor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = liveTerminalRegistry,
    )
    secondEditor.selectNotify()

    assertThat(terminalTabs.closeCalls).isEqualTo(0)
    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isTrue()
    assertThat(secondEditor.preferredFocusedComponent).isSameAs(terminalTabs.tab.preferredFocusableComponent)

    Disposer.dispose(secondEditor)
    liveTerminalStore.dispose(project)
  }

  @Test
  fun fileClosedClosesInitializedTerminalTabWhenNoCopiesRemain() {
    val project = testProject()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentThreadViewLiveTerminalStore()
    val editor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = TestAgentThreadViewLiveTerminalRegistry(project, liveTerminalStore),
    )

    editor.selectNotify()
    Disposer.dispose(editor)
    liveTerminalStore.handleFileClosed(project, testFileEditorManager(isFileOpen = false), file)

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.closeCalls).isEqualTo(1)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isFalse()

    liveTerminalStore.dispose(project)
  }

  @Test
  fun fileClosedRecordsProviderTerminalSessionCloseWhenNoCopiesRemain() {
    val project = testProject()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentThreadViewLiveTerminalStore()
    val descriptor = RecordingTerminalSessionClosedProvider(AgentSessionProvider.from("claude"))

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      liveTerminalStore.acquireOrCreate(project = project, file = file, terminalTabs = terminalTabs)
      liveTerminalStore.handleFileClosed(project, testFileEditorManager(isFileOpen = false), file)
    }

    assertThat(descriptor.closedSessions).containsExactly(ClosedTerminalSession(file.projectPath, file.threadId))
  }

  @Test
  fun fileClosedKeepsInitializedTerminalTabWhenFileIsStillReportedOpen() {
    val project = testProject()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentThreadViewLiveTerminalStore()
    val editor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = TestAgentThreadViewLiveTerminalRegistry(project, liveTerminalStore),
    )

    editor.selectNotify()
    Disposer.dispose(editor)
    liveTerminalStore.handleFileClosed(project, testFileEditorManager(isFileOpen = true), file)

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.closeCalls).isEqualTo(0)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isTrue()

    liveTerminalStore.dispose(project)
  }

  @Test
  fun fileClosedDoesNotRecordProviderTerminalSessionCloseWhenFileIsStillOpen() {
    val project = testProject()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentThreadViewLiveTerminalStore()
    val descriptor = RecordingTerminalSessionClosedProvider(AgentSessionProvider.from("claude"))

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      liveTerminalStore.acquireOrCreate(project = project, file = file, terminalTabs = terminalTabs)
      liveTerminalStore.handleFileClosed(project, testFileEditorManager(isFileOpen = true), file)
    }

    assertThat(descriptor.closedSessions).isEmpty()

    liveTerminalStore.dispose(project)
  }

  @Test
  fun fileClosedKeepsInitializedTerminalTabWhenFileIsOpenInAnotherProject() {
    val sourceProject = testProject()
    val targetProject = testProject()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentThreadViewLiveTerminalStore { candidate, excludedProject ->
      if (candidate.tabKey == file.tabKey && excludedProject === sourceProject) targetProject else null
    }

    liveTerminalStore.acquireOrCreate(project = sourceProject, file = file, terminalTabs = terminalTabs)
    val closeResult = liveTerminalStore.handleFileClosed(sourceProject, testFileEditorManager(isFileOpen = false), file)

    assertThat(closeResult).isEqualTo(AgentThreadViewLiveTerminalCloseResult.KEPT_OPEN)
    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.closeCalls).isEqualTo(0)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isTrue()

    liveTerminalStore.dispose(targetProject)
  }

  @Test
  fun crossProjectRecreatedEditorReusesInitializedTerminalTab() {
    val dedicatedProject = testProject()
    val sourceProject = testProject()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentThreadViewLiveTerminalStore()

    val dedicatedTab = liveTerminalStore.acquireOrCreate(project = dedicatedProject, file = file, terminalTabs = terminalTabs)
    val sourceTab = liveTerminalStore.acquireOrCreate(project = sourceProject, file = file, terminalTabs = terminalTabs)

    assertThat(sourceTab).isSameAs(dedicatedTab)
    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.closeCalls).isEqualTo(0)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isTrue()

    liveTerminalStore.disposeProject(dedicatedProject)
    assertThat(terminalTabs.closeCalls).isEqualTo(0)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isTrue()

    val closeResult = liveTerminalStore.handleFileClosed(sourceProject, testFileEditorManager(isFileOpen = false), file)
    assertThat(closeResult).isEqualTo(AgentThreadViewLiveTerminalCloseResult.CLOSED)
    assertThat(terminalTabs.closeCalls).isEqualTo(1)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isFalse()

    liveTerminalStore.dispose(sourceProject)
  }

  @Test
  fun projectDisposalReassignsInitializedTerminalTabWhenFileIsOpenInAnotherProject() {
    val dedicatedProject = testProject()
    val sourceProject = testProject()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentThreadViewLiveTerminalStore { candidate, excludedProject ->
      if (candidate.tabKey == file.tabKey && excludedProject === dedicatedProject) sourceProject else null
    }

    liveTerminalStore.acquireOrCreate(project = dedicatedProject, file = file, terminalTabs = terminalTabs)
    liveTerminalStore.disposeProject(dedicatedProject)

    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.closeCalls).isEqualTo(0)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isTrue()

    val closeResult = liveTerminalStore.handleFileClosed(sourceProject, testFileEditorManager(isFileOpen = false), file)

    assertThat(closeResult).isEqualTo(AgentThreadViewLiveTerminalCloseResult.CLOSED)
    assertThat(terminalTabs.closeCalls).isEqualTo(1)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isFalse()

    liveTerminalStore.dispose(sourceProject)
  }

  @Test
  fun fileClosedDefersInitializedTerminalTabCloseWhileClosingToReopen() {
    val project = testProject()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentThreadViewLiveTerminalStore()
    val editor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = TestAgentThreadViewLiveTerminalRegistry(project, liveTerminalStore),
    )

    editor.selectNotify()
    Disposer.dispose(editor)
    file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, true)
    val closeResult = liveTerminalStore.handleFileClosed(project, testFileEditorManager(isFileOpen = false), file)

    assertThat(closeResult).isEqualTo(AgentThreadViewLiveTerminalCloseResult.DEFERRED)
    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.closeCalls).isEqualTo(0)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isTrue()
    assertThat(liveTerminalStore.isPendingClose(file.tabKey)).isTrue()

    file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, null)
    liveTerminalStore.dispose(project)
  }

  @Test
  fun pendingCloseConfirmationKeepsInitializedTerminalTabWhenFileReopens() {
    val project = testProject()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentThreadViewLiveTerminalStore()
    val editor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = TestAgentThreadViewLiveTerminalRegistry(project, liveTerminalStore),
    )
    val fileEditorManager = TestFileEditorManager(isFileOpen = false)

    editor.selectNotify()
    Disposer.dispose(editor)
    file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, true)
    val closeResult = liveTerminalStore.handleFileClosed(project, fileEditorManager, file)
    fileEditorManager.setFileOpen(true)
    liveTerminalStore.handleFileOpened(file)
    val confirmResult = liveTerminalStore.confirmPendingClose(project, fileEditorManager, file)

    assertThat(closeResult).isEqualTo(AgentThreadViewLiveTerminalCloseResult.DEFERRED)
    assertThat(confirmResult).isEqualTo(AgentThreadViewLiveTerminalCloseResult.KEPT_OPEN)
    assertThat(terminalTabs.closeCalls).isEqualTo(0)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isTrue()
    assertThat(liveTerminalStore.isPendingClose(file.tabKey)).isFalse()

    file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, null)
    liveTerminalStore.dispose(project)
  }

  @Test
  fun pendingCloseConfirmationClosesInitializedTerminalTabWhenReopenNeverArrives() {
    val project = testProject()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentThreadViewLiveTerminalStore()
    val editor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = TestAgentThreadViewLiveTerminalRegistry(project, liveTerminalStore),
    )
    val fileEditorManager = TestFileEditorManager(isFileOpen = false)

    editor.selectNotify()
    Disposer.dispose(editor)
    file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, true)
    val closeResult = liveTerminalStore.handleFileClosed(project, fileEditorManager, file)
    file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, null)
    val confirmResult = liveTerminalStore.confirmPendingClose(project, fileEditorManager, file)

    assertThat(closeResult).isEqualTo(AgentThreadViewLiveTerminalCloseResult.DEFERRED)
    assertThat(confirmResult).isEqualTo(AgentThreadViewLiveTerminalCloseResult.CLOSED)
    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.closeCalls).isEqualTo(1)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isFalse()
    assertThat(liveTerminalStore.isPendingClose(file.tabKey)).isFalse()

    liveTerminalStore.dispose(project)
  }

  @Test
  fun lastEditorCloseArchivesConcreteTerminalSessionOnly() {
    val terminalProvider = RecordingTerminalSessionClosedProvider(
      provider = AgentSessionProvider.from("terminal"),
      supportsArchiveThread = true,
      archiveOnLastEditorClose = true,
    )
    val codexProvider = RecordingTerminalSessionClosedProvider(
      provider = AgentSessionProvider.from("codex"),
      supportsArchiveThread = true,
    )
    val claudeProvider = RecordingTerminalSessionClosedProvider(provider = AgentSessionProvider.from("claude"))
    val registry = InMemoryAgentSessionProviderRegistry(listOf(terminalProvider, codexProvider, claudeProvider))

    AgentSessionProviders.withRegistryForTest(registry) {
      assertThat(shouldArchiveTerminalSessionOnLastEditorClose(terminalLifecycleTestFile()))
        .isTrue()
      assertThat(shouldArchiveTerminalSessionOnLastEditorClose(codexLifecycleTestFile()))
        .isFalse()
      assertThat(shouldArchiveTerminalSessionOnLastEditorClose(claudeLifecycleTestFile()))
        .isFalse()
      assertThat(shouldArchiveTerminalSessionOnLastEditorClose(pendingTestFile(provider = AgentSessionProvider.from("terminal"))))
        .isFalse()
    }
  }

  @Test
  fun acquireOrCreateClearsPendingCloseAndReusesInitializedTerminalTab() {
    val project = testProject()
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = claudeLifecycleTestFile()
    val liveTerminalStore = AgentThreadViewLiveTerminalStore()
    val liveTerminalRegistry = TestAgentThreadViewLiveTerminalRegistry(project, liveTerminalStore)
    val firstEditor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = liveTerminalRegistry,
    )

    firstEditor.selectNotify()
    Disposer.dispose(firstEditor)
    file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, true)
    val closeResult = liveTerminalStore.handleFileClosed(project, testFileEditorManager(isFileOpen = false), file)
    file.setStartupLaunchSpecOverride(AgentSessionTerminalLaunchSpec(command = listOf("claude", "--resume", "session-1")))

    val secondEditor = testEditor(
      project = project,
      file = file,
      terminalTabs = terminalTabs,
      liveTerminalRegistry = liveTerminalRegistry,
    )
    secondEditor.selectNotify()

    assertThat(closeResult).isEqualTo(AgentThreadViewLiveTerminalCloseResult.DEFERRED)
    assertThat(terminalTabs.createCalls).isEqualTo(1)
    assertThat(terminalTabs.closeCalls).isEqualTo(0)
    assertThat(liveTerminalStore.isTracked(file.tabKey)).isTrue()
    assertThat(liveTerminalStore.isPendingClose(file.tabKey)).isFalse()
    assertThat(secondEditor.preferredFocusedComponent).isSameAs(terminalTabs.tab.preferredFocusableComponent)

    Disposer.dispose(secondEditor)
    file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, null)
    liveTerminalStore.dispose(project)
  }

  @Test
  fun disposeWithoutInitializationDoesNotCloseTerminalTab() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val editor = testEditor(terminalTabs = terminalTabs)

    Disposer.dispose(editor)

    assertThat(terminalTabs.createCalls).isEqualTo(0)
    assertThat(terminalTabs.closeCalls).isEqualTo(0)
  }

  @Test
  fun selectNotifySendsInitialMessageOnce() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "Refactor selected code",
        initialMessageToken = "token-1",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    editor.selectNotify()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()

    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)

    waitForCondition { terminalTabs.tab.sentTexts.size == 1 && file.initialMessageSent }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("Refactor selected code", shouldExecute = true))
  }

  @Test
  fun flushPendingInitialMessageWaitsForRunningSessionState() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = testFile()
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    file.updateInitialMessageMetadata(
      initialComposedMessage = "Apply follow-up changes",
      initialMessageToken = "token-follow-up",
      initialMessageSent = false,
    )

    editor.flushPendingInitialMessageIfInitialized()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()

    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    editor.flushPendingInitialMessageIfInitialized()
    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("Apply follow-up changes", shouldExecute = true))
  }

  @Test
  fun disposeBeforeSessionRunningSkipsInitialMessageSend() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "Generate tests",
        initialMessageToken = "token-dispose",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    Disposer.dispose(editor)
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    Thread.sleep(100)

    assertThat(terminalTabs.tab.sentTexts).isEmpty()
    assertThat(file.initialMessageSent).isFalse()
  }

  @Test
  fun waitingForSessionRunningSendsLatestInitialMessageMetadata() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = testFile()
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    file.updateInitialMessageMetadata(
      initialComposedMessage = "First draft",
      initialMessageToken = "token-1",
      initialMessageSent = false,
    )
    editor.flushPendingInitialMessageIfInitialized()

    file.updateInitialMessageMetadata(
      initialComposedMessage = "Second draft",
      initialMessageToken = "token-2",
      initialMessageSent = false,
    )
    editor.flushPendingInitialMessageIfInitialized()

    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    waitForCondition { file.initialMessageSent }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("Second draft", shouldExecute = true))
  }

  @Test
  fun terminatedSessionDoesNotSendInitialMessage() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "Do not send",
        initialMessageToken = "token-term",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Terminated)
    Thread.sleep(100)

    editor.flushPendingInitialMessageIfInitialized()
    assertThat(file.initialMessageSent).isFalse()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()
  }

  @Test
  fun timeoutReadinessStillSendsInitialMessage() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    terminalTabs.tab.readinessResult = AgentThreadViewTerminalInputReadiness.TIMEOUT
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "Send even if output is silent",
        initialMessageToken = "token-timeout",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("Send even if output is silent", shouldExecute = true))
  }

  @Test
  fun claudeMenuCommandInitialMessageUsesTypedInputInsteadOfBracketedPaste() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = testFile(
      threadIdentity = "CLAUDE:session-1",
      shellCommand = listOf("claude", "--resume", "session-1"),
    ).also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "/mcp",
        initialMessageToken = "token-menu",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("/mcp", shouldExecute = true, useBracketedPasteMode = false))
  }

  @Test
  fun junieInitialMessageWaitsForPromptInputBeforeSending() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val file = testFile(
      threadIdentity = "junie:new-1",
      shellCommand = listOf("junie", "--skip-update-check"),
    ).also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "Implement the feature",
        initialMessageToken = "token-junie",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    terminalTabs.tab.emitMeaningfulOutput("Select model Current model")
    Thread.sleep(350)

    assertThat(file.initialMessageSent).isFalse()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()

    terminalTabs.tab.emitMeaningfulOutput("Welcome to Junie Type your prompt...")
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("Implement the feature", shouldExecute = true))
  }

  @Test
  fun codexPlannerPrefixStillFallsBackOnTimeout() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    terminalTabs.tab.readinessResult = AgentThreadViewTerminalInputReadiness.TIMEOUT
    val file = testFile().also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "/planner still fallback",
        initialMessageToken = "token-planner-timeout",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    waitForCondition { file.initialMessageSent }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("/planner still fallback", shouldExecute = true))
  }

  @Test
  fun nonCodexPlanCommandStillFallsBackOnTimeout() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    terminalTabs.tab.readinessResult = AgentThreadViewTerminalInputReadiness.TIMEOUT
    val file = testFile(
      threadIdentity = "CLAUDE:thread-1",
      shellCommand = listOf("claude", "--resume", "thread-1"),
    ).also {
      it.updateInitialMessageMetadata(
        initialComposedMessage = "/plan fallback for non-codex",
        initialMessageToken = "token-non-codex-timeout",
        initialMessageSent = false,
      )
    }
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(SentTerminalText("/plan fallback for non-codex", shouldExecute = true))
  }

  @Test
  fun timeoutPolicyUsesLatestInitialMessageMetadata() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    terminalTabs.tab.readinessResult = AgentThreadViewTerminalInputReadiness.TIMEOUT
    val file = testFile()
    val editor = testEditor(file = file, terminalTabs = terminalTabs)

    editor.selectNotify()
    file.updateInitialMessageMetadata(
      initialComposedMessage = "Fallback candidate",
      initialMessageToken = "token-timeout-latest-1",
      initialMessageSent = false,
    )
    editor.flushPendingInitialMessageIfInitialized()

    file.updateInitialMessageMetadata(
      initialComposedMessage = "/plan Wait for readiness",
      initialMessageToken = "token-timeout-latest-2",
      initialMessageSent = false,
      initialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
    )
    editor.flushPendingInitialMessageIfInitialized()

    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)
    Thread.sleep(100)

    assertThat(file.initialMessageSent).isFalse()
    assertThat(terminalTabs.tab.sentTexts).isEmpty()
    Disposer.dispose(editor)
  }

  @Test
  fun slashNewTrackerIgnoresPartialCommandsAndHandlesBackspaceCorrection() {
    val tracker = AgentThreadViewTerminalCommandTracker()

    "/new branch".forEach { tracker.record(keyTyped(it)) }
    assertThat(tracker.record(keyPressed(KeyEvent.VK_ENTER))).isEqualTo("/new branch")

    "/newx".forEach { tracker.record(keyTyped(it)) }
    tracker.record(keyPressed(KeyEvent.VK_BACK_SPACE))
    assertThat(tracker.record(keyPressed(KeyEvent.VK_ENTER))).isEqualTo("/new")

    "echo /new".forEach { tracker.record(keyTyped(it)) }
    assertThat(tracker.record(keyPressed(KeyEvent.VK_ENTER))).isEqualTo("echo /new")
  }

  @Test
  fun pendingCodexFirstInputPersistsMetadataAndRetriesScopedRefresh() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val snapshotWriter = RecordingSnapshotWriter()
    val signalCollector = CodexScopedRefreshSignalCollector()
    val file = pendingTestFile()
    val editor = testEditor(
      file = file,
      terminalTabs = terminalTabs,
      snapshotWriter = snapshotWriter,
      pendingScopedRefreshRetryIntervalMs = 25L,
    )

    try {
      editor.selectNotify()
      terminalTabs.tab.emitKeyEvent(keyTyped('a'))

      waitForCondition {
        file.pendingFirstInputAtMs != null &&
        snapshotWriter.snapshots.lastOrNull()?.runtime?.pendingFirstInputAtMs == file.pendingFirstInputAtMs &&
        signalCollector.codexSignals.size >= 2
      }

      assertThat(signalCollector.codexSignals.map { it.single() }).containsOnly(file.projectPath)
    }
    finally {
      signalCollector.dispose()
      Disposer.dispose(editor)
    }
  }

  @Test
  fun restoredPendingCodexTabResumesScopedRefreshRetriesOnInitialization() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val signalCollector = CodexScopedRefreshSignalCollector()
    val pendingFirstInputAtMs = System.currentTimeMillis() - 100L
    val file = pendingTestFile(pendingFirstInputAtMs = pendingFirstInputAtMs)
    val editor = testEditor(
      file = file,
      terminalTabs = terminalTabs,
      pendingScopedRefreshRetryIntervalMs = 25L,
    )

    try {
      editor.selectNotify()

      waitForCondition {
        signalCollector.codexSignals.size >= 2
      }

      assertThat(file.pendingFirstInputAtMs).isEqualTo(pendingFirstInputAtMs)
      assertThat(signalCollector.codexSignals.map { it.single() }).containsOnly(file.projectPath)
    }
    finally {
      signalCollector.dispose()
      Disposer.dispose(editor)
    }
  }

  @Test
  fun pendingCodexScopedRefreshRetriesStopAfterRebind() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val signalCollector = CodexScopedRefreshSignalCollector()
    val file = pendingTestFile()
    val editor = testEditor(
      file = file,
      terminalTabs = terminalTabs,
      pendingScopedRefreshRetryIntervalMs = 100L,
    )

    try {
      editor.selectNotify()
      terminalTabs.tab.emitKeyEvent(keyTyped('b'))
      waitForCondition { signalCollector.codexSignals.isNotEmpty() }

      file.rebindPendingThread(
        threadIdentity = buildAgentThreadIdentity(AgentSessionProvider.from("codex").value, "thread-42"),
        threadId = "thread-42",
        threadTitle = "Recovered thread",
        threadActivity = AgentThreadActivity.READY,
      )

      val signalCountAfterRebind = signalCollector.codexSignals.size
      Thread.sleep(180)

      assertThat(file.isPendingThread).isFalse()
      assertThat(signalCollector.codexSignals).hasSize(signalCountAfterRebind)
    }
    finally {
      signalCollector.dispose()
      Disposer.dispose(editor)
    }
  }

  @Test
  fun stalePendingCodexTabDoesNotResumeScopedRefreshRetries() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val signalCollector = CodexScopedRefreshSignalCollector()
    val file = pendingTestFile(
      pendingFirstInputAtMs = System.currentTimeMillis() - AgentSessionThreadRebindPolicy.PENDING_THREAD_MATCH_POST_WINDOW_MS - 1L,
    )
    val editor = testEditor(
      file = file,
      terminalTabs = terminalTabs,
      pendingScopedRefreshRetryIntervalMs = 25L,
    )

    try {
      editor.selectNotify()
      Thread.sleep(120)

      assertThat(signalCollector.codexSignals).isEmpty()
    }
    finally {
      signalCollector.dispose()
      Disposer.dispose(editor)
    }
  }

  @Test
  fun pendingClaudeTabDoesNotStartCodexScopedRefreshRetries() {
    val terminalTabs = FakeAgentThreadViewTerminalTabs()
    val signalCollector = CodexScopedRefreshSignalCollector()
    val file = pendingTestFile(
      provider = AgentSessionProvider.from("claude"),
      pendingFirstInputAtMs = System.currentTimeMillis() - 100L,
    )
    val editor = testEditor(
      file = file,
      terminalTabs = terminalTabs,
      pendingScopedRefreshRetryIntervalMs = 25L,
    )

    try {
      editor.selectNotify()
      terminalTabs.tab.emitKeyEvent(keyTyped('c'))
      Thread.sleep(120)

      assertThat(signalCollector.codexSignals).isEmpty()
    }
    finally {
      signalCollector.dispose()
      Disposer.dispose(editor)
    }
  }
}

private class FakeAgentThreadViewTerminalTabs : AgentThreadViewTerminalTabs {
  var createCalls: Int = 0
  var closeCalls: Int = 0
  var tab = FakeAgentThreadViewTerminalTab()
  val createdTabs: MutableList<FakeAgentThreadViewTerminalTab> = mutableListOf()
  var lastStartupLaunchSpec: AgentSessionTerminalLaunchSpec? = null

  override fun createTab(
    project: Project,
    file: AgentThreadViewVirtualFile,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentThreadViewTerminalTab {
    if (createCalls > 0) {
      tab = FakeAgentThreadViewTerminalTab()
    }
    createCalls++
    createdTabs += tab
    lastStartupLaunchSpec = startupLaunchSpec
    return tab
  }

  override fun closeTab(project: Project, tab: AgentThreadViewTerminalTab) {
    closeCalls++
    (tab as? FakeAgentThreadViewTerminalTab)?.coroutineScope?.cancel()
  }
}

private class FakeAgentThreadViewTerminalTab : AgentThreadViewTerminalTab {
  private val focusableComponent = RecordingFocusComponent()

  override val component: JComponent = JPanel()
  override val preferredFocusableComponent: JComponent = focusableComponent
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext = Job()
  }
  private val mutableSessionState: MutableStateFlow<TerminalViewSessionState> = MutableStateFlow(TerminalViewSessionState.NotStarted)
  private val mutableKeyEventsFlow: MutableSharedFlow<TerminalKeyEvent> = MutableSharedFlow(replay = 1, extraBufferCapacity = 16)
  override val sessionState: StateFlow<TerminalViewSessionState> = mutableSessionState
  override val keyEventsFlow: Flow<TerminalKeyEvent> = mutableKeyEventsFlow.asSharedFlow()

  @Volatile
  var readinessResult: AgentThreadViewTerminalInputReadiness = AgentThreadViewTerminalInputReadiness.READY

  @Volatile
  private var recentOutputTail: String = ""
  private val emittedOutputChunks: CopyOnWriteArrayList<EmittedOutputChunk> = CopyOnWriteArrayList()
  private val outputVersion: AtomicLong = AtomicLong()

  @JvmField
  val sentTexts: CopyOnWriteArrayList<SentTerminalText> = CopyOnWriteArrayList()

  val focusRequests: Int
    get() = focusableComponent.requestFocusInWindowCalls

  fun emitMeaningfulOutput(text: String = "ready") {
    val normalizedText = text.trim()
    if (normalizedText.isEmpty()) {
      return
    }
    recentOutputTail = normalizedText
    val nextVersion = outputVersion.incrementAndGet()
    emittedOutputChunks += EmittedOutputChunk(version = nextVersion, text = normalizedText)
  }

  fun setSessionState(state: TerminalViewSessionState) {
    mutableSessionState.value = state
  }

  fun emitKeyEvent(awtEvent: KeyEvent) {
    mutableKeyEventsFlow.tryEmit(terminalKeyEvent(awtEvent))
  }

  override suspend fun captureOutputCheckpoint(): AgentThreadViewTerminalOutputCheckpoint {
    val currentOutputVersion = outputVersion.get()
    return AgentThreadViewTerminalOutputCheckpoint(
      regularEndOffset = currentOutputVersion,
      alternativeEndOffset = currentOutputVersion,
    )
  }

  override suspend fun awaitOutputObservation(
    checkpoint: AgentThreadViewTerminalOutputCheckpoint,
    timeoutMs: Long,
    idleMs: Long,
  ): AgentThreadViewTerminalOutputObservation {
    val deadline = System.currentTimeMillis() + timeoutMs
    val pollIntervalMs = idleMs.coerceIn(10, 50)
    while (true) {
      if (sessionState.value == TerminalViewSessionState.Terminated) {
        return AgentThreadViewTerminalOutputObservation(
          readiness = AgentThreadViewTerminalInputReadiness.TERMINATED,
          text = readOutputSince(checkpoint),
        )
      }
      val text = readOutputSince(checkpoint)
      if (text.isNotEmpty()) {
        return AgentThreadViewTerminalOutputObservation(
          readiness = AgentThreadViewTerminalInputReadiness.READY,
          text = text,
        )
      }
      if (System.currentTimeMillis() >= deadline) {
        return AgentThreadViewTerminalOutputObservation(
          readiness = AgentThreadViewTerminalInputReadiness.TIMEOUT,
          text = text,
        )
      }
      Thread.sleep(pollIntervalMs)
    }
  }

  override fun sendText(text: String, shouldExecute: Boolean, useBracketedPasteMode: Boolean) {
    sentTexts += SentTerminalText(text, shouldExecute, useBracketedPasteMode)
  }

  override suspend fun awaitInitialMessageReadiness(
    timeoutMs: Long,
    idleMs: Long,
    checkpoint: AgentThreadViewTerminalOutputCheckpoint?,
  ): AgentThreadViewTerminalInputReadiness {
    if (sessionState.value == TerminalViewSessionState.Terminated) {
      return AgentThreadViewTerminalInputReadiness.TERMINATED
    }
    if (hasMeaningfulOutputSince(checkpoint)) {
      return AgentThreadViewTerminalInputReadiness.READY
    }
    return readinessResult
  }

  override suspend fun readRecentOutputTail(): String {
    return recentOutputTail.takeLast(4_096)
  }

  private fun hasMeaningfulOutputSince(checkpoint: AgentThreadViewTerminalOutputCheckpoint?): Boolean {
    val baseline = checkpoint?.regularEndOffset ?: Long.MIN_VALUE
    return emittedOutputChunks.any { chunk -> chunk.version > baseline }
  }

  private fun readOutputSince(checkpoint: AgentThreadViewTerminalOutputCheckpoint): String {
    return emittedOutputChunks
      .filter { chunk -> chunk.version > checkpoint.regularEndOffset }
      .joinToString(separator = "\n") { chunk -> chunk.text }
  }
}

private class RecordingFocusComponent : JButton("focus") {
  var requestFocusInWindowCalls: Int = 0
    private set

  override fun requestFocusInWindow(): Boolean {
    requestFocusInWindowCalls++
    return true
  }
}

private data class EmittedOutputChunk(
  @JvmField val version: Long,
  @JvmField val text: String,
)

private data class SentTerminalText(
  @JvmField val text: String,
  @JvmField val shouldExecute: Boolean,
  @JvmField val useBracketedPasteMode: Boolean = true,
)

private fun testFile(
  threadIdentity: String = "CODEX:thread-1",
  shellCommand: List<String> = listOf("codex", "resume", "thread-1"),
): AgentThreadViewVirtualFile {
  return AgentThreadViewVirtualFile(
    projectPath = "/work/project-a",
    threadIdentity = threadIdentity,
    shellCommand = shellCommand,
    threadId = "thread-1",
    threadTitle = "Thread",
    subAgentId = null,
    projectHash = "hash-1",
  )
}

private fun pendingTestFile(
  provider: AgentSessionProvider = AgentSessionProvider.from("codex"),
  pendingFirstInputAtMs: Long? = null,
): AgentThreadViewVirtualFile {
  return testFile(
    threadIdentity = buildAgentThreadIdentity(provider.value, "new-thread"),
    shellCommand = listOf(provider.value),
  ).also { file ->
    file.updatePendingMetadata(
      pendingCreatedAtMs = System.currentTimeMillis() - 1_000L,
      pendingFirstInputAtMs = pendingFirstInputAtMs,
      pendingLaunchMode = "standard",
    )
  }
}

private fun restoredConcreteTestFile(): AgentThreadViewVirtualFile {
  val snapshot = AgentThreadViewTabSnapshot.create(
    projectHash = "hash-1",
    projectPath = "/work/project-a",
    threadIdentity = "CODEX:thread-restored",
    threadId = "thread-restored",
    threadTitle = "Restored thread",
    subAgentId = null,
  )
  return AgentThreadViewVirtualFile(
    fileSystem = createStandaloneAgentThreadViewVirtualFileSystemForTest(),
    resolution = AgentThreadViewTabResolution.Resolved(snapshot),
  )
}

private fun testContextItem(): AgentPromptContextItem {
  return AgentPromptContextItem(
    rendererId = "test",
    title = "Queued.kt",
    body = "body",
    source = "test",
  )
}

private fun claudeLifecycleTestFile(): AgentThreadViewVirtualFile {
  return testFile(
    threadIdentity = "CLAUDE:session-1",
    shellCommand = listOf("claude", "--resume", "session-1"),
  )
}

private fun codexLifecycleTestFile(): AgentThreadViewVirtualFile {
  return testFile(
    threadIdentity = buildAgentThreadIdentity(AgentSessionProvider.from("codex").value, "codex-1"),
    shellCommand = listOf("codex", "resume", "codex-1"),
  ).also { file ->
    file.updateThreadId("codex-1")
  }
}

private fun terminalLifecycleTestFile(): AgentThreadViewVirtualFile {
  return testFile(
    threadIdentity = buildAgentThreadIdentity(AgentSessionProvider.from("terminal").value, "terminal-1"),
    shellCommand = emptyList(),
  ).also { file ->
    file.updateThreadId("terminal-1")
  }
}

private fun testEditor(
  project: Project = testProject(),
  file: AgentThreadViewVirtualFile = testFile(),
  terminalTabs: AgentThreadViewTerminalTabs = FakeAgentThreadViewTerminalTabs(),
  liveTerminalRegistry: AgentThreadViewLiveTerminalRegistry = TestAgentThreadViewLiveTerminalRegistry(project),
  snapshotWriter: AgentThreadViewTabSnapshotWriter = AgentThreadViewTabSnapshotWriter { },
  archivedRestoreHandler: AgentThreadViewArchivedRestoreHandler = AgentThreadViewArchivedRestoreHandler { },
  pendingScopedRefreshRetryIntervalMs: Long = AgentSessionThreadRebindPolicy.PENDING_THREAD_REFRESH_RETRY_INTERVAL_MS,
  editorCoroutineScope: CoroutineScope? = unconfinedTestScope(),
  showComponent: Boolean = true,
  providerDescriptorResolver: (AgentSessionProvider) -> AgentSessionProviderDescriptor? = ::testAgentSessionProviderDescriptor,
  customContentProviderResolver: (AgentThreadViewContentContext) -> AgentThreadViewCustomContentProvider? = { null },
  behaviorResolver: (AgentSessionProvider?) -> AgentThreadViewProviderBehavior = ::testAgentThreadViewProviderBehavior,
): AgentThreadViewFileEditor {
  return AgentThreadViewFileEditor(
    project = project,
    file = file,
    terminalTabs = terminalTabs,
    liveTerminalRegistry = liveTerminalRegistry,
    tabSnapshotWriter = snapshotWriter,
    archivedRestoreHandler = archivedRestoreHandler,
    pendingScopedRefreshRetryIntervalMs = pendingScopedRefreshRetryIntervalMs,
    editorCoroutineScope = editorCoroutineScope,
    providerDescriptorResolver = providerDescriptorResolver,
    customContentProviderResolver = customContentProviderResolver,
    behaviorResolver = behaviorResolver,
  ).also { editor ->
    if (showComponent) {
      editor.showComponentForTests()
    }
    editorsToDispose += editor
  }
}

private fun testAgentThreadViewProviderBehavior(provider: AgentSessionProvider?): AgentThreadViewProviderBehavior {
  return when (provider) {
    AgentSessionProvider.from("codex") -> TestCodexAgentThreadViewProviderBehavior
    AgentSessionProvider.from("junie") -> TestJunieAgentThreadViewProviderBehavior
    else -> TestDefaultAgentThreadViewProviderBehavior
  }
}

private object TestDefaultAgentThreadViewProviderBehavior : AgentThreadViewProviderBehavior

private object TestJunieAgentThreadViewProviderBehavior : AgentThreadViewProviderBehavior {
  override suspend fun beforeInitialMessageSend(
    file: AgentThreadViewBehaviorFile,
    tab: AgentThreadViewBehaviorTerminalTab,
    dispatch: AgentThreadViewInitialMessageDispatchContext,
    retryAttempt: Int,
  ): AgentThreadViewInitialMessageRetryDecision {
    return if (testJuniePromptInputReady(tab.readRecentOutputTail())) {
      AgentThreadViewInitialMessageRetryDecision.PROCEED
    }
    else {
      AgentThreadViewInitialMessageRetryDecision.RetryWithoutReadiness(TEST_JUNIE_RETRY_BACKOFF_MS)
    }
  }

}

private object TestCodexAgentThreadViewProviderBehavior : AgentThreadViewProviderBehavior {
  override fun supportsPendingThreadRefreshRetry(file: AgentThreadViewBehaviorFile): Boolean {
    return file.isPendingThread && file.subAgentId == null && file.provider == AgentSessionProvider.from("codex")
  }

  override fun pendingThreadRefreshRetryDelayMs(file: AgentThreadViewBehaviorFile, currentTimeMs: Long, retryIntervalMs: Long): Long? {
    if (!supportsPendingThreadRefreshRetry(file)) {
      return null
    }
    val pendingFirstInputAtMs = file.pendingFirstInputAtMs ?: return null
    val retryDeadlineMs = pendingFirstInputAtMs + AgentSessionThreadRebindPolicy.PENDING_THREAD_MATCH_POST_WINDOW_MS
    val remainingMs = retryDeadlineMs - currentTimeMs
    if (remainingMs <= 0L) {
      return null
    }
    return min(retryIntervalMs, remainingMs)
  }

  override fun supportsConcreteNewThreadRebind(
    file: AgentThreadViewBehaviorFile,
    descriptor: AgentSessionProviderDescriptor?,
  ): Boolean {
    return descriptor?.supportsNewThreadRebind == true && !file.isPendingThread && file.subAgentId == null
  }

  override fun isConcreteNewThreadRebindCommand(command: String): Boolean = command == "/new" || command == "/fork"
}

private fun testJuniePromptInputReady(text: String): Boolean {
  val normalized = testSanitizeTerminalText(text)
  return normalized.contains("Type your prompt", ignoreCase = true)
}

private fun testSanitizeTerminalText(text: String): String {
  val sanitized = buildString(text.length) {
    text.forEach { char ->
      append(
        when {
          char.isWhitespace() || char.isISOControl() -> ' '
          else -> char
        }
      )
    }
  }
  return sanitized.replace(TEST_TERMINAL_WHITESPACE_REGEX, " ").trim()
}

private val TEST_TERMINAL_WHITESPACE_REGEX: Regex = Regex(" +")

private fun unconfinedTestScope(): CoroutineScope {
  return object : CoroutineScope {
    override val coroutineContext: CoroutineContext = Job() + Dispatchers.Unconfined
  }
}

@Suppress("SameParameterValue")
private fun terminalTitle(threadId: String, threadTitle: String? = null): String {
  return listOfNotNull("thread:$threadId", threadTitle).joinToString(" | ")
}

private fun terminalTitleThreadRebindContributor(): AgentThreadViewTerminalTitleThreadRebindContributor {
  return object : AgentThreadViewTerminalTitleThreadRebindContributor {
    override fun extractThreadId(applicationTitle: String?): String? {
      return applicationTitle?.substringAfter("thread:", missingDelimiterValue = "")?.takeIf { it.isNotBlank() }
    }

    override fun extractThreadSignal(applicationTitle: String?): AgentThreadViewTerminalTitleThreadRebindSignal? {
      val value = applicationTitle?.substringAfter("thread:", missingDelimiterValue = "")?.takeIf { it.isNotBlank() } ?: return null
      val parts = value.split(" | ", limit = 2)
      return AgentThreadViewTerminalTitleThreadRebindSignal(
        threadId = parts[0],
        threadTitle = parts.getOrNull(1),
      )
    }
  }
}

private object PausedCoroutineDispatcher : CoroutineDispatcher() {
  override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
}

private fun codexPlanDispatchSteps(
  prompt: String,
  promptTimeoutPolicy: AgentInitialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
): List<AgentInitialMessageDispatchStep> {
  return listOf(
    AgentInitialMessageDispatchStep(
      text = prompt,
      timeoutPolicy = promptTimeoutPolicy,
      action = AgentInitialMessageDispatchAction.PROVIDER,
    ),
  )
}

private fun pendingRebindReport(
  projectPath: String,
  request: AgentThreadViewPendingTabRebindRequest,
  status: AgentThreadViewPendingTabRebindStatus,
): AgentThreadViewPendingTabRebindReport {
  val reboundFiles = if (status == AgentThreadViewPendingTabRebindStatus.REBOUND) 1 else 0
  return AgentThreadViewPendingTabRebindReport(
    requestedBindings = 1,
    reboundBindings = reboundFiles,
    reboundFiles = reboundFiles,
    updatedPresentations = reboundFiles,
    outcomesByPath = mapOf(
      projectPath to listOf(
        AgentThreadViewPendingTabRebindOutcome(
          projectPath = projectPath,
          request = request,
          status = status,
          reboundFiles = reboundFiles,
        )
      )
    ),
  )
}

private fun concreteRebindReport(
  projectPath: String,
  request: AgentThreadViewConcreteTabRebindRequest,
  status: AgentThreadViewConcreteTabRebindStatus,
): AgentThreadViewConcreteTabRebindReport {
  val reboundFiles = if (status == AgentThreadViewConcreteTabRebindStatus.REBOUND) 1 else 0
  return AgentThreadViewConcreteTabRebindReport(
    requestedBindings = 1,
    reboundBindings = reboundFiles,
    reboundFiles = reboundFiles,
    updatedPresentations = reboundFiles,
    outcomesByPath = mapOf(
      projectPath to listOf(
        AgentThreadViewConcreteTabRebindOutcome(
          projectPath = projectPath,
          request = request,
          status = status,
          reboundFiles = reboundFiles,
        )
      )
    ),
  )
}

private class RecordingSnapshotWriter : AgentThreadViewTabSnapshotWriter {
  val snapshots: MutableList<AgentThreadViewTabSnapshot> = mutableListOf()

  override suspend fun upsert(snapshot: AgentThreadViewTabSnapshot) {
    snapshots += snapshot
  }
}

private class CodexScopedRefreshSignalCollector {
  val codexSignals: CopyOnWriteArrayList<Set<String>> = CopyOnWriteArrayList()
  private val job = object : CoroutineScope {
    override val coroutineContext = Job() + Dispatchers.Default
  }.launch(start = CoroutineStart.UNDISPATCHED) {
    agentThreadViewScopedRefreshSignals(AgentSessionProvider.from("codex")).collect { signal ->
      codexSignals += signal.scopedPaths.orEmpty()
    }
  }

  fun dispose() {
    job.cancel()
  }
}

private data class ClosedTerminalSession(
  @JvmField val path: String,
  @JvmField val threadId: String,
)

private fun testAgentSessionProviderDescriptor(provider: AgentSessionProvider): AgentSessionProviderDescriptor? {
  return when (provider) {
    AgentSessionProvider.from("claude") -> TestClaudeAgentSessionProviderDescriptor
    else -> null
  }
}

private object TestClaudeAgentSessionProviderDescriptor : AgentSessionProviderDescriptor {
  override val provider: AgentSessionProvider = AgentSessionProvider.from("claude")
  override val displayNameKey: String = "provider.claude"
  override val newSessionLabelKey: String = displayNameKey
  override val icon: Icon = EmptyIcon.ICON_0
  override val sessionSource: AgentSessionSource
    get() = error("Not required for this test")
  override val cliMissingMessageKey: String = displayNameKey
  override val menuCommands: List<AgentSessionMenuCommand> = listOf(AgentSessionMenuCommand("/mcp"))

  override suspend fun isCliAvailable(): Boolean = true

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = emptyList())
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = emptyList())
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    return AgentInitialMessagePlan.EMPTY
  }
}

private class ArchivedThreadsProviderDescriptor(
  override val provider: AgentSessionProvider,
  private val archivedThreads: List<AgentSessionThread>,
) : AgentSessionProviderDescriptor {
  override val displayNameKey: String = "test.provider"
  override val newSessionLabelKey: String = "test.new.session"
  override val icon: Icon = EmptyIcon.ICON_0
  override val sessionSource: AgentSessionSource = object : AgentSessionSource, AgentSessionArchivedSource {
    override val provider: AgentSessionProvider
      get() = this@ArchivedThreadsProviderDescriptor.provider

    override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> = emptyList()

    override suspend fun listArchivedThreads(path: String, openProject: Project?): List<AgentSessionThread> = archivedThreads
  }
  override val cliMissingMessageKey: String = "test.cli.missing"

  override suspend fun isCliAvailable(): Boolean = true

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec =
    AgentSessionTerminalLaunchSpec(listOf(provider.value, "resume", sessionId))

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec =
    AgentSessionTerminalLaunchSpec(listOf(provider.value))

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan = AgentInitialMessagePlan.EMPTY
}

private class RecordingTerminalSessionClosedProvider(
  override val provider: AgentSessionProvider,
  override val supportsArchiveThread: Boolean = false,
  override val archiveOnLastEditorClose: Boolean = false,
) : AgentSessionProviderDescriptor {
  val closedSessions: CopyOnWriteArrayList<ClosedTerminalSession> = CopyOnWriteArrayList()

  override val displayNameKey: String = "test.provider"
  override val newSessionLabelKey: String = "test.new.session"
  override val icon: Icon = EmptyIcon.ICON_0
  override val sessionSource: AgentSessionSource = object : AgentSessionSource {
    override val provider: AgentSessionProvider
      get() = this@RecordingTerminalSessionClosedProvider.provider

    override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> = emptyList()

  }
  override val cliMissingMessageKey: String = "test.cli.missing"

  override suspend fun isCliAvailable(): Boolean = true

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec =
    AgentSessionTerminalLaunchSpec(emptyList())

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec =
    AgentSessionTerminalLaunchSpec(emptyList())

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan = AgentInitialMessagePlan.EMPTY

  override fun recordTerminalSessionClosed(path: String, threadId: String) {
    closedSessions += ClosedTerminalSession(path, threadId)
  }
}

private fun testProject(): Project {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "isDisposed" -> false
      "toString" -> "Project(agent-thread-view-lifecycle-test)"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> defaultValue(method.returnType)
    }
  }
  return Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java), handler) as Project
}

private class TestAgentThreadViewLiveTerminalRegistry(
  private val project: Project,
  private val store: AgentThreadViewLiveTerminalStore = AgentThreadViewLiveTerminalStore(),
) : AgentThreadViewLiveTerminalRegistry {
  override fun acquireOrCreate(
    file: AgentThreadViewVirtualFile,
    terminalTabs: AgentThreadViewTerminalTabs,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentThreadViewTerminalTab {
    return store.acquireOrCreate(project = project, file = file, terminalTabs = terminalTabs, startupLaunchSpec = startupLaunchSpec)
  }

  override fun replace(
    file: AgentThreadViewVirtualFile,
    terminalTabs: AgentThreadViewTerminalTabs,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentThreadViewTerminalTab {
    return store.replace(project = project, file = file, terminalTabs = terminalTabs, startupLaunchSpec = startupLaunchSpec)
  }
}

private fun testFileEditorManager(isFileOpen: Boolean): FileEditorManager {
  return TestFileEditorManager(isFileOpen)
}

private class TestFileEditorManager(isFileOpen: Boolean) : FileEditorManager() {
  @Volatile
  private var fileOpen: Boolean = isFileOpen
  private val testProjectInstance = testProject()
  private val selectedEditorStateFlow = MutableStateFlow<FileEditor?>(null)

  fun setFileOpen(isFileOpen: Boolean) {
    fileOpen = isFileOpen
  }

  override fun getComposite(file: VirtualFile): FileEditorComposite? = null

  override fun canOpenFile(file: VirtualFile): Boolean = true

  override fun openFile(file: VirtualFile, focusEditor: Boolean): Array<FileEditor> = emptyArray()

  override fun openFile(file: VirtualFile): List<FileEditor> = emptyList()

  override fun closeFile(file: VirtualFile) = Unit

  override fun openTextEditor(descriptor: OpenFileDescriptor, focusEditor: Boolean): Editor? = null

  override fun getSelectedTextEditor(): Editor? = null

  override fun isFileOpen(file: VirtualFile): Boolean = fileOpen

  override fun getOpenFiles(): Array<VirtualFile> = emptyArray()

  override fun getOpenFilesWithRemotes(): List<VirtualFile> = emptyList()

  override fun getCurrentFile(): VirtualFile? = null

  override fun getSelectedFiles(): Array<VirtualFile> = emptyArray()

  override fun getSelectedEditors(): Array<FileEditor> = emptyArray()

  override fun getSelectedEditorFlow(): StateFlow<FileEditor?> = selectedEditorStateFlow

  override fun getSelectedEditor(file: VirtualFile): FileEditor? = null

  override fun getEditors(file: VirtualFile): Array<FileEditor> = emptyArray()

  override fun getAllEditors(file: VirtualFile): Array<FileEditor> = emptyArray()

  override fun getAllEditorList(file: VirtualFile): List<FileEditor> = emptyList()

  override fun getAllEditors(): Array<FileEditor> = emptyArray()

  override fun addTopComponent(editor: FileEditor, component: JComponent) = Unit

  override fun removeTopComponent(editor: FileEditor, component: JComponent) = Unit

  override fun addBottomComponent(editor: FileEditor, component: JComponent) = Unit

  override fun removeBottomComponent(editor: FileEditor, component: JComponent) = Unit

  override fun openFileEditor(descriptor: FileEditorNavigatable, focusEditor: Boolean): List<FileEditor> = emptyList()

  override fun getProject(): Project = testProjectInstance

  override fun setSelectedEditor(file: VirtualFile, fileEditorProviderId: String) = Unit

  override fun runWhenLoaded(editor: Editor, runnable: Runnable) = runnable.run()

  override fun toString(): String = "FileEditorManager(agent-thread-view-lifecycle-test)"
}

private fun keyTyped(keyChar: Char): KeyEvent {
  return KeyEvent(JPanel(), KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, keyChar)
}

private fun keyPressed(keyCode: Int): KeyEvent {
  return KeyEvent(JPanel(), KeyEvent.KEY_PRESSED, 0L, 0, keyCode, KeyEvent.CHAR_UNDEFINED)
}

private fun terminalKeyEvent(awtEvent: KeyEvent): TerminalKeyEvent {
  return TERMINAL_KEY_EVENT_CONSTRUCTOR.newInstance(awtEvent, TerminalOffset.ZERO) as TerminalKeyEvent
}

private val TERMINAL_KEY_EVENT_CONSTRUCTOR: Constructor<*> by lazy {
  Class.forName("com.intellij.terminal.frontend.view.TerminalKeyEventImpl")
    .getDeclaredConstructor(KeyEvent::class.java, TerminalOffset::class.java)
    .apply { isAccessible = true }
}

private fun defaultValue(returnType: Class<*>): Any? {
  return when {
    !returnType.isPrimitive -> null
    returnType == Boolean::class.javaPrimitiveType -> false
    returnType == Int::class.javaPrimitiveType -> 0
    returnType == Long::class.javaPrimitiveType -> 0L
    returnType == Short::class.javaPrimitiveType -> 0.toShort()
    returnType == Byte::class.javaPrimitiveType -> 0.toByte()
    returnType == Float::class.javaPrimitiveType -> 0f
    returnType == Double::class.javaPrimitiveType -> 0.0
    returnType == Char::class.javaPrimitiveType -> '\u0000'
    else -> null
  }
}

private fun waitForCondition(timeoutMs: Long = 2_000, condition: () -> Boolean) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    if (condition()) {
      return
    }
    Thread.sleep(10)
  }
  throw AssertionError("Condition was not satisfied within ${timeoutMs}ms")
}

private fun layoutRecursively(component: Component) {
  if (component is Container) {
    component.doLayout()
    component.components.forEach(::layoutRecursively)
  }
}

private fun <T : Component> collectComponentsOfType(component: Component, type: Class<T>): List<T> {
  val result = ArrayList<T>()

  fun visit(current: Component) {
    if (type.isInstance(current)) {
      result.add(type.cast(current))
    }
    if (current is Container) {
      current.components.forEach(::visit)
    }
  }

  visit(component)
  return result
}

private fun collectAgentThreadViewStartProgressComponents(component: Component): List<Component> {
  val result = ArrayList<Component>()

  fun visit(current: Component) {
    if (current.name == "Agent Thread View Start Progress") {
      result.add(current)
    }
    if (current is Container) {
      current.components.forEach(::visit)
    }
  }

  visit(component)
  return result
}

private fun xCenterInRoot(component: Component, root: Component): Int {
  val location = SwingUtilities.convertPoint(component.parent, component.location, root)
  return location.x + component.width / 2
}

private fun yCenterInRoot(component: Component, root: Component): Int {
  val location = SwingUtilities.convertPoint(component.parent, component.location, root)
  return location.y + component.height / 2
}

private const val TEST_JUNIE_RETRY_BACKOFF_MS: Long = 100
