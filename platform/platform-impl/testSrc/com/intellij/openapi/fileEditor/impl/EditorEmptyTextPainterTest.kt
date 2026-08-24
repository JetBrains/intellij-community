// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardGestureAction
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.impl.finishEmptyEditorStartupBeforeProjectView
import com.intellij.openapi.project.impl.presentProjectViewOnStartup
import com.intellij.openapi.project.impl.shouldRestoreStartupEditorFocus
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@TestApplication
@RunInEdt(writeIntent = true)
internal class EditorEmptyTextPainterTest {
  private val doubleShiftShortcut = KeyboardModifierGestureShortcut.newInstance(
    KeyboardGestureAction.ModifierType.dblClick,
    KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, InputEvent.SHIFT_MASK),
  ) as KeyboardModifierGestureShortcut
  private val doubleCtrlShortcut = KeyboardModifierGestureShortcut.newInstance(
    KeyboardGestureAction.ModifierType.dblClick,
    KeyStroke.getKeyStroke(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK),
  ) as KeyboardModifierGestureShortcut
  private val ctrlBackslashShortcut = KeyboardShortcut(
    KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, InputEvent.CTRL_MASK),
    null,
  )
  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()
  private val tempPathFixture = tempPathFixture()
  private val tempPath by tempPathFixture

  private lateinit var originalShortcuts: Map<String, List<Shortcut>>

  private val manager: FileEditorManagerImpl
    get() = fileEditorManagerFixture.get()

  @BeforeEach
  fun setUp() {
    originalShortcuts = listOf(IdeActions.ACTION_SEARCH_EVERYWHERE, PROVIDER_ACTION_ID)
      .associateWith { activeKeymap().getShortcuts(it).toList() }
  }

  @AfterEach
  fun tearDown() {
    originalShortcuts.forEach { (actionId, shortcuts) -> resetShortcuts(actionId, shortcuts) }
    val splitters = manager.mainSplitters
    splitters.setEmptyStateComponentCreationGateForTests(null)
    splitters.setEmptyStateComponentFocusRequesterForTests(null)
    // a creation left waiting out an inflated delay must not survive into the next test
    splitters.suppressRichEmptyStateComponents()
    splitters.setEmptyStateComponentCreationDelayForTests(null)
    splitters.setEmptyStateComponentPresentationGateTimeoutForTests(null)
    splitters.resetStartupEmptyStatePresentationHoldForTests()
  }

  @Test
  fun defaultProviderSearchEverywhereHintIsHiddenWithoutShortcut(@TestDisposable disposable: Disposable) {
    resetShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE, emptyList())
    registerDefaultEmptyTextProvider(disposable)

    assertThat(RecordingEditorEmptyTextPainter().appendSearchEverywhereLines()).isEmpty()
  }

  @Test
  fun defaultProviderSearchEverywhereHintUsesAssignedShortcut(@TestDisposable disposable: Disposable) {
    resetShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE, listOf(doubleShiftShortcut))
    registerDefaultEmptyTextProvider(disposable)

    val expectedLine = IdeBundle.message("empty.text.search.everywhere") +
                       " <shortcut>" + KeymapUtil.getShortcutText(doubleShiftShortcut) + "</shortcut>"
    assertThat(RecordingEditorEmptyTextPainter().appendSearchEverywhereLines())
      .containsExactly(expectedLine)
  }

  @Test
  fun emptyTextProviderHintIsRenderedBeforeDefaultProvider(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROVIDER_ACTION_ID, listOf(doubleCtrlShortcut))
    resetShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE, listOf(doubleShiftShortcut))
    registerEmptyTextProvider(disposable, includeDefaultProvider = true)

    val providerLine = PROVIDER_ACTION_TEXT + " <shortcut>" + KeymapUtil.getShortcutText(doubleCtrlShortcut) + "</shortcut>"
    val searchEverywhereLine = IdeBundle.message("empty.text.search.everywhere") +
                               " <shortcut>" + KeymapUtil.getShortcutText(doubleShiftShortcut) + "</shortcut>"
    assertThat(RecordingEditorEmptyTextPainter().appendAdvertisedActionLines())
      .startsWith(providerLine, searchEverywhereLine)
  }

  @Test
  fun emptyTextProviderHintUsesAssignedShortcuts(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROVIDER_ACTION_ID, listOf(ctrlBackslashShortcut, doubleCtrlShortcut))
    registerEmptyTextProvider(disposable)

    val lines = RecordingEditorEmptyTextPainter().appendProviderActionLines()

    val expectedShortcutText = KeymapUtil.getShortcutText(doubleCtrlShortcut) +
                               " " + IdeBundle.message("empty.text.shortcut.separator") + " " +
                               KeymapUtil.getShortcutText(ctrlBackslashShortcut)
    assertThat(lines).containsExactly("$PROVIDER_ACTION_TEXT <shortcut>$expectedShortcutText</shortcut>")
  }

  @Test
  fun emptyTextProviderHintIsHiddenWithoutShortcut(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROVIDER_ACTION_ID, emptyList())
    registerEmptyTextProvider(disposable)

    assertThat(RecordingEditorEmptyTextPainter().appendProviderActionLines()).isEmpty()
  }

  @Test
  fun componentProviderIsVisibleOnlyInEmptyEditorState(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    val disposedComponents = AtomicInteger()
    registerComponentProvider(disposable, disposedComponents)
    manager.closeAllFiles()
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    splitters.updateEmptyStateComponent()
    waitForEmptyStateComponentCreation(splitters)

    assertThat(findEmptyStateComponent(splitters)).isNotNull()

    val disposedBeforeOpen = disposedComponents.get()
    val file = LightVirtualFile("empty-state.txt", "content")
    manager.openFile(file, false)

    assertThat(findEmptyStateComponent(splitters)).isNull()
    assertThat(disposedComponents).hasValue(disposedBeforeOpen + 1)

    manager.closeFile(file)
    waitForEmptyStateComponentCreation(splitters)

    assertThat(findEmptyStateComponent(splitters)).isNotNull()
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun splittersScopeDisposesVisibleEmptyStateComponent(@TestDisposable disposable: Disposable) {
    val disposedComponents = AtomicInteger()
    registerComponentProvider(disposable, disposedComponents)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val splitters = EditorsSplitters(manager = manager, coroutineScope = scope)
    try {
      enableRichEmptyStateComponentsWithoutDelay(splitters)
      splitters.updateEmptyStateComponent()
      waitForEmptyStateComponentCreation(splitters)

      assertThat(findEmptyStateComponent(splitters)).isNotNull()

      scope.cancel()
      // the scope's job completes only once its long-running children do, so the disposal it triggers is posted to the EDT
      // some time after `cancel()` returns — wait for it instead of pumping once
      waitForNoEmptyStateComponent(splitters)

      assertThat(disposedComponents).hasValue(1)
    }
    finally {
      scope.cancel()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
  }

  @Test
  fun componentProviderIsMountedAsEmptyStatePanel(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerComponentProvider(disposable)
    manager.closeAllFiles()
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    splitters.updateEmptyStateComponent()
    waitForEmptyStateComponentCreation(splitters)

    val emptyStateHost = checkNotNull(findEmptyStateHost(splitters))

    assertThat(emptyStateHost.parent).isSameAs(splitters)
    assertThat(emptyStateLayout(splitters).emptyStateOverlay).isSameAs(emptyStateHost)
    assertThat(splitters.components).containsExactly(emptyStateHost)
    assertThat(splitters.isOptimizedDrawingEnabled).isFalse()
  }

  @Test
  fun componentProviderIsMountedAboveEmptyEditorRoot(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerComponentProvider(disposable)
    manager.closeAllFiles()
    splitters.createCurrentWindow()
    val editorRoot = checkNotNull(emptyStateLayout(splitters).editorRootComponent)

    enableRichEmptyStateComponentsWithoutDelay(splitters)
    splitters.updateEmptyStateComponent()
    waitForEmptyStateComponentCreation(splitters)

    val emptyStateHost = checkNotNull(findEmptyStateHost(splitters))

    assertThat(emptyStateLayout(splitters).editorRootComponent).isSameAs(editorRoot)
    assertThat(emptyStateLayout(splitters).emptyStateOverlay).isSameAs(emptyStateHost)
    assertThat(splitters.components).containsExactly(emptyStateHost, editorRoot)
    assertThat(splitters.isOptimizedDrawingEnabled).isFalse()
  }

  @Test
  fun componentProviderCanBeMountedAfterEditorRootIsCleared(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerComponentProvider(disposable)
    manager.closeAllFiles()
    splitters.createCurrentWindow()
    assertThat(emptyStateLayout(splitters).editorRootComponent).isNotNull()

    splitters.clear()
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    splitters.updateEmptyStateComponent()
    waitForEmptyStateComponentCreation(splitters)

    assertThat(emptyStateLayout(splitters).editorRootComponent).isNull()
    assertThat(findEmptyStateComponent(splitters)).isNotNull()
    assertThat(emptyStateLayout(splitters).emptyStateOverlay).isSameAs(findEmptyStateHost(splitters))
  }

  @Test
  fun richComponentProviderIsPreferredOverTheFallbackEmptyText(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROVIDER_ACTION_ID, listOf(doubleCtrlShortcut))
    resetShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE, listOf(doubleShiftShortcut))
    registerEmptyTextProvider(disposable)
    registerComponentProvider(disposable, includeFallbackProvider = true)

    val splitters = manager.mainSplitters
    manager.closeAllFiles()
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    splitters.updateEmptyStateComponent()
    waitForEmptyStateComponentCreation(splitters)

    assertThat(findEmptyStateComponent(splitters)).isNotNull()
    assertThat(findEmptyTextComponent(splitters)).isNull()
  }

  @Test
  fun aPendingRichCreationLeavesTheFallbackEmptyTextUnmounted(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROVIDER_ACTION_ID, listOf(doubleCtrlShortcut))
    registerEmptyTextProvider(disposable)
    registerComponentProvider(disposable, includeFallbackProvider = true)

    val splitters = manager.mainSplitters
    val gateEntered = CompletableDeferred<Unit>()
    val releaseGate = CompletableDeferred<Unit>()
    splitters.setEmptyStateComponentCreationDelayForTests(Duration.ZERO)
    splitters.setEmptyStateComponentCreationGateForTests {
      gateEntered.complete(Unit)
      releaseGate.await()
    }

    manager.closeAllFiles()
    splitters.enableRichEmptyStateComponents()
    waitForDeferred(gateEntered)

    try {
      // the fallback is not a stand-in for a rich component that is still being prepared: this area shows nothing until it mounts,
      // which is what bounds the wait on the presentation gate
      assertThat(findEmptyTextComponent(splitters)).isNull()
    }
    finally {
      releaseGate.complete(Unit)
      waitForEmptyStateComponentCreation(splitters)
    }
  }

  @Test
  fun fallbackEmptyTextProviderIsMountedWithoutRichProvider(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROVIDER_ACTION_ID, listOf(doubleCtrlShortcut))
    registerEmptyTextProvider(disposable)
    registerFallbackComponentProvider(disposable)

    val splitters = manager.mainSplitters
    manager.closeAllFiles()
    splitters.updateEmptyStateComponent()
    waitForEmptyStateComponentCreation(splitters)

    assertThat(findEmptyStateComponent(splitters)).isNull()
    assertThat(findEmptyTextComponent(splitters)).isNotNull()
    assertThat(emptyStateLayout(splitters).emptyStateOverlay).isSameAs(findEmptyTextComponent(splitters)?.parent)
  }

  @Test
  fun fallbackEmptyTextProviderIsMountedWhenAvailableComponentProviderCreatesNothing(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROVIDER_ACTION_ID, listOf(doubleCtrlShortcut))
    registerEmptyTextProvider(disposable)
    registerNullAndFallbackComponentProviders(disposable)

    val splitters = manager.mainSplitters
    manager.closeAllFiles()
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    splitters.updateEmptyStateComponent()
    waitForEmptyStateComponentCreation(splitters)

    assertThat(findEmptyStateComponent(splitters)).isNull()
    assertThat(findEmptyTextComponent(splitters)).isNotNull()
  }

  @Test
  fun fallbackEmptyTextReachedThroughARichProviderIsNotDelayed(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROVIDER_ACTION_ID, listOf(doubleCtrlShortcut))
    registerEmptyTextProvider(disposable)
    registerNullAndFallbackComponentProviders(disposable)

    val splitters = manager.mainSplitters
    manager.closeAllFiles()
    splitters.setEmptyStateComponentCreationDelayForTests(NEVER_ELAPSING_CREATION_DELAY)
    splitters.enableRichEmptyStateComponents()

    // the delay holds back a rich component; what is presented here is plain empty text, which this area showed before the delay
    // existed — reaching it through a rich provider that built nothing does not make it worth hiding
    waitForEmptyTextComponent(splitters, "A fallback reached through a rich provider waited out the creation delay")
  }

  @Test
  fun unavailableComponentProviderMountsNothing(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROVIDER_ACTION_ID, listOf(doubleCtrlShortcut))
    registerEmptyTextProvider(disposable)
    // the provider fails the test if it is invoked at all, so this covers availability as well as what gets mounted
    registerUnavailableComponentProvider(disposable)

    val splitters = manager.mainSplitters
    manager.closeAllFiles()
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    splitters.updateEmptyStateComponent()
    waitForEmptyStateComponentCreation(splitters)

    assertThat(findEmptyStateComponent(splitters)).isNull()
    assertThat(findEmptyTextComponent(splitters)).isNull()
  }

  @Test
  fun emptyStateComponentIsNotShownWithoutProvider(@TestDisposable disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, emptyList(), disposable)

    val splitters = manager.mainSplitters
    manager.closeAllFiles()
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    splitters.updateEmptyStateComponent()
    waitForEmptyStateComponentCreation(splitters)

    assertThat(findEmptyStateComponent(splitters)).isNull()
  }

  @Test
  fun emptyStateComponentIsNotSerialized(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerComponentProvider(disposable)
    manager.closeAllFiles()
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    splitters.updateEmptyStateComponent()
    waitForEmptyStateComponentCreation(splitters)

    val element = Element("state")
    splitters.writeExternal(element)

    assertThat(findEmptyStateComponent(splitters)).isNotNull()
    assertThat(element.children).isEmpty()
  }

  @Test
  fun componentProviderIsNotInvokedWhileRichEmptyStateComponentsAreSuppressed(@TestDisposable disposable: Disposable) {
    val providerCalls = AtomicInteger()
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, listOf(object : EditorEmptyStateComponentProvider {
      override suspend fun createComponent(splitters: EditorsSplitters): JComponent {
        providerCalls.incrementAndGet()
        return JPanel()
      }
    }), disposable)

    val splitters = manager.mainSplitters
    manager.closeAllFiles()
    splitters.updateEmptyStateComponent()
    waitForEmptyStateComponentCreation(splitters)

    assertThat(providerCalls).hasValue(0)
    assertThat(findEmptyStateComponent(splitters)).isNull()
  }

  @Test
  fun componentProviderIsNotInvokedWhileSavedStateRestoreIsPending(@TestDisposable disposable: Disposable) {
    val providerCalls = AtomicInteger()
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, listOf(object : EditorEmptyStateComponentProvider {
      override suspend fun createComponent(splitters: EditorsSplitters): JComponent {
        providerCalls.incrementAndGet()
        return JPanel()
      }
    }), disposable)

    val splitters = manager.mainSplitters
    manager.closeAllFiles()
    splitters.readExternal(splitterStateElementWithFile())
    splitters.enableRichEmptyStateComponents()
    splitters.updateEmptyStateComponent()
    waitForEmptyStateComponentCreation(splitters)

    assertThat(providerCalls).hasValue(0)
    assertThat(findEmptyStateComponent(splitters)).isNull()

    val openFilesJob = splitters.openFilesAsync(requestFocus = false)
    PlatformTestUtil.waitWhileBusy { !openFilesJob.isCompleted }
    waitForEmptyStateComponentCreation(splitters)

    // the restore ends with the area non-empty, so settling the startup empty state must dispose rather than mount —
    // no composer flash on a project that does reopen its editors
    assertThat(splitters.openFileList).isNotEmpty()
    assertThat(providerCalls).hasValue(0)
    assertThat(findEmptyStateComponent(splitters)).isNull()
  }

  @Test
  fun componentProviderIsInvokedForEmptySavedState(@TestDisposable disposable: Disposable) {
    val providerCalls = AtomicInteger()
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, listOf(object : EditorEmptyStateComponentProvider {
      override suspend fun createComponent(splitters: EditorsSplitters): JComponent {
        providerCalls.incrementAndGet()
        return JPanel().apply { name = EMPTY_STATE_COMPONENT_NAME }
      }
    }), disposable)

    val splitters = manager.mainSplitters
    manager.closeAllFiles()
    splitters.setEmptyStateComponentCreationDelayForTests(Duration.ZERO)
    splitters.readExternal(emptySplitterStateElement())
    splitters.enableRichEmptyStateComponents()
    splitters.updateEmptyStateComponent()
    waitForEmptyStateComponentCreation(splitters)

    assertThat(providerCalls).hasValue(1)
    assertThat(findEmptyStateComponent(splitters)).isNotNull()

    val openFilesJob = splitters.openFilesAsync(requestFocus = false)
    PlatformTestUtil.waitWhileBusy { !openFilesJob.isCompleted }
    waitForEmptyStateComponentCreation(splitters)
  }

  @Test
  fun suppressingRichEmptyStateComponentsDisposesVisibleComponent(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    val disposedComponents = AtomicInteger()
    registerComponentProvider(disposable, disposedComponents)
    manager.closeAllFiles()
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    waitForEmptyStateComponentCreation(splitters)

    assertThat(findEmptyStateComponent(splitters)).isNotNull()

    splitters.suppressRichEmptyStateComponents()

    assertThat(findEmptyStateComponent(splitters)).isNull()
    assertThat(disposedComponents).hasValue(1)
  }

  @Test
  fun startupHoldPreparesTheEmptyStateWithoutMountingIt(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    val providerCalls = AtomicInteger()
    registerComponentProvider(disposable, providerCalls = providerCalls)
    manager.closeAllFiles()
    // building happens before presenting, so a delay this long must never be reached: it would mean the components are only
    // built once project open is over, which is the latency this whole split exists to avoid
    splitters.setEmptyStateComponentCreationDelayForTests(NEVER_ELAPSING_CREATION_DELAY)

    splitters.beginStartupEmptyStatePresentationHold()
    splitters.finishStartupEditorRestore()

    waitForProviderCall(providerCalls, "The empty state was not prepared under the startup hold")
    dispatchEventsFor(100.milliseconds)

    // project open may still open an editor of its own, so nothing may be shown yet
    assertThat(findEmptyStateComponent(splitters)).isNull()
    assertThat(splitters.isEmptyStateComponentCreationPending()).isTrue()
  }

  @Test
  fun releasingTheStartupHoldMountsWithoutTheCreationDelay(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerComponentProvider(disposable)
    manager.closeAllFiles()
    splitters.setEmptyStateComponentCreationDelayForTests(NEVER_ELAPSING_CREATION_DELAY)
    splitters.beginStartupEmptyStatePresentationHold()
    splitters.finishStartupEditorRestore()
    dispatchEventsFor(100.milliseconds)
    assertThat(findEmptyStateComponent(splitters)).isNull()

    // the release is knowledge that nothing is coming, so there is no flash left for the delay to hide
    splitters.endStartupEmptyStatePresentationHold()

    waitForEmptyStateComponent(splitters, "Releasing the startup hold waited out the creation delay")
  }

  @Test
  fun everyStartupHoldMustBeReleasedBeforeTheEmptyStateIsPresented(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    val providerCalls = AtomicInteger()
    registerComponentProvider(disposable, providerCalls = providerCalls)
    manager.closeAllFiles()
    splitters.setEmptyStateComponentCreationDelayForTests(NEVER_ELAPSING_CREATION_DELAY)

    // project open holds for its own editor phase, and a file named on the command line is opened after that phase has ended: two
    // holds, taken by two owners that know nothing about each other
    splitters.beginStartupEmptyStatePresentationHold()
    splitters.beginStartupEmptyStatePresentationHold()
    splitters.finishStartupEditorRestore()
    waitForProviderCall(providerCalls, "The empty state was not prepared under the startup holds")

    splitters.endStartupEmptyStatePresentationHold()
    dispatchEventsFor(100.milliseconds)

    assertThat(findEmptyStateComponent(splitters)).isNull()

    splitters.endStartupEmptyStatePresentationHold()

    waitForEmptyStateComponent(splitters, "Releasing the last startup hold waited out the creation delay")
  }

  @Test
  fun releasingTheStartupHoldFromProjectOpensOwnHopMountsTheEmptyState(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    val providerCalls = AtomicInteger()
    registerComponentProvider(disposable, providerCalls = providerCalls)
    manager.closeAllFiles()
    splitters.setEmptyStateComponentCreationDelayForTests(NEVER_ELAPSING_CREATION_DELAY)
    splitters.beginStartupEmptyStatePresentationHold()
    splitters.finishStartupEditorRestore()
    waitForProviderCall(providerCalls, "The empty state was not prepared under the startup hold")

    // every other test releases straight from the test body; this one releases through the hop project open actually uses, so a release
    // that reaches the mount only because the test body happens to be on the EDT with a lock cannot pass
    releaseStartupHoldFromProjectOpensHop(splitters)

    waitForEmptyStateComponent(splitters, "A release from project open's own hop did not mount the empty state")
  }

  @Test
  fun aHoldNobodyReleasesStopsHoldingBackTheEmptyState(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerComponentProvider(disposable)
    manager.closeAllFiles()
    splitters.setEmptyStateComponentCreationDelayForTests(Duration.ZERO)
    splitters.setEmptyStateComponentPresentationGateTimeoutForTests(200.milliseconds)

    splitters.beginStartupEmptyStatePresentationHold()
    splitters.finishStartupEditorRestore()

    // nobody releases this hold, and the fallback empty text is not shown while a rich provider is available: without a ceiling on the
    // wait, this area would show nothing at all for as long as the project stays open
    waitForEmptyStateComponent(splitters, "A hold nobody released kept the empty state invisible")
  }

  @Test
  fun aReleaseThatHadNoHoldToPairWithIsNotReportedAsUnbalanced(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerComponentProvider(disposable)
    manager.closeAllFiles()
    splitters.setEmptyStateComponentCreationDelayForTests(Duration.ZERO)

    val holdWarnings = mutableListOf<String>()
    // project open releases its hold unconditionally, but takes it only once restoring has returned a component: an open cancelled
    // before that point releases a hold it never took, which is ordinary
    LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        if (message.contains("presentation hold")) {
          holdWarnings.add(message)
        }
        return true
      }
    }) {
      splitters.endStartupEmptyStatePresentationHold()
    }

    assertThat(holdWarnings).isEmpty()
    waitForEmptyStateComponent(splitters, "A release with no hold to pair with left presentation held")
  }

  @Test
  fun anEditorOpenedDuringTheStartupHoldPreventsTheEmptyStateEntirely(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    val disposedComponents = AtomicInteger()
    val disposedWithoutLock = AtomicBoolean()
    val providerCalls = AtomicInteger()
    registerComponentProvider(disposable, disposedComponents, providerCalls = providerCalls, disposedWithoutLock = disposedWithoutLock)
    manager.closeAllFiles()
    splitters.setEmptyStateComponentCreationDelayForTests(NEVER_ELAPSING_CREATION_DELAY)
    splitters.beginStartupEmptyStatePresentationHold()
    splitters.finishStartupEditorRestore()
    waitForProviderCall(providerCalls, "The empty state was not prepared under the startup hold")
    dispatchEventsFor(100.milliseconds)

    // the welcome tab, a README, a file named on the command line: project open keeps opening editors after restoring finds none
    val file = LightVirtualFile("empty-state-startup-hold.txt", "content")
    manager.openFile(file, false)
    splitters.endStartupEmptyStatePresentationHold()
    waitForEmptyStateComponentCreation(splitters)

    assertThat(findEmptyStateComponent(splitters)).isNull()
    assertThat(disposedComponents).hasValue(1)
    // discarding runs on a strict-UI hop inside the creation job, so the lock a provider needs to release an editor has to be taken
    // there rather than inherited from a dispatcher
    assertThat(disposedWithoutLock).isFalse()
  }

  @Test
  fun theStartupHoldDoesNotHoldBackTheFallbackEmptyText(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROVIDER_ACTION_ID, listOf(doubleCtrlShortcut))
    registerEmptyTextProvider(disposable)
    registerFallbackComponentProvider(disposable)

    val splitters = manager.mainSplitters
    manager.closeAllFiles()
    splitters.setEmptyStateComponentCreationDelayForTests(NEVER_ELAPSING_CREATION_DELAY)
    splitters.beginStartupEmptyStatePresentationHold()
    splitters.finishStartupEditorRestore()

    // plain empty text is what this area showed before the hold existed, and holding it back would leave the area blank rather than
    // plain: nothing else paints those hints any more
    waitForEmptyTextComponent(splitters, "The fallback empty text was held back by the startup presentation hold")
  }

  @Test
  fun aStartupHoldTakenAfterProjectOpenGaveUpIsNotHeldAgainstTheEmptyState(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerComponentProvider(disposable)
    val gateEntered = CompletableDeferred<Unit>()
    val releaseGate = CompletableDeferred<Unit>()
    splitters.setEmptyStateComponentCreationDelayForTests(Duration.ZERO)
    splitters.setEmptyStateComponentCreationGateForTests {
      gateEntered.complete(Unit)
      releaseGate.await()
    }
    manager.closeAllFiles()

    // project open was cancelled before restoring took its hold, so its own release runs first
    splitters.abandonStartupEmptyStatePresentationHold()
    waitForDeferred(gateEntered)
    // restoring takes its hold uninterruptibly, so it still arrives — with nobody left to release it
    splitters.beginStartupEmptyStatePresentationHold()
    releaseGate.complete(Unit)

    waitForEmptyStateComponent(splitters, "A hold taken after project open gave up left the empty state unpresented")
  }

  @Test
  fun userEmptiedEditorAreaKeepsTheCreationDelay(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerComponentProvider(disposable)
    splitters.setEmptyStateComponentCreationDelayForTests(NEVER_ELAPSING_CREATION_DELAY)
    splitters.enableRichEmptyStateComponents()

    manager.closeAllFiles()
    dispatchEventsFor(100.milliseconds)

    // closing the last tab is a user-visible transition, not startup: nothing says whether an editor is on its way in, so the
    // components may be built but the delay still holds them back
    assertThat(splitters.isEmptyStateComponentCreationPending()).isTrue()
    assertThat(findEmptyStateComponent(splitters)).isNull()
  }

  @Test
  fun doNotReopenFilesKeepsRichEmptyStateDisabledUntilExplicitEnable(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerComponentProvider(disposable)
    manager.closeAllFiles()
    val project = projectFixture.get()
    FileEditorManagerKeys.DO_NOT_REOPEN_FILES.set(project, true)
    try {
      splitters.setEmptyStateComponentCreationDelayForTests(Duration.ZERO)
      splitters.finishStartupEditorRestore()
      dispatchEventsFor(100.milliseconds)

      // editors arrive from elsewhere here, so the absence of a local restore settles nothing
      assertThat(splitters.isEmptyStateComponentCreationPending()).isFalse()
      assertThat(findEmptyStateComponent(splitters)).isNull()

      splitters.enableRichEmptyStateComponents()
      waitForEmptyStateComponent(splitters, "The explicit enable did not mount the empty state")
    }
    finally {
      FileEditorManagerKeys.DO_NOT_REOPEN_FILES.set(project, null)
    }
  }

  @Test
  fun doNotReopenFilesMountsWithoutTheCreationDelayOnceTheStartupHoldIsReleased(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerComponentProvider(disposable)
    manager.closeAllFiles()
    val project = projectFixture.get()
    FileEditorManagerKeys.DO_NOT_REOPEN_FILES.set(project, true)
    try {
      splitters.setEmptyStateComponentCreationDelayForTests(NEVER_ELAPSING_CREATION_DELAY)
      splitters.beginStartupEmptyStatePresentationHold()
      splitters.finishStartupEditorRestore()
      dispatchEventsFor(100.milliseconds)

      // nothing is even prepared yet: rich components stay disabled until something says editors are not arriving from elsewhere
      assertThat(splitters.isEmptyStateComponentCreationPending()).isFalse()

      // the release is what says it, and it enables rich components before opening the gate, so the creation it makes possible starts
      // under a closed gate and mounts at once instead of waiting out a delay that only exists to hide a flash
      splitters.endStartupEmptyStatePresentationHold()

      waitForEmptyStateComponent(splitters, "A project that does not reopen files waited out the creation delay")
    }
    finally {
      FileEditorManagerKeys.DO_NOT_REOPEN_FILES.set(project, null)
    }
  }

  @Test
  @Suppress("RAW_SCOPE_CREATION")
  fun restoringStateWithoutFileEntriesPreparesTheEmptyStateAtOnce(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    val providerCalls = AtomicInteger()
    registerComponentProvider(disposable, providerCalls = providerCalls)
    manager.closeAllFiles()
    splitters.setEmptyStateComponentCreationDelayForTests(NEVER_ELAPSING_CREATION_DELAY)
    splitters.beginStartupEmptyStatePresentationHold()

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    try {
      // a saved state whose leaves hold no files restores nothing, so the empty state may be built while the rest of the project
      // open runs — even though restoring does rebuild the editor component and its window
      val restoreJob = scope.launch { splitters.createEditors(EditorSplitterState(emptySplitterStateElement())) }
      PlatformTestUtil.waitWhileBusy { !restoreJob.isCompleted }

      waitForProviderCall(providerCalls, "Restoring a file-less state did not prepare the empty state")
      assertThat(findEmptyStateComponent(splitters)).isNull()

      splitters.endStartupEmptyStatePresentationHold()
      waitForEmptyStateComponent(splitters, "Restoring a file-less state waited out the creation delay")
    }
    finally {
      scope.cancel()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
  }

  @Test
  fun openFilesAsyncWithoutSavedStateEnablesRichEmptyStateComponents(@TestDisposable disposable: Disposable) {
    val providerCalls = AtomicInteger()
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, listOf(object : EditorEmptyStateComponentProvider {
      override suspend fun createComponent(splitters: EditorsSplitters): JComponent {
        providerCalls.incrementAndGet()
        return withContext(Dispatchers.EDT) {
          JPanel().apply { name = EMPTY_STATE_COMPONENT_NAME }
        }
      }
    }), disposable)

    val splitters = manager.mainSplitters
    manager.closeAllFiles()
    splitters.setEmptyStateComponentCreationDelayForTests(Duration.ZERO)

    val openFilesJob = splitters.openFilesAsync(requestFocus = false)
    PlatformTestUtil.waitWhileBusy { !openFilesJob.isCompleted }
    waitForEmptyStateComponentCreation(splitters)

    assertThat(providerCalls).hasValue(1)
    assertThat(findEmptyStateComponent(splitters)).isNotNull()
  }

  @Test
  fun openingFileDuringEmptyStateCreationDelayCancelsProviderInvocation(@TestDisposable disposable: Disposable) {
    val providerCalls = AtomicInteger()
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, listOf(object : EditorEmptyStateComponentProvider {
      override suspend fun createComponent(splitters: EditorsSplitters): JComponent {
        providerCalls.incrementAndGet()
        return JPanel()
      }
    }), disposable)

    val splitters = manager.mainSplitters
    val gateEntered = CompletableDeferred<Unit>()
    val releaseGate = CompletableDeferred<Unit>()
    splitters.setEmptyStateComponentCreationDelayForTests(Duration.ZERO)
    splitters.setEmptyStateComponentCreationGateForTests {
      gateEntered.complete(Unit)
      releaseGate.await()
    }
    manager.closeAllFiles()
    splitters.enableRichEmptyStateComponents()
    waitForDeferred(gateEntered)

    val file = LightVirtualFile("empty-state-cancel.txt", "content")
    manager.openFile(file, false)
    releaseGate.complete(Unit)
    waitForEmptyStateComponentCreation(splitters)

    assertThat(providerCalls).hasValue(0)
    assertThat(findEmptyStateComponent(splitters)).isNull()
  }

  @Test
  fun openingFileDuringProviderCreationDisposesAlreadyCreatedEntries(@TestDisposable disposable: Disposable) {
    val disposedComponents = AtomicInteger()
    val blockingProviderEntered = CompletableDeferred<Unit>()
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, listOf(
      object : EditorEmptyStateComponentProvider {
        override suspend fun createComponent(splitters: EditorsSplitters): JComponent = withContext(Dispatchers.EDT) {
          JPanel().apply { name = EMPTY_STATE_COMPONENT_NAME }
        }

        override fun disposeComponent(component: JComponent) {
          disposedComponents.incrementAndGet()
        }
      },
      object : EditorEmptyStateComponentProvider {
        override suspend fun createComponent(splitters: EditorsSplitters): JComponent? {
          blockingProviderEntered.complete(Unit)
          awaitCancellation()
        }
      },
    ), disposable)

    val splitters = manager.mainSplitters
    manager.closeAllFiles()
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    waitForDeferred(blockingProviderEntered)

    val file = LightVirtualFile("empty-state-provider-cancel.txt", "content")
    manager.openFile(file, false)
    waitForEmptyStateComponentCreation(splitters)

    assertThat(findEmptyStateComponent(splitters)).isNull()
    assertThat(disposedComponents).hasValue(1)
  }

  @Test
  fun aClaimingEmptyStateIsFocusedWhenItIsPresented(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerFocusClaimingComponentProvider(disposable)
    manager.closeAllFiles()
    val focusRequests = CopyOnWriteArrayList<JComponent>()
    var reportFocusTransferred: (() -> Unit)? = null
    splitters.setEmptyStateComponentFocusRequesterForTests { component, transferred ->
      focusRequests.add(component)
      reportFocusTransferred = transferred
    }
    splitters.setEmptyStateComponentCreationDelayForTests(NEVER_ELAPSING_CREATION_DELAY)
    splitters.beginStartupEmptyStatePresentationHold()

    // the request is made where project open makes it: before anything is built, and honoured only once the empty state is presented
    val focusSettled = splitters.requestEmptyStateFocusWhenPresentedAsync()
    splitters.finishStartupEditorRestore()
    dispatchEventsFor(100.milliseconds)

    assertThat(focusRequests).isEmpty()
    assertThat(focusSettled.isCompleted).isFalse()

    releaseStartupHoldFromProjectOpensHop(splitters)
    waitForEmptyStateComponent(splitters, "The claimed empty state was not presented")

    assertThat(focusRequests).containsExactly(findFocusTargetComponent(splitters))
    assertThat(focusSettled.isCompleted).isFalse()

    checkNotNull(reportFocusTransferred).invoke()
    assertThat(focusSettled.isCompleted).isTrue()
  }

  @Test
  fun startupEmptyStateIsPresentedBeforeProjectViewIsOpened() {
    runBlocking {
      val startupEvents = mutableListOf<String>()

      finishEmptyEditorStartupBeforeProjectView(
        finishOpeningStartupEditors = { startupEvents.add("startup editors finished") },
        presentEmptyEditor = { startupEvents.add("empty editor presented") },
        openProjectView = { startupEvents.add("Project view opened") },
      )

      assertThat(startupEvents).containsExactly(
        "startup editors finished",
        "empty editor presented",
        "Project view opened",
      )
    }
  }

  @Test
  fun startupProjectViewIsShownWithoutActivationWhenTheEditorKeepsFocus() {
    val events = mutableListOf<String>()

    presentProjectViewOnStartup(
      focusProjectView = false,
      showProjectView = { events.add("shown") },
      activateProjectView = { events.add("activated") },
    )

    assertThat(events).containsExactly("shown")
  }

  @Test
  fun startupProjectViewIsActivatedWhenItTakesFocus() {
    val events = mutableListOf<String>()

    presentProjectViewOnStartup(
      focusProjectView = true,
      showProjectView = { events.add("shown") },
      activateProjectView = { events.add("activated") },
    )

    assertThat(events).containsExactly("activated")
  }

  @Test
  fun startupEditorFocusIsRestoredOnlyWhenProjectViewTookIt() {
    val startupFocusOwner = object : JPanel() {
      override fun isShowing(): Boolean = true
    }
    val projectView = JPanel()
    val projectViewFocusOwner = JPanel().also { projectView.add(it) }

    assertThat(shouldRestoreStartupEditorFocus(startupFocusOwner, projectViewFocusOwner, projectView)).isTrue()
    assertThat(shouldRestoreStartupEditorFocus(startupFocusOwner, null, projectView)).isTrue()
    assertThat(shouldRestoreStartupEditorFocus(startupFocusOwner, JPanel(), projectView)).isFalse()
    assertThat(shouldRestoreStartupEditorFocus(JPanel(), projectViewFocusOwner, projectView)).isFalse()
  }

  @Test
  fun anEmptyStateThatDoesNotClaimFocusIsNotFocused(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerComponentProvider(disposable)
    manager.closeAllFiles()
    val focusRequests = recordFocusRequests(splitters)

    assertThat(splitters.emptyStateClaimsFocus()).isFalse()

    splitters.requestEmptyStateFocusWhenPresented()
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    waitForEmptyStateComponent(splitters, "The empty state was not presented")

    assertThat(focusRequests).isEmpty()
    assertThat(splitters.emptyStatePreferredFocusedComponent()).isNull()
  }

  @Test
  fun aClaimingEmptyStateClaimsFocusBeforeItIsBuilt(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerFocusClaimingComponentProvider(disposable)
    manager.closeAllFiles()

    // project open asks this while the empty state is still being prepared, so the answer must not depend on a mounted component
    assertThat(splitters.emptyStateClaimsFocus()).isTrue()
    assertThat(splitters.emptyStatePreferredFocusedComponent()).isNull()

    enableRichEmptyStateComponentsWithoutDelay(splitters)
    waitForEmptyStateComponent(splitters, "The claimed empty state was not presented")

    assertThat(splitters.emptyStateClaimsFocus()).isTrue()
    assertThat(splitters.emptyStatePreferredFocusedComponent()).isSameAs(findFocusTargetComponent(splitters))
  }

  @Test
  fun aFocusRequestIsDroppedWhenAnEditorTakesTheAreaOver(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerFocusClaimingComponentProvider(disposable)
    manager.closeAllFiles()
    val focusRequests = recordFocusRequests(splitters)
    splitters.setEmptyStateComponentCreationDelayForTests(NEVER_ELAPSING_CREATION_DELAY)
    splitters.requestEmptyStateFocusWhenPresented()
    splitters.enableRichEmptyStateComponents()

    val file = LightVirtualFile("empty-state-focus-drop.txt", "content")
    manager.openFile(file, false)
    waitForEmptyStateComponentCreation(splitters)

    // the editor that took the area over owns the focus the request was made for
    assertThat(findEmptyStateComponent(splitters)).isNull()
    assertThat(focusRequests).isEmpty()

    manager.closeFile(file)
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    waitForEmptyStateComponent(splitters, "The claimed empty state was not presented after the editor was closed")

    // and closing that editor is a request of its own rather than the dropped one coming back
    assertThat(focusRequests).containsExactly(findFocusTargetComponent(splitters))
  }

  @Test
  fun closingTheLastEditorFocusesAClaimingEmptyState(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerFocusClaimingComponentProvider(disposable)
    manager.closeAllFiles()
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    waitForEmptyStateComponent(splitters, "The claimed empty state was not presented")

    val file = LightVirtualFile("empty-state-last-tab.txt", "content")
    manager.openFile(file, false)
    waitForNoEmptyStateComponent(splitters)
    val focusRequests = recordFocusRequests(splitters)

    manager.closeFile(file)
    waitForEmptyStateComponent(splitters, "The claimed empty state did not come back after the last editor was closed")

    // the focus of the editor the user just closed is inherited by the empty state that replaces it
    assertThat(focusRequests).containsExactly(findFocusTargetComponent(splitters))
  }

  @Test
  fun aClaimIsPendingUntilTheEmptyStateTakesTheFocus(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerFocusClaimingComponentProvider(disposable)
    manager.closeAllFiles()
    val focusRequests = recordFocusRequests(splitters)
    val handedBack = AtomicInteger()
    splitters.setEmptyStateComponentCreationDelayForTests(NEVER_ELAPSING_CREATION_DELAY)
    splitters.beginStartupEmptyStatePresentationHold()

    splitters.requestEmptyStateFocusWhenPresented(onFocusUnclaimed = { handedBack.incrementAndGet() })
    splitters.finishStartupEditorRestore()
    dispatchEventsFor(100.milliseconds)

    // what the tool window manager stands down for while the claimed component is still being prepared
    assertThat(splitters.isEmptyStateFocusRequestPending()).isTrue()

    releaseStartupHoldFromProjectOpensHop(splitters)
    waitForEmptyStateComponent(splitters, "The claimed empty state was not presented")

    assertThat(focusRequests).containsExactly(findFocusTargetComponent(splitters))
    assertThat(splitters.isEmptyStateFocusRequestPending()).isFalse()
    assertThat(handedBack).hasValue(0)
  }

  @Test
  fun aClaimThatIsNeverPresentedHandsTheFocusBack(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerFocusClaimingComponentProviderThatBuildsNothing(disposable)
    manager.closeAllFiles()
    val focusRequests = recordFocusRequests(splitters)
    val handedBack = AtomicInteger()

    // the claim is made on an available provider, before it is known that the provider will build nothing
    assertThat(splitters.emptyStateClaimsFocus()).isTrue()

    splitters.requestEmptyStateFocusWhenPresented(onFocusUnclaimed = { handedBack.incrementAndGet() })
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    waitForEmptyStateComponentCreation(splitters)

    // nothing was presented for the area to focus, so whoever stood down for the claim gets it back
    assertThat(focusRequests).isEmpty()
    assertThat(handedBack).hasValue(1)
    assertThat(splitters.isEmptyStateFocusRequestPending()).isFalse()
  }

  @Test
  fun aClaimingEmptyStateThatNamesNoComponentHandsTheFocusBack(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerFocusClaimingComponentProviderWithoutFocusTarget(disposable)
    manager.closeAllFiles()
    val focusRequests = recordFocusRequests(splitters)
    val handedBack = AtomicInteger()

    splitters.requestEmptyStateFocusWhenPresented(onFocusUnclaimed = { handedBack.incrementAndGet() })
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    waitForEmptyStateComponent(splitters, "The claimed empty state was not presented")

    assertThat(focusRequests).isEmpty()
    assertThat(handedBack).hasValue(1)
  }

  @Test
  fun anEditorTakingTheAreaOverDoesNotHandTheFocusBack(@TestDisposable disposable: Disposable) {
    val splitters = manager.mainSplitters
    registerFocusClaimingComponentProvider(disposable)
    manager.closeAllFiles()
    val focusRequests = recordFocusRequests(splitters)
    val handedBack = AtomicInteger()
    splitters.setEmptyStateComponentCreationDelayForTests(NEVER_ELAPSING_CREATION_DELAY)
    splitters.requestEmptyStateFocusWhenPresented(onFocusUnclaimed = { handedBack.incrementAndGet() })
    splitters.enableRichEmptyStateComponents()

    // the editor that took the area over owns the focus the claim was made for — the README on a project's first open
    val file = LightVirtualFile("empty-state-focus-handback.txt", "content")
    manager.openFile(file, false)
    waitForEmptyStateComponentCreation(splitters)
    dispatchEventsFor(100.milliseconds)

    assertThat(focusRequests).isEmpty()
    assertThat(handedBack).hasValue(0)
    assertThat(splitters.isEmptyStateFocusRequestPending()).isFalse()
  }

  private fun registerDefaultEmptyTextProvider(disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(EditorEmptyTextProvider.EP_NAME, listOf(DefaultEditorEmptyTextProvider()), disposable)
  }

  private fun registerEmptyTextProvider(disposable: Disposable, includeDefaultProvider: Boolean = false) {
    ExtensionTestUtil.maskExtensions(EditorEmptyTextProvider.EP_NAME, buildList {
      add(object : EditorEmptyTextProvider {
        override fun appendEmptyText(splitters: JComponent, sink: EditorEmptyTextSink) {
          sink.appendActionWithShortcuts(PROVIDER_ACTION_TEXT, PROVIDER_ACTION_ID)
        }
      })
      if (includeDefaultProvider) {
        add(DefaultEditorEmptyTextProvider())
      }
    }, disposable)
  }

  private fun registerComponentProvider(
    disposable: Disposable,
    disposedComponents: AtomicInteger = AtomicInteger(),
    includeFallbackProvider: Boolean = false,
    providerCalls: AtomicInteger = AtomicInteger(),
    disposedWithoutLock: AtomicBoolean = AtomicBoolean(),
  ) {
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, buildList {
      add(object : EditorEmptyStateComponentProvider {
        override suspend fun createComponent(splitters: EditorsSplitters): JComponent {
          providerCalls.incrementAndGet()
          return withContext(Dispatchers.EDT) {
            JPanel().apply {
              name = EMPTY_STATE_COMPONENT_NAME
              preferredSize = java.awt.Dimension(320, 40)
            }
          }
        }

        override fun disposeComponent(component: JComponent) {
          // a real provider may release an editor here, through `removeNotify` — a headless test never realizes the hierarchy, so
          // this is where the platform's lock is observable at all
          if (!ApplicationManager.getApplication().isWriteIntentLockAcquired) {
            disposedWithoutLock.set(true)
          }
          disposedComponents.incrementAndGet()
        }
      })
      if (includeFallbackProvider) {
        add(EditorEmptyTextComponentProvider())
      }
    }, disposable)
  }

  /** A provider whose empty state is the focus target of the area it is shown in, and whose focus target is inside its component. */
  private fun registerFocusClaimingComponentProvider(disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, listOf(object : EditorEmptyStateComponentProvider {
      override suspend fun createComponent(splitters: EditorsSplitters): JComponent = withContext(Dispatchers.EDT) {
        JPanel().apply {
          name = EMPTY_STATE_COMPONENT_NAME
          add(JPanel().apply { name = FOCUS_TARGET_COMPONENT_NAME })
        }
      }

      override fun claimsFocus(splitters: EditorsSplitters): Boolean = true

      override fun getPreferredFocusedComponent(component: JComponent): JComponent? {
        return UIUtil.uiTraverser(component).find { it is JComponent && it.name == FOCUS_TARGET_COMPONENT_NAME } as? JComponent
      }
    }), disposable)
  }

  /** A provider that claims the area's focus and then builds nothing, so the claim it made cannot be kept. */
  private fun registerFocusClaimingComponentProviderThatBuildsNothing(disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, listOf(object : EditorEmptyStateComponentProvider {
      override suspend fun createComponent(splitters: EditorsSplitters): JComponent? = null

      override fun claimsFocus(splitters: EditorsSplitters): Boolean = true
    }), disposable)
  }

  /** A provider that claims the area's focus and presents a component that names nothing to focus inside it. */
  private fun registerFocusClaimingComponentProviderWithoutFocusTarget(disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, listOf(object : EditorEmptyStateComponentProvider {
      override suspend fun createComponent(splitters: EditorsSplitters): JComponent = withContext(Dispatchers.EDT) {
        JPanel().apply { name = EMPTY_STATE_COMPONENT_NAME }
      }

      override fun claimsFocus(splitters: EditorsSplitters): Boolean = true
    }), disposable)
  }

  private fun registerNullAndFallbackComponentProviders(disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, buildList {
      add(object : EditorEmptyStateComponentProvider {
        override suspend fun createComponent(splitters: EditorsSplitters): JComponent? = null
      })
      add(EditorEmptyTextComponentProvider())
    }, disposable)
  }

  private fun registerFallbackComponentProvider(disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, listOf(EditorEmptyTextComponentProvider()), disposable)
  }

  private fun registerUnavailableComponentProvider(disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, listOf(object : EditorEmptyStateComponentProvider {
      override fun isAvailable(splitters: EditorsSplitters): Boolean = false

      override suspend fun createComponent(splitters: EditorsSplitters): JComponent {
        error("Unavailable provider must not be invoked")
      }
    }), disposable)
  }

  private fun findEmptyStateComponent(splitters: EditorsSplitters): JComponent? {
    return UIUtil.uiTraverser(splitters).find { it is JComponent && it.name == EMPTY_STATE_COMPONENT_NAME } as? JComponent
  }

  private fun findFocusTargetComponent(splitters: EditorsSplitters): JComponent {
    return checkNotNull(UIUtil.uiTraverser(splitters).find { it is JComponent && it.name == FOCUS_TARGET_COMPONENT_NAME } as? JComponent)
  }

  /** Records what the empty state asks to focus, which is all a headless test can observe of a focus request. */
  private fun recordFocusRequests(splitters: EditorsSplitters): List<JComponent> {
    val requests = CopyOnWriteArrayList<JComponent>()
    splitters.setEmptyStateComponentFocusRequesterForTests { component, transferred ->
      requests.add(component)
      transferred()
    }
    return requests
  }

  private fun findEmptyTextComponent(splitters: EditorsSplitters): JComponent? {
    return UIUtil.uiTraverser(splitters).find { it is JComponent && it.name == EDITOR_EMPTY_TEXT_COMPONENT_NAME } as? JComponent
  }

  private fun findEmptyStateHost(splitters: EditorsSplitters): JComponent? {
    val component = findEmptyStateComponent(splitters) ?: return null
    return component.parent?.parent as? JComponent
  }

  private fun emptyStateLayout(splitters: EditorsSplitters): EditorsSplittersLayout {
    return splitters.layout as EditorsSplittersLayout
  }

  private fun waitForEmptyStateComponentCreation(splitters: EditorsSplitters) {
    PlatformTestUtil.waitWhileBusy { splitters.isEmptyStateComponentCreationPending() }
  }

  private fun waitForEmptyStateComponent(splitters: EditorsSplitters, message: String) {
    try {
      // `waitWhileBusy` — not `waitWithEventsDispatching` — because the test body holds the write-intent lock, and the
      // mount takes it too: only the former releases it while it dispatches
      PlatformTestUtil.waitWhileBusy { findEmptyStateComponent(splitters) == null }
    }
    catch (e: AssertionError) {
      throw AssertionError(message, e)
    }
  }

  private fun waitForEmptyTextComponent(splitters: EditorsSplitters, message: String) {
    try {
      PlatformTestUtil.waitWhileBusy { findEmptyTextComponent(splitters) == null }
    }
    catch (e: AssertionError) {
      throw AssertionError(message, e)
    }
  }

  /**
   * Releases one startup hold from the hop `IdeProjectFrameAllocator` uses: [Dispatchers.EDT] with [ModalityState.any], off the test
   * body's own stack.
   *
   * `Dispatchers.EDT` and not the strict UI dispatcher because releasing may mount or dispose components, which takes the write-intent
   * lock — the strict dispatcher forbids taking it.
   */
  @Suppress("RAW_SCOPE_CREATION")
  private fun releaseStartupHoldFromProjectOpensHop(splitters: EditorsSplitters) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    try {
      var failure: Throwable? = null
      val releaseJob = scope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        try {
          splitters.endStartupEmptyStatePresentationHold()
        }
        catch (e: Throwable) {
          failure = e
        }
      }
      PlatformTestUtil.waitWhileBusy { !releaseJob.isCompleted }
      failure?.let { throw AssertionError("Releasing the startup hold from project open's own hop failed", it) }
    }
    finally {
      scope.cancel()
    }
  }

  private fun waitForProviderCall(providerCalls: AtomicInteger, message: String) {
    try {
      PlatformTestUtil.waitWhileBusy { providerCalls.get() == 0 }
    }
    catch (e: AssertionError) {
      throw AssertionError(message, e)
    }
  }

  private fun waitForNoEmptyStateComponent(splitters: EditorsSplitters) {
    try {
      PlatformTestUtil.waitWhileBusy { findEmptyStateComponent(splitters) != null }
    }
    catch (e: AssertionError) {
      throw AssertionError("The empty state was not disposed", e)
    }
  }

  /** Gives a creation that is not waiting out the delay every chance to mount. */
  private fun dispatchEventsFor(duration: Duration) {
    val deadline = System.nanoTime() + duration.inWholeNanoseconds
    while (System.nanoTime() < deadline) {
      Thread.sleep(10)
      // same reason as in `waitForEmptyStateComponent`: this dispatches with the write-intent lock released, so a creation
      // that is only blocked on that lock cannot pass for one that is waiting out the delay
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
  }

  private fun waitForDeferred(deferred: CompletableDeferred<Unit>) {
    PlatformTestUtil.waitWhileBusy { !deferred.isCompleted }
  }

  private fun emptySplitterStateElement(): Element {
    return Element("state").addContent(Element("leaf"))
  }

  private fun splitterStateElementWithFile(): Element {
    val file = Files.createTempFile(tempPath, "empty-state", ".txt")
    val virtualFile = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file))
    return Element("state").addContent(
      Element("leaf").addContent(
        Element("file").addContent(
          Element(HistoryEntry.TAG).setAttribute(HistoryEntry.FILE_ATTRIBUTE, virtualFile.url),
        ),
      ),
    )
  }

  private fun enableRichEmptyStateComponentsWithoutDelay(splitters: EditorsSplitters) {
    splitters.setEmptyStateComponentCreationDelayForTests(Duration.ZERO)
    splitters.enableRichEmptyStateComponents()
  }

  private fun resetShortcuts(actionId: String, shortcuts: List<Shortcut>) {
    val keymap = activeKeymap()
    runWriteAction {
      keymap.getShortcuts(actionId).forEach { shortcut ->
        keymap.removeShortcut(actionId, shortcut)
      }
      shortcuts.forEach { shortcut ->
        keymap.addShortcut(actionId, shortcut)
      }
    }
  }

  private fun activeKeymap(): Keymap = checkNotNull(KeymapManager.getInstance()).activeKeymap

  private class RecordingEditorEmptyTextPainter : EditorEmptyTextPainter() {
    private val lines = mutableListOf<String>()

    fun appendSearchEverywhereLines(): List<String> {
      advertiseActions(JPanel(), createTextPainter())
      return lines.filter { it.startsWith(IdeBundle.message("empty.text.search.everywhere")) }
    }

    fun appendAdvertisedActionLines(): List<String> {
      advertiseActions(JPanel(), createTextPainter())
      return lines
    }

    fun appendProviderActionLines(): List<String> {
      advertiseActions(JPanel(), createTextPainter())
      return lines.filter { it.startsWith(PROVIDER_ACTION_TEXT) }
    }

    override fun appendLine(painter: UIUtil.TextPainter, line: String) {
      lines.add(line)
    }
  }

  private companion object {
    const val PROVIDER_ACTION_ID: String = "EditorEmptyTextPainterTest.ProviderAction"
    const val PROVIDER_ACTION_TEXT: String = "Provider Action"
    const val EMPTY_STATE_COMPONENT_NAME: String = "EditorEmptyTextPainterTest.EmptyStateComponent"
    const val FOCUS_TARGET_COMPONENT_NAME: String = "EditorEmptyTextPainterTest.FocusTarget"

    /** Long enough that a test which reaches the delay fails on its own timeout rather than passing slowly. */
    val NEVER_ELAPSING_CREATION_DELAY: Duration = 10.minutes
  }
}
