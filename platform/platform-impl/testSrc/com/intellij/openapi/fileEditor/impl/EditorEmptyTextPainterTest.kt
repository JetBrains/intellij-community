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
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import kotlin.time.Duration

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
      beforeInit = { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()

  private lateinit var originalShortcuts: Map<String, List<Shortcut>>

  private val manager: FileEditorManagerImpl
    get() = fileEditorManagerFixture.get()

  @BeforeEach
  fun setUp() {
    originalShortcuts = listOf(IdeActions.ACTION_SEARCH_EVERYWHERE, PROMOTED_ACTION_ID)
      .associateWith { activeKeymap().getShortcuts(it).toList() }
  }

  @AfterEach
  fun tearDown() {
    originalShortcuts.forEach { (actionId, shortcuts) -> resetShortcuts(actionId, shortcuts) }
    manager.mainSplitters.setEmptyStateComponentCreationGateForTests(null)
  }

  @Test
  fun searchEverywhereHintIsHiddenWithoutShortcut() {
    resetShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE, emptyList())

    assertThat(RecordingEditorEmptyTextPainter().appendSearchEverywhereLines()).isEmpty()
  }

  @Test
  fun searchEverywhereHintUsesAssignedShortcut() {
    resetShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE, listOf(doubleShiftShortcut))

    val expectedLine = IdeBundle.message("empty.text.search.everywhere") +
                       " <shortcut>" + KeymapUtil.getShortcutText(doubleShiftShortcut) + "</shortcut>"
    assertThat(RecordingEditorEmptyTextPainter().appendSearchEverywhereLines())
      .containsExactly(expectedLine)
  }

  @Test
  fun promotedActionHintIsRenderedBeforeSearchEverywhere(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROMOTED_ACTION_ID, listOf(doubleCtrlShortcut))
    resetShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE, listOf(doubleShiftShortcut))
    registerPromotedActionProvider(disposable)

    val promotedLine = PROMOTED_ACTION_TEXT + " <shortcut>" + KeymapUtil.getShortcutText(doubleCtrlShortcut) + "</shortcut>"
    val searchEverywhereLine = IdeBundle.message("empty.text.search.everywhere") +
                               " <shortcut>" + KeymapUtil.getShortcutText(doubleShiftShortcut) + "</shortcut>"
    assertThat(RecordingEditorEmptyTextPainter().appendAdvertisedActionLines())
      .startsWith(promotedLine, searchEverywhereLine)
  }

  @Test
  fun promotedActionHintUsesAssignedShortcuts(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROMOTED_ACTION_ID, listOf(ctrlBackslashShortcut, doubleCtrlShortcut))
    registerPromotedActionProvider(disposable)

    val lines = RecordingEditorEmptyTextPainter().appendPromotedActionLines()

    val expectedShortcutText = KeymapUtil.getShortcutText(doubleCtrlShortcut) +
                               " " + IdeBundle.message("empty.text.shortcut.separator") + " " +
                               KeymapUtil.getShortcutText(ctrlBackslashShortcut)
    assertThat(lines).containsExactly("$PROMOTED_ACTION_TEXT <shortcut>$expectedShortcutText</shortcut>")
  }

  @Test
  fun promotedActionHintIsHiddenWithoutShortcut(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROMOTED_ACTION_ID, emptyList())
    registerPromotedActionProvider(disposable)

    assertThat(RecordingEditorEmptyTextPainter().appendPromotedActionLines()).isEmpty()
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
  fun componentProviderCoexistsWithEmptyTextHints(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROMOTED_ACTION_ID, listOf(doubleCtrlShortcut))
    resetShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE, listOf(doubleShiftShortcut))
    registerPromotedActionProvider(disposable)
    registerComponentProvider(disposable)

    val splitters = manager.mainSplitters
    manager.closeAllFiles()
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    splitters.updateEmptyStateComponent()
    waitForEmptyStateComponentCreation(splitters)

    val promotedLine = PROMOTED_ACTION_TEXT + " <shortcut>" + KeymapUtil.getShortcutText(doubleCtrlShortcut) + "</shortcut>"
    val searchEverywhereLine = IdeBundle.message("empty.text.search.everywhere") +
                               " <shortcut>" + KeymapUtil.getShortcutText(doubleShiftShortcut) + "</shortcut>"
    assertThat(findEmptyStateComponent(splitters)).isNotNull()
    assertThat(RecordingEditorEmptyTextPainter().appendAdvertisedActionLines())
      .startsWith(promotedLine, searchEverywhereLine)
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

  private fun registerPromotedActionProvider(disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(EditorEmptyTextPromotedActionProvider.EP_NAME, listOf(object : EditorEmptyTextPromotedActionProvider {
      override fun getPromotedAction(splitters: JComponent): EditorEmptyTextPromotedActionProvider.PromotedAction {
        return EditorEmptyTextPromotedActionProvider.PromotedAction(PROMOTED_ACTION_ID, PROMOTED_ACTION_TEXT)
      }
    }), disposable)
  }

  private fun registerComponentProvider(disposable: Disposable, disposedComponents: AtomicInteger = AtomicInteger()) {
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, listOf(object : EditorEmptyStateComponentProvider {
      override suspend fun createComponent(splitters: EditorsSplitters): JComponent = withContext(Dispatchers.EDT) {
        JPanel().apply {
          name = EMPTY_STATE_COMPONENT_NAME
          preferredSize = java.awt.Dimension(320, 40)
        }
      }

      override fun disposeComponent(component: JComponent) {
        disposedComponents.incrementAndGet()
      }
    }), disposable)
  }

  private fun findEmptyStateComponent(splitters: EditorsSplitters): JComponent? {
    return UIUtil.uiTraverser(splitters).find { it is JComponent && it.name == EMPTY_STATE_COMPONENT_NAME } as? JComponent
  }

  private fun waitForEmptyStateComponentCreation(splitters: EditorsSplitters) {
    PlatformTestUtil.waitWhileBusy { splitters.isEmptyStateComponentCreationPending() }
  }

  private fun waitForDeferred(deferred: CompletableDeferred<Unit>) {
    PlatformTestUtil.waitWhileBusy { !deferred.isCompleted }
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
      appendSearchEverywhere(createTextPainter())
      return lines
    }

    fun appendAdvertisedActionLines(): List<String> {
      advertiseActions(JPanel(), createTextPainter())
      return lines
    }

    fun appendPromotedActionLines(): List<String> {
      advertiseActions(JPanel(), createTextPainter())
      return lines.filter { it.startsWith(PROMOTED_ACTION_TEXT) }
    }

    override fun appendLine(painter: UIUtil.TextPainter, line: String) {
      lines.add(line)
    }
  }

  private companion object {
    const val PROMOTED_ACTION_ID: String = "EditorEmptyTextPainterTest.PromotedAction"
    const val PROMOTED_ACTION_TEXT: String = "Promoted Action"
    const val EMPTY_STATE_COMPONENT_NAME: String = "EditorEmptyTextPainterTest.EmptyStateComponent"
  }
}
