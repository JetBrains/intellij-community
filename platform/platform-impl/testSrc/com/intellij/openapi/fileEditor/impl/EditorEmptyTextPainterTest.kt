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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
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
import java.awt.image.BufferedImage
import java.nio.file.Files
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
    manager.mainSplitters.setEmptyStateComponentCreationGateForTests(null)
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
  fun componentProviderSuppressesEmptyTextHints(@TestDisposable disposable: Disposable) {
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
    assertThat(RecordingEditorEmptyTextPainter().paintEmptyTextLines(splitters)).isEmpty()
  }

  @Test
  fun componentProviderCreationPendingSuppressesEmptyTextHints(@TestDisposable disposable: Disposable) {
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
      assertThat(findEmptyTextComponent(splitters)).isNull()
      assertThat(RecordingEditorEmptyTextPainter().paintEmptyTextLines(splitters)).isEmpty()
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
    assertThat(RecordingEditorEmptyTextPainter().paintEmptyTextLines(splitters)).isEmpty()
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
    assertThat(RecordingEditorEmptyTextPainter().paintEmptyTextLines(splitters)).isEmpty()
  }

  @Test
  fun unavailableComponentProviderDoesNotSuppressEmptyTextHints(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROVIDER_ACTION_ID, listOf(doubleCtrlShortcut))
    registerEmptyTextProvider(disposable)
    registerUnavailableComponentProvider(disposable)

    val splitters = manager.mainSplitters
    manager.closeAllFiles()
    enableRichEmptyStateComponentsWithoutDelay(splitters)
    splitters.updateEmptyStateComponent()
    waitForEmptyStateComponentCreation(splitters)

    assertThat(findEmptyStateComponent(splitters)).isNull()
    assertThat(RecordingEditorEmptyTextPainter().paintEmptyTextLines(splitters))
      .containsExactly(PROVIDER_ACTION_TEXT + " <shortcut>" + KeymapUtil.getShortcutText(doubleCtrlShortcut) + "</shortcut>")
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
    assertThat(RecordingEditorEmptyTextPainter().paintEmptyTextLines(splitters)).isEmpty()

    val openFilesJob = splitters.openFilesAsync(requestFocus = false)
    PlatformTestUtil.waitWhileBusy { !openFilesJob.isCompleted }
    waitForEmptyStateComponentCreation(splitters)
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
  ) {
    ExtensionTestUtil.maskExtensions(EditorEmptyStateComponentProvider.EP_NAME, buildList {
      add(object : EditorEmptyStateComponentProvider {
        override suspend fun createComponent(splitters: EditorsSplitters): JComponent = withContext(Dispatchers.EDT) {
          JPanel().apply {
            name = EMPTY_STATE_COMPONENT_NAME
            preferredSize = java.awt.Dimension(320, 40)
          }
        }

        override fun disposeComponent(component: JComponent) {
          disposedComponents.incrementAndGet()
        }
      })
      if (includeFallbackProvider) {
        add(EditorEmptyTextComponentProvider())
      }
    }, disposable)
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

    fun paintEmptyTextLines(splitters: JComponent): List<String> {
      val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
      val graphics = image.createGraphics()
      try {
        doPaintEmptyText(splitters, graphics)
      }
      finally {
        graphics.dispose()
      }
      return lines
    }

    override fun appendLine(painter: UIUtil.TextPainter, line: String) {
      lines.add(line)
    }
  }

  private companion object {
    const val PROVIDER_ACTION_ID: String = "EditorEmptyTextPainterTest.ProviderAction"
    const val PROVIDER_ACTION_TEXT: String = "Provider Action"
    const val EMPTY_STATE_COMPONENT_NAME: String = "EditorEmptyTextPainterTest.EmptyStateComponent"
  }
}
