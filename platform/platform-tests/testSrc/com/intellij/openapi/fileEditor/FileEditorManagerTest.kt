// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.ide.actions.Switcher
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsState
import com.intellij.mock.Mock
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingModel
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileEditor.impl.DefaultPlatformFileEditorProvider
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.EditorOpenTracker
import com.intellij.openapi.fileEditor.impl.EditorSplitterState
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl
import com.intellij.openapi.fileEditor.impl.blockingWaitForCompositeFileOpen
import com.intellij.openapi.fileEditor.impl.getOrLoadDocumentUnderProgress
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.ExpirableRunnable
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFilePreCloseCheck
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.progress.createProgressPipe
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.executeSomeCoroutineTasksAndDispatchAllInvocationEvents
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.util.io.write
import com.intellij.util.ui.EDT as EdtUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.EventQueue
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Window
import java.awt.event.KeyEvent
import java.util.concurrent.CompletableFuture
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@TestApplication
@Suppress("DEPRECATION")
class FileEditorManagerTest {
  @TestDisposable
  private lateinit var disposable: Disposable
  private val providerDisposables = mutableListOf<Disposable>()

  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()

  private val project: Project
    get() = projectFixture.get()

  private val manager: FileEditorManagerImpl
    get() = fileEditorManagerFixture.get()

  @BeforeEach
  fun clearEditorStateBeforeTest(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    manager.closeAllFiles()
    EditorHistoryManager.getInstance(project).removeAllFiles()
    (FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl).clearSelectedProviders()
  }

  @AfterEach
  fun resetUiSettings(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    manager.closeAllFiles()
    EditorOpenTracker.getInstance(project).pendingEditorOpen().join()
    manager.unsplitAllWindow()
    EditorHistoryManager.getInstance(project).removeAllFiles()
    providerDisposables.forEach { Disposer.dispose(it) }
    providerDisposables.clear()
    (FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl).clearSelectedProviders()
    val template = UISettingsState()
    val uiSettings = UISettings.getInstance().state
    uiSettings.editorTabLimit = template.editorTabLimit
    uiSettings.reuseNotModifiedTabs = template.reuseNotModifiedTabs
    uiSettings.editorTabPlacement = template.editorTabPlacement
  }

  @Test
  @Timeout(30)
  fun testInitFromBackgroundThread(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    assertThat(EventQueue.isDispatchThread()).isFalse()
    val fileEditorManager = manager
    fileEditorManager.loadState(JDOMUtil.load(getXMLText()))

    val (editorComponent, editorState) = fileEditorManager.init()
    assertThat(editorComponent).isSameAs(fileEditorManager.mainSplitters)
    assertThat(editorState).isNotNull()

    val (repeatedEditorComponent, consumedEditorState) = fileEditorManager.init()
    assertThat(repeatedEditorComponent).isSameAs(editorComponent)
    assertThat(consumedEditorState).isNull()
  }

  @Test
  fun testSuspendingOpenReportsDocumentProgressToCaller(): Unit = timeoutRunBlocking {
    val firstRead = AtomicBoolean(true)
    val file = object : LightVirtualFile("progress.txt", PlainTextFileType.INSTANCE, "text") {
      override fun getLength(): Long = 4

      override fun getContent(): CharSequence {
        if (firstRead.compareAndSet(true, false)) {
          assertThat(ApplicationManager.getApplication().isDispatchThread).isFalse()
          val indicator = assertThatNotNull(ProgressManager.getInstance().progressIndicator)
          indicator.text = "Reading the document"
        }
        return super.getContent()
      }
    }
    val pipe = (this + Dispatchers.Unconfined).createProgressPipe()
    val updates = CopyOnWriteArrayList<String?>()
    val collector = launch(Dispatchers.Unconfined) {
      pipe.progressUpdates().collect { updates.add(it.text) }
    }
    try {
      val composite = pipe.collectProgressUpdates {
        manager.openFile(file = file, options = FileEditorOpenOptions())
      }
      assertThat(composite.allEditors).isNotEmpty()
      assertThat(updates).contains("Reading the document")
      withContext(Dispatchers.EDT) {
        composite.allEditors.filterIsInstance<TextEditor>().forEach { EditorTestUtil.waitForLoading(it.editor) }
      }
    }
    finally {
      collector.cancelAndJoin()
      withContext(Dispatchers.EDT) {
        manager.closeFile(file)
      }
    }
  }

  @Test
  fun testCancellingDocumentPreparationDoesNotOpenTab(): Unit = timeoutRunBlocking {
    val started = CompletableDeferred<Unit>()
    val release = CountDownLatch(1)
    val file = object : LightVirtualFile("cancelled.txt", PlainTextFileType.INSTANCE, "text") {
      override fun getLength(): Long = 4

      override fun getContent(): CharSequence {
        started.complete(Unit)
        check(release.await(5, TimeUnit.SECONDS))
        ProgressManager.checkCanceled()
        return super.getContent()
      }
    }
    val opening = async {
      manager.openFile(file = file, options = FileEditorOpenOptions())
    }
    try {
      started.await()
      opening.cancel()
    }
    finally {
      release.countDown()
      opening.cancelAndJoin()
    }

    withContext(Dispatchers.EDT) {
      assertThat(manager.isFileOpen(file)).isFalse()
    }
    assertThat(FileDocumentManager.getInstance().getCachedDocument(file)).isNull()
  }

  @Test
  fun `returns no document for a binary file without a decompiler`(): Unit = timeoutRunBlocking {
    val file = object : LightVirtualFile("binary", UnknownFileType.INSTANCE, "binary") {
      override fun getFileType(): FileType {
        assertFalse(ApplicationManager.getApplication().isReadAccessAllowed)
        return UnknownFileType.INSTANCE
      }
    }

    assertNull(FileDocumentManager.getInstance().getOrLoadDocumentUnderProgress(file))
  }

  @Test
  fun testTabOrder(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    openFiles(getXMLText(fooPinned = false))
    assertOpenFiles("1.txt", "foo.xml", "2.txt", "3.txt")

    manager.closeAllFiles()
    openFiles(getXMLText())
    // regardless of pin, we open files in the same order as it was closed
    assertOpenFiles("1.txt", "foo.xml", "2.txt", "3.txt")
  }

  @Test
  fun testTabLimit(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    UISettings.getInstance().state.editorTabLimit = 2
    openFiles(getXMLText())
    // note that foo.xml is pinned
    assertOpenFiles("foo.xml", "3.txt")
  }

  /**
   * IDEA-309704 Files are closed if tabs exceed the limit
   */
  @Test
  fun testTabLimit2(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    manager.closeAllFiles()
    UISettings.getInstance().state.editorTabLimit = 3
    openSourceFiles("1.txt", "2.txt", "3.txt", "foo.xml")
    assertOpenFiles("2.txt", "3.txt", "foo.xml")
  }

  @Test
  fun testTabLimitWithJupyterNotebooks(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    openSourceFile("test.ipynb")
    manager.closeAllFiles()
    openSourceFile("1.txt")
    UISettings.getInstance().state.editorTabLimit = 1
    openSourceFile("test.ipynb")
    assertOpenFiles("test.ipynb")
  }

  @Test
  fun testSingleTabLimit(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    UISettings.getInstance().state.editorTabLimit = 1
    openFiles(getXMLText(fooPinned = false))
    assertOpenFiles("3.txt")

    manager.closeAllFiles()

    openFiles(getXMLText())
    // note that foo.xml is pinned
    assertOpenFiles("foo.xml")
    manager.openFile(getSourceFile("3.txt"), null, FileEditorOpenOptions().withRequestFocus())
    // the limit is still 1, but a pinned flag prevents closing the tab, and the actual tab number may exceed the limit
    assertOpenFiles("foo.xml", "3.txt")

    manager.closeAllFiles()

    openSourceFiles("3.txt", "foo.xml")
    assertOpenFiles("foo.xml")
    callTrimToSize()
    assertOpenFiles("foo.xml")
  }

  @Test
  fun testReuseNotModifiedTabs(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val uiSettings = UISettings.getInstance().state
    uiSettings.editorTabLimit = 2
    uiSettings.reuseNotModifiedTabs = false

    openSourceFiles("3.txt", "foo.xml")
    assertOpenFiles("3.txt", "foo.xml")
    uiSettings.editorTabLimit = 1
    callTrimToSize()
    assertOpenFiles("foo.xml")
    uiSettings.editorTabLimit = 2

    manager.closeAllFiles()

    uiSettings.reuseNotModifiedTabs = true
    openSourceFile("3.txt")
    assertOpenFiles("3.txt")
    openSourceFile("foo.xml")
    assertOpenFiles("foo.xml")
  }

  private fun callTrimToSize() {
    for (each: EditorsSplitters in manager.getAllSplitters()) {
      each.trimToSize()
    }
  }

  @Test
  fun testOpenRecentEditorTab(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    registerProvider(MyDumbAwareProvider("mock", MockFileEditorProvider.DEFAULT_FILE_EDITOR_NAME, FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR))

    openFiles("""
                  <component name="FileEditorManager">
                    <leaf>
                      <file pinned="false" current="true" current-in-tab="true">
                        <entry selected="true" file="file://$projectDirMacro/src/1.txt">
                          <provider editor-type-id="mock" selected="true">
                            <state />
                          </provider>
                          <provider editor-type-id="text-editor">
                            <state/>
                          </provider>
                        </entry>
                      </file>
                    </leaf>
                  </component>
                """)
    val selectedEditors = manager.selectedEditors
    assertThat(selectedEditors).hasSize(1)
    assertThat(selectedEditors[0].name).isEqualTo(MockFileEditorProvider.DEFAULT_FILE_EDITOR_NAME)
  }

  @Test
  fun testTrackSelectedEditor(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    registerProvider(MyDumbAwareProvider("mock", MockFileEditorProvider.DEFAULT_FILE_EDITOR_NAME, FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR))
    val file = getSourceFile("1.txt")
    val editors = manager.openFile(file, true)
    assertThat(editors).hasSize(2)
    assertThat(selectedEditorName(file)).isEqualTo("Text")
    manager.setSelectedEditor(file, "mock")
    assertThat(selectedEditorName(file)).isEqualTo(MockFileEditorProvider.DEFAULT_FILE_EDITOR_NAME)

    openSourceFile("2.txt")
    assertThat(selectedEditorName(file)).isEqualTo(MockFileEditorProvider.DEFAULT_FILE_EDITOR_NAME)
  }

  @Test
  fun testStaleSelectionNotificationDoesNotReturnClosedFileToHistory(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = openSourceFile("1.txt", focusEditor = false)
    val staleEditor = staleEditorWithProvider(file)
    val history = EditorHistoryManager.getInstance(project)

    manager.closeFile(file)
    assertThat(manager.isFileOpen(file)).isFalse()
    history.removeAllFiles()
    assertThat(history.fileList).isEmpty()

    // no entry created should own a pointer nobody could dispose anymore
    val pointerTracker = VirtualFilePointerTracker()
    publishSelectionChangedFrom(staleEditor)
    pointerTracker.assertPointersAreDisposed()
    assertThat(history.fileList).isEmpty()
  }

  @Test
  fun testWindowClosingRetainsOtherWindows(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = openSourceFile("1.txt", focusEditor = false)
    val primaryWindow = currentWindow()
    val secondaryWindow = createVerticalSplitter(primaryWindow)
    manager.createSplitter(SwingConstants.VERTICAL, secondaryWindow)
    manager.closeFile(file, primaryWindow)
    assertThat(manager.windows).hasSize(2)
  }

  @Test
  fun testCloseFileWithChecksVetoesSingleClose(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = openSourceFile("1.txt")
    val window = currentWindow()
    var checkedFile: VirtualFile? = null
    registerPreCloseCheck(object : VirtualFilePreCloseCheck {
      override fun canCloseFile(file: VirtualFile): Boolean {
        checkedFile = file
        return false
      }
    })

    assertThat(manager.closeFileWithChecks(file, window)).isFalse()

    assertThat(checkedFile).isEqualTo(file)
    assertOpenFiles("1.txt")
  }

  @Test
  fun testCloseFilesWithChecksRunsBatchCheckOnceAndCancelsWholeCloseSet(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    openSourceFiles("1.txt", "2.txt")
    val window = currentWindow()
    var singleChecks = 0
    var batchFiles: List<VirtualFile> = emptyList()
    registerPreCloseCheck(object : VirtualFilePreCloseCheck {
      override fun canCloseFile(file: VirtualFile): Boolean {
        singleChecks++
        return true
      }

      override fun canCloseFiles(files: Collection<VirtualFile>): Boolean {
        batchFiles = files.toList()
        return false
      }
    })

    assertThat(manager.closeFilesWithChecks(window.allComposites.map { Pair.create(it, window) })).isFalse()

    assertThat(singleChecks).isZero()
    assertThat(batchFiles.map { it.name }).containsExactly("1.txt", "2.txt")
    assertOpenFiles("1.txt", "2.txt")
  }

  @Test
  fun testCloseFilesWithChecksClosesWholeSetAfterBatchCheck(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    openSourceFiles("1.txt", "2.txt")
    val window = currentWindow()
    var batchChecks = 0
    registerPreCloseCheck(object : VirtualFilePreCloseCheck {
      override fun canCloseFile(file: VirtualFile): Boolean = true

      override fun canCloseFiles(files: Collection<VirtualFile>): Boolean {
        batchChecks++
        return true
      }
    })

    assertThat(manager.closeFilesWithChecks(window.allComposites.map { Pair.create(it, window) })).isTrue()

    assertThat(batchChecks).isEqualTo(1)
    assertOpenFiles()
  }

  @Test
  fun testUncheckedCloseBypassesPreCloseCheck(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = openSourceFile("1.txt")
    val window = currentWindow()
    var checked = false
    registerPreCloseCheck(object : VirtualFilePreCloseCheck {
      override fun canCloseFile(file: VirtualFile): Boolean {
        checked = true
        return false
      }
    })

    manager.closeFile(file, window)

    assertThat(checked).isFalse()
    assertOpenFiles()
  }

  @Test
  @Suppress("DEPRECATION")
  fun testSwitcherCloseWithWindowRunsPreCloseCheck(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = openSourceFile("1.txt")
    val window = currentWindow()
    var checkedFile: VirtualFile? = null
    registerPreCloseCheck(object : VirtualFilePreCloseCheck {
      override fun canCloseFile(file: VirtualFile): Boolean {
        checkedFile = file
        return false
      }
    })

    assertThat(Switcher.SwitcherPanel.closeVirtualFileForTest(project, file, window)).isFalse()

    assertThat(checkedFile).isEqualTo(file)
    assertOpenFiles("1.txt")
  }

  @Test
  @Suppress("DEPRECATION")
  fun testSwitcherCloseWithoutWindowRunsBatchPreCloseCheck(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = openSourceFile("1.txt")
    var singleChecks = 0
    var batchFiles: List<VirtualFile> = emptyList()
    registerPreCloseCheck(object : VirtualFilePreCloseCheck {
      override fun canCloseFile(file: VirtualFile): Boolean {
        singleChecks++
        return true
      }

      override fun canCloseFiles(files: Collection<VirtualFile>): Boolean {
        batchFiles = files.toList()
        return false
      }
    })

    assertThat(Switcher.SwitcherPanel.closeVirtualFileForTest(project, file, null)).isFalse()

    assertThat(singleChecks).isZero()
    assertThat(batchFiles).containsExactly(file)
    assertOpenFiles("1.txt")
  }

  @Test
  fun testOpenFileInTablessSplitter(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file1 = openSourceFile("1.txt", focusEditor = false)
    val file2 = openSourceFile("2.txt")
    // 1.txt and selected 2.txt
    val primaryWindow = currentWindow()
    primaryWindow.split(SwingConstants.VERTICAL, true, null, true)

    // 2.txt only, selected and focused
    val secondaryWindow = nextWindow(primaryWindow)
    val secondaryFile2Composite = assertThatNotNull(secondaryWindow.getComposite(file2))
    blockingWaitForCompositeFileOpen(secondaryFile2Composite)
    UISettings.getInstance().editorTabPlacement = UISettings.TABS_NONE
    // here we have to ignore 'searchForSplitter'
    manager.openFile(file1, null, FileEditorOpenOptions().withReuseOpen().withRequestFocus())
    assertThat(primaryWindow.tabCount).isEqualTo(2)
    assertThat(secondaryWindow.tabCount).isEqualTo(2)
    assertThat(primaryWindow.fileList).containsExactly(file1, file2)
    assertThat(secondaryWindow.fileList).containsExactly(file2, file1)
  }

  @Test
  fun testStoringCaretStateForFileWithFoldingsWithNoTabs(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    UISettings.getInstance().editorTabPlacement = UISettings.TABS_NONE
    val file = getSourceFile("Test.java")
    assertThat(file.fileType.name).isEqualTo("JAVA") // otherwise, the folding would be incorrect
    var editor = openSingleTextEditor(file)
    val foldingModel: FoldingModel = editor.foldingModel
    assertThat(foldingModel.allFoldRegions).hasSize(2)
    foldingModel.runBatchFoldingOperation {
      for (region: FoldRegion in foldingModel.allFoldRegions) {
        region.isExpanded = false
      }
    }
    val textLength = editor.document.textLength
    editor.caretModel.moveToOffset(textLength)
    editor.selectionModel.setSelection(textLength - 1, textLength)

    openSourceFile("1.txt", focusEditor = false)
    assertThat(manager.getEditors(file)).hasSize(1)

    editor = openSingleTextEditor(file)
    assertThat(editor.caretModel.offset).isEqualTo(textLength)
    assertThat(editor.selectionModel.selectionStart).isEqualTo(textLength - 1)
    assertThat(editor.selectionModel.selectionEnd).isEqualTo(textLength)
  }

  @Test
  fun testOpenInDumbMode(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    registerProvider(MockFileEditorProvider())
    registerProvider(MyDumbAwareProvider())
    val createdFile = DumbModeTestUtils.computeInDumbModeSynchronously(project) {
      val file = createTempFooBar()
      val editors = manager.openFile(file, false)
      assertThat(editors).describedAs(editors.joinToString { "$it of ${it.javaClass}" }).hasSize(1)
      file
    }

    manager.waitForAsyncUpdateOnDumbModeFinished()
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)
    assertThat(manager.getAllEditorList(createdFile)).hasSize(2)
  }

  @Test
  fun testOpenSpecificTextEditor(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    registerProvider(MyTextEditorProvider("one", 1))
    registerProvider(MyTextEditorProvider("two", 2))
    val file = getSourceFile("Test.java")
    val request = FileEditorOpenRequest.withFocus(true)
    val composite = manager.requestOpenEditor(OpenFileDescriptor(project, file, 1), request).await()
    assertThat(composite.allEditors).hasSize(2)
    assertThat(selectedEditorName(file)).isEqualTo("one")
    val secondEditor = manager.requestOpenTextEditor(OpenFileDescriptor(project, file, 2), request).await()
    assertThat(secondEditor).isNotNull()
    assertThat(selectedEditorName(file)).isEqualTo("two")
  }

  @Test
  fun testHideDefaultEditor(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createTempFooBar()

    registerProvider(MyDefaultEditorProvider("t_default", "default"))

    manager.openFile(file, false)
    assertOpenedFileEditorsNames(file, "default")
    manager.closeAllFiles()

    registerProvider(MyDumbAwareProvider("t_hide_def_1", "hide_def_1", FileEditorPolicy.HIDE_DEFAULT_EDITOR))

    manager.openFile(file, false)
    assertOpenedFileEditorsNames(file, "hide_def_1")
    manager.closeAllFiles()

    registerProvider(MyDumbAwareProvider("t_hide_def_2", "hide_def_2", FileEditorPolicy.HIDE_DEFAULT_EDITOR))

    manager.openFile(file, false)
    assertOpenedFileEditorsNames(file, "hide_def_1", "hide_def_2")
    manager.closeAllFiles()

    registerProvider(MyDumbAwareProvider("t_passive", "passive", FileEditorPolicy.NONE))

    manager.openFile(file, false)
    assertOpenedFileEditorsNames(file, "hide_def_1", "hide_def_2", "passive")
    assertThat(manager.getAllEditorList(file)).hasSize(3)
  }

  @Test
  fun testHideOtherEditors(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createTempFooBar()

    registerProvider(MyDefaultEditorProvider("t_default", "default"))
    registerProvider(MockFileEditorProvider("t_passive", "passive", FileEditorPolicy.NONE))
    registerProvider(MyDumbAwareProvider("t_hide_default", "hide_default", FileEditorPolicy.HIDE_DEFAULT_EDITOR))
    registerProvider(MyDumbAwareProvider("t_hide_others_1", "hide_others_1", FileEditorPolicy.HIDE_OTHER_EDITORS))

    manager.openFile(file, false)
    assertOpenedFileEditorsNames(file, "hide_others_1")
    manager.closeAllFiles()

    registerProvider(MyDumbAwareProvider("t_hide_others_2", "hide_others_2", FileEditorPolicy.HIDE_OTHER_EDITORS))
    registerProvider(MyDumbAwareProvider("t_hide_others_3", "hide_others_3", FileEditorPolicy.HIDE_OTHER_EDITORS))

    manager.openFile(file, false)
    assertOpenedFileEditorsNames(file, "hide_others_1", "hide_others_2", "hide_others_3")
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("activeSplitterNavigationCases")
  fun testOpenInActiveSplitter(
    caseName: String,
    openInInactiveSplitter: Boolean,
    useCurrentWindow: Boolean,
    expectedTabCount: Int,
  ): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    if (!openInInactiveSplitter) {
      (AdvancedSettings.getInstance() as AdvancedSettingsImpl)
        .setSetting(FileEditorManagerImpl.EDITOR_OPEN_INACTIVE_SPLITTER, false, disposable)
    }

    val (file, secondaryWindow) = createSecondaryWindowWithSecondFileSelected()
    OpenFileDescriptor(project, file)
      .apply { if (useCurrentWindow) setUseCurrentWindow(true) }
      .navigate(true)
    assertThat(secondaryWindow.tabCount).describedAs(caseName).isEqualTo(expectedTabCount)
  }

  @Test
  fun testGetPreviousWindow(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    openSourceFile("1.txt", focusEditor = false)
    val currentWindow = currentWindow()
    val expectedFile = openSourceFile("2.txt", focusEditor = false)
    createVerticalSplitter(currentWindow)

    val actualFile = assertThatNotNull(manager.getPrevWindow(currentWindow)).selectedFile
    assertThat(actualFile).isEqualTo(expectedFile)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun testFileEditorOpenRequestOptions(suspending: Boolean): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val exManager: FileEditorManagerEx = manager

    val file = getSourceFile("1.txt")
    val file2 = getSourceFile("2.txt")

    exManager.openFile(file, false)
    val primaryWindow = currentWindow(exManager)
    val secondaryWindow = createVerticalSplitter(primaryWindow, exManager)
    val request = FileEditorOpenRequest.withFocus(true).withPin(true)
    val composite = if (suspending) exManager.openFileInWindow(file2, secondaryWindow, request)
                    else exManager.requestOpenFileInWindow(file2, secondaryWindow, request).await()
    assertThat(composite.allEditors).isNotEmpty()
    assertThat(secondaryWindow.isFileOpen(file2)).isTrue()
    assertThat(secondaryWindow.getComposite(file2)!!.isPinned).isTrue()
    exManager.closeFile(file, secondaryWindow)
  }

  @Test
  fun testFileEditorOpenRequestUsesManagedMode() {
    assertThat(FileEditorOpenRequest.defaults().openMode).isEqualTo(FileEditorOpenMode.MANAGED)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun testExplicitRequestPreservesOptionsInRightSplit(suspending: Boolean): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val first = getSourceFile("1.txt")
      val second = getSourceFile("2.txt")
      manager.requestOpenFile(first, FileEditorOpenRequest.defaults()).await()
      val request = FileEditorOpenRequest.defaults().withOpenMode(FileEditorOpenMode.RIGHT_SPLIT).withPin(true)
      val composite = if (suspending) manager.openFile(second, request) else manager.requestOpenFile(second, request).await()
      val rightWindow = manager.windows.single { it.isFileOpen(second) }
      assertThat(rightWindow.getComposite(second)).isSameAs(composite)
      assertThat((composite as EditorComposite).isPinned).isTrue()
      assertThat(rightWindow.selectedFile).isEqualTo(second)

      val third = getSourceFile("Test.java")
      manager.currentWindow = manager.windows.single { it !== rightWindow }
      val backgroundRequest = request.withSelectAsCurrent(false)
      val background = if (suspending) manager.openFile(third, backgroundRequest)
                       else manager.requestOpenFile(third, backgroundRequest).await()
      assertThat(rightWindow.getComposite(third)).isSameAs(background)
      assertThat((background as EditorComposite).isPinned).isTrue()
      assertThat(rightWindow.selectedFile).isEqualTo(second)
    }
  }

  @ParameterizedTest
  @CsvSource("false,false,false", "false,false,true", "false,true,false", "false,true,true",
             "true,false,false", "true,false,true", "true,true,false", "true,true,true")
  fun testNewRightSplitSelection(suspending: Boolean, selectAsCurrent: Boolean, requestFocus: Boolean): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      manager.requestOpenFile(getSourceFile("1.txt")).await()
      val originalWindow = manager.currentWindow
      val focusManager = RecordingFocusManager(IdeFocusManager.getGlobalInstance())
      ApplicationManager.getApplication().replaceService(IdeFocusManager::class.java, focusManager, disposable)
      val file = getSourceFile("2.txt")
      val request = FileEditorOpenRequest.defaults().withOpenMode(FileEditorOpenMode.RIGHT_SPLIT)
        .withSelectAsCurrent(selectAsCurrent).withRequestFocus(requestFocus)
      val composite = if (suspending) manager.openFile(file, request) else manager.requestOpenFile(file, request).await()
      val newWindow = manager.windows.single { it !== originalWindow }
      assertThat(newWindow.selectedComposite).isSameAs(composite)
      assertThat(newWindow.selectedFile).isEqualTo(file)
      val expectedWindow = if (selectAsCurrent) newWindow else originalWindow
      assertThat(manager.currentWindow).isSameAs(expectedWindow)
      executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)
      assertThat(manager.currentWindow).isSameAs(expectedWindow)
      if (!requestFocus || !selectAsCurrent) {
        assertThat(focusManager.requestedComponents).isEmpty()
      }
    }
  }

  @Suppress("OVERRIDE_DEPRECATION")
  private class RecordingFocusManager(private val delegate: IdeFocusManager) : IdeFocusManager() {
    val requestedComponents = mutableListOf<Component>()

    override fun requestFocus(component: Component, forced: Boolean): ActionCallback {
      requestedComponents.add(component)
      return ActionCallback.DONE
    }

    override fun getFocusTargetFor(component: JComponent): JComponent? = delegate.getFocusTargetFor(component)

    override fun doWhenFocusSettlesDown(runnable: Runnable) = delegate.doWhenFocusSettlesDown(runnable)

    override fun doWhenFocusSettlesDown(runnable: Runnable, modality: ModalityState) =
      delegate.doWhenFocusSettlesDown(runnable, modality)

    override fun doWhenFocusSettlesDown(runnable: ExpirableRunnable) = delegate.doWhenFocusSettlesDown(runnable)

    override fun getFocusedDescendantFor(component: Component): Component? = delegate.getFocusedDescendantFor(component)

    override fun isFocusTransferEnabled(): Boolean = delegate.isFocusTransferEnabled

    override fun getFocusOwner(): Component? = delegate.focusOwner

    override fun runOnOwnContext(context: DataContext, runnable: Runnable) = delegate.runOnOwnContext(context, runnable)

    override fun getLastFocusedFor(frame: Window?): Component? = delegate.getLastFocusedFor(frame)

    override fun getLastFocusedFrame(): IdeFrame? = delegate.lastFocusedFrame

    override fun getLastFocusedIdeWindow(): Window? = delegate.lastFocusedIdeWindow

    override fun toFront(component: JComponent) = delegate.toFront(component)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun testLegacyRightSplitWithoutFocus(existingSplit: Boolean): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      manager.requestOpenFile(getSourceFile("1.txt")).await()
      val originalWindow = manager.currentWindow
      if (existingSplit) {
        manager.splitters.openInRightSplit(getSourceFile("2.txt"), false)
        manager.currentWindow = originalWindow
      }
      val file = getSourceFile("Test.java")
      val rightWindow = manager.splitters.openInRightSplit(file, false)!!
      assertThat(rightWindow.selectedFile).isEqualTo(file)
      val expectedWindow = if (existingSplit) rightWindow else originalWindow
      assertThat(manager.currentWindow).isSameAs(expectedWindow)
      executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)
      assertThat(manager.currentWindow).isSameAs(expectedWindow)
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun testExplicitWindowValidationAndDisposal(suspending: Boolean): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      manager.requestOpenFile(getSourceFile("1.txt")).await()
      val file = getSourceFile("2.txt")
      val window = manager.splitters.openInRightSplit(file, false)!!
      val request = FileEditorOpenRequest.defaults()
      val foreignManager = Mock.MyFileEditorManager()
      assertFailsWith<IllegalArgumentException> {
        if (suspending) foreignManager.openFileInWindow(file, window, request)
        else foreignManager.requestOpenFileInWindow(file, window, request)
      }
      assertFailsWith<IllegalArgumentException> {
        val conflictingRequest = request.withOpenMode(FileEditorOpenMode.RIGHT_SPLIT)
        if (suspending) manager.openFileInWindow(file, window, conflictingRequest)
        else manager.requestOpenFileInWindow(file, window, conflictingRequest)
      }
      window.closeFile(file)
      assertThat(window.isDisposed).isTrue()
      val composite = if (suspending) manager.openFileInWindow(file, window, request)
                      else manager.requestOpenFileInWindow(file, window, request).await()
      assertThat(manager.currentWindow!!.getComposite(file)).isSameAs(composite)
      assertThat(manager.currentWindow).isNotSameAs(window)
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun testNavigationRequestOwnsOpeningFlags(suspending: Boolean): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val first = getSourceFile("1.txt")
      val target = getSourceFile("Test.java")
      manager.requestOpenFile(first, FileEditorOpenRequest.defaults()).await()
      val descriptor = OpenFileDescriptor(project, target, 5).apply {
        isUseCurrentWindow = true
        isUsePreviewTab = true
      }
      val request = FileEditorOpenRequest.fromDescriptor(descriptor).withSelectAsCurrent(false).withPin(true)
      descriptor.isUseCurrentWindow = false
      descriptor.isUsePreviewTab = false
      val editor = if (suspending) manager.openTextEditor(descriptor, request)
                   else manager.requestOpenTextEditor(descriptor, request).await()
      assertThat(editor).isNotNull()
      assertThat(editor!!.caretModel.offset).isEqualTo(5)
      assertThat(manager.currentWindow!!.selectedFile).isEqualTo(first)
      assertThat(manager.getComposite(target)!!.isPinned).isTrue()
      assertThat(request.reuseOpen).isFalse()
      assertThat(request.usePreviewTab).isTrue()
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun testNavigationUsesCapturedDescriptorFlags(suspending: Boolean): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val file = getSourceFile("1.txt")
      val descriptor = OpenFileDescriptor(project, file).apply {
        isUseCurrentWindow = true
        isUsePreviewTab = true
      }
      val request = FileEditorOpenRequest.fromDescriptor(descriptor)
      descriptor.isUseCurrentWindow = false
      descriptor.isUsePreviewTab = false
      val settings = UISettings.getInstance()
      val oldPreviewSetting = settings.openInPreviewTabIfPossible
      settings.openInPreviewTabIfPossible = true
      try {
        val composite = if (suspending) manager.openEditor(descriptor, request)
                        else manager.requestOpenEditor(descriptor, request).await()
        assertThat((composite as EditorComposite).isPreview).isTrue()
      }
      finally {
        settings.openInPreviewTabIfPossible = oldPreviewSetting
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  @Timeout(30)
  fun testRequestOpenFileDoesNotWait(explicitRequest: Boolean): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = getSourceFile("1.txt")
    val creationStarted = CompletableDeferred<Unit>()
    val proceedWithCreation = CompletableDeferred<Unit>()
    val provider = object : AsyncFileEditorProvider {
      override fun accept(project: Project, fileToAccept: VirtualFile): Boolean = fileToAccept == file
      override fun acceptRequiresReadAction(): Boolean = false
      override fun getEditorTypeId(): String = "request-overload"
      override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
      override fun createEditor(project: Project, file: VirtualFile): FileEditor = error("Use asynchronous creation")

      override suspend fun createFileEditor(
        project: Project,
        file: VirtualFile,
        document: Document?,
        editorCoroutineScope: CoroutineScope,
      ): FileEditor {
        creationStarted.complete(Unit)
        proceedWithCreation.await()
        return withContext(Dispatchers.EDT) {
          MyTextEditor(file, checkNotNull(document), "request-overload", 0)
        }
      }
    }
    registerProvider(provider)

    val baseManager: FileEditorManager = manager
    val future = if (explicitRequest) baseManager.requestOpenFile(file, FileEditorOpenRequest.defaults().withReuseOpen(true))
                 else baseManager.requestOpenFile(file)
    creationStarted.await()
    assertThat(future.isDone).isFalse()
    proceedWithCreation.complete(Unit)
    val composite = future.await()
    assertThat(composite.allEditors).hasSize(1)
    assertThat(composite.allProviders).containsExactly(provider)
  }

  @Test
  fun testRequestOpenTextEditorCompletesOnEdt(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = getSourceFile("1.txt")
    val creationStarted = CompletableDeferred<Unit>()
    val proceedWithCreation = CompletableDeferred<Unit>()

    registerProvider(object : AsyncFileEditorProvider {
      override fun accept(project: Project, fileToAccept: VirtualFile): Boolean = fileToAccept == file

      override fun acceptRequiresReadAction(): Boolean = false

      override fun getEditorTypeId(): String = "request-edt-completion"

      override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

      override fun createEditor(project: Project, file: VirtualFile): FileEditor = throw UnsupportedOperationException()

      override suspend fun createFileEditor(
        project: Project,
        file: VirtualFile,
        document: Document?,
        editorCoroutineScope: CoroutineScope,
      ): FileEditor {
        creationStarted.complete(Unit)
        proceedWithCreation.await()
        return withContext(Dispatchers.EDT) {
          MyTextEditor(file, checkNotNull(document), "request-edt-completion", 0)
        }
      }
    })

    // the callback is registered before the future completes, so it runs on the thread which completes it
    val completedOnEdt = CompletableDeferred<Boolean>()
    manager.requestOpenTextEditor(OpenFileDescriptor(project, file), FileEditorOpenRequest.defaults())
      .thenAccept { completedOnEdt.complete(EdtUtil.isCurrentThreadEdt()) }
    creationStarted.await()
    proceedWithCreation.complete(Unit)

    assertThat(completedOnEdt.await()).isTrue()
  }

  @Test
  fun testRequestOpenFileHonorsRightSplitMode(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val exManager: FileEditorManagerEx = manager
    val file = getSourceFile("1.txt")
    val file2 = getSourceFile("2.txt")

    exManager.requestOpenFile(file, FileEditorOpenRequest.defaults()).await()
    assertThat(exManager.windowSplitCount).isEqualTo(1)

    exManager.requestOpenFile(file2, FileEditorOpenRequest.defaults().withOpenMode(FileEditorOpenMode.RIGHT_SPLIT)).await()
    assertThat(exManager.windowSplitCount).isEqualTo(2)
  }

  @ParameterizedTest
  @CsvSource("file,false", "file,true", "editor,false", "editor,true", "text,false", "text,true")
  fun testRequestCapturesOnlyManagedMode(api: String, explicitDefault: Boolean): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      manager.requestOpenFile(getSourceFile("1.txt"), FileEditorOpenRequest.defaults()).await()
      assertThat(manager.windowSplitCount).isEqualTo(1)
      val descriptor = OpenFileDescriptor(project, getSourceFile("2.txt"))
      val publicManager: FileEditorManager = manager
      val request = FileEditorOpenRequest.fromDescriptor(descriptor)
        .withOpenMode(if (explicitDefault) FileEditorOpenMode.DEFAULT else FileEditorOpenMode.MANAGED)
      lateinit var submitted: CompletableFuture<*>
      dispatchOpenInRightSplitGesture(disposable) {
        submitted = when (api) {
          "file" -> publicManager.requestOpenFile(descriptor.file, request)
          "editor" -> publicManager.requestOpenEditor(descriptor, request)
          else -> publicManager.requestOpenTextEditor(descriptor, request)
        }
      }
      submitted.await()
      assertThat(manager.windowSplitCount).isEqualTo(if (explicitDefault) 1 else 2)
      assertThat(request.openMode).isEqualTo(if (explicitDefault) FileEditorOpenMode.DEFAULT else FileEditorOpenMode.MANAGED)
    }

  @ParameterizedTest
  @ValueSource(strings = ["file", "editor", "text"])
  fun testSuspendingOpenIgnoresCurrentGesture(api: String): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    manager.openFile(getSourceFile("1.txt"), FileEditorOpenRequest.defaults())
    val descriptor = OpenFileDescriptor(project, getSourceFile("2.txt"))
    val request = FileEditorOpenRequest.defaults()
    lateinit var opening: Deferred<*>
    dispatchOpenInRightSplitGesture(disposable) {
      opening = async(start = CoroutineStart.UNDISPATCHED) {
        when (api) {
          "file" -> manager.openFile(descriptor.file, request)
          "editor" -> manager.openEditor(descriptor, request)
          else -> manager.openTextEditor(descriptor, request)
        }
      }
    }
    opening.await()
    assertThat(manager.isFileOpen(descriptor.file)).isTrue()
    assertThat(manager.windowSplitCount).isEqualTo(1)
    assertThat(request.openMode).isEqualTo(FileEditorOpenMode.MANAGED)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun testExplicitWindowIgnoresCurrentGesture(suspending: Boolean): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    manager.openFile(getSourceFile("1.txt"), FileEditorOpenRequest.defaults())
    val targetWindow = currentWindow(manager)
    val file = getSourceFile("2.txt")
    val request = FileEditorOpenRequest.defaults()
    lateinit var opening: Deferred<FileEditorComposite>
    dispatchOpenInRightSplitGesture(disposable) {
      opening = async(start = CoroutineStart.UNDISPATCHED) {
        if (suspending) manager.openFileInWindow(file, targetWindow, request)
        else manager.requestOpenFileInWindow(file, targetWindow, request).await()
      }
    }
    opening.await()
    assertThat(targetWindow.isFileOpen(file)).isTrue()
    assertThat(manager.windowSplitCount).isEqualTo(1)
    assertThat(request.openMode).isEqualTo(FileEditorOpenMode.MANAGED)
  }

  @Test
  @Timeout(30)
  fun testAwaitLoadedAfterOpeningTextEditor(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val editor = checkNotNull(manager.requestOpenTextEditor(
      OpenFileDescriptor(project, getSourceFile("1.txt")),
      FileEditorOpenRequest.defaults()).await()
    )
    manager.awaitLoaded(editor)
    assertThat(editor.isDisposed).isFalse()
    assertThat(AsyncEditorLoader.isEditorLoaded(editor)).isTrue()
  }

  @Test
  fun testRequestOpenFileCompletesWhenCompositeCloses(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = getSourceFile("1.txt")
    val creationStarted = CompletableDeferred<Unit>()

    registerProvider(object : AsyncFileEditorProvider {
      override fun accept(project: Project, fileToAccept: VirtualFile): Boolean = fileToAccept == file

      override fun acceptRequiresReadAction(): Boolean = false

      override fun getEditorTypeId(): String = "request-close-while-loading"

      override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

      override fun createEditor(project: Project, file: VirtualFile): FileEditor = throw UnsupportedOperationException()

      override suspend fun createFileEditor(
        project: Project,
        file: VirtualFile,
        document: Document?,
        editorCoroutineScope: CoroutineScope,
      ): FileEditor {
        creationStarted.complete(Unit)
        awaitCancellation()
      }
    })

    val completion = manager.requestOpenFile(file, FileEditorOpenRequest.defaults()).toCompletableFuture()
    creationStarted.await()
    manager.closeFile(file)

    waitUntil("The request must finish when the tab closes") {
      completion.isDone
    }
    assertThat(completion.isCancelled).isTrue()
  }

  @Test
  fun testRequestOpenFileCancellationDoesNotCancelOpening(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = getSourceFile("1.txt")
    val creationStarted = CompletableDeferred<Unit>()
    val proceedWithCreation = CompletableDeferred<Unit>()

    registerProvider(object : AsyncFileEditorProvider {
      override fun accept(project: Project, fileToAccept: VirtualFile): Boolean = fileToAccept == file

      override fun acceptRequiresReadAction(): Boolean = false

      override fun getEditorTypeId(): String = "request-cancel-wait"

      override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

      override fun createEditor(project: Project, file: VirtualFile): FileEditor = throw UnsupportedOperationException()

      override suspend fun createFileEditor(
        project: Project,
        file: VirtualFile,
        document: Document?,
        editorCoroutineScope: CoroutineScope,
      ): FileEditor {
        creationStarted.complete(Unit)
        proceedWithCreation.await()
        return withContext(Dispatchers.EDT) {
          MyTextEditor(file, checkNotNull(document), "request-cancel-wait", 0)
        }
      }
    })

    val completion = manager.requestOpenFile(file, FileEditorOpenRequest.defaults()).toCompletableFuture()
    creationStarted.await()
    assertThat(completion.cancel(false)).isTrue()
    proceedWithCreation.complete(Unit)

    waitUntil("The editor must open after the request future is canceled") {
      manager.getEditors(file).isNotEmpty()
    }
  }

  @Test
  fun testCloseFileWhileEditorIsBeingCreated(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = getSourceFile("1.txt")
    val creationStarted = CompletableDeferred<Unit>()
    val proceedWithCreation = CompletableDeferred<Unit>()
    val createdEditor = CompletableDeferred<Editor>()

    registerProvider(object : AsyncFileEditorProvider {
      override fun accept(project: Project, fileToAccept: VirtualFile): Boolean = fileToAccept == file

      override fun acceptRequiresReadAction(): Boolean = false

      override fun getEditorTypeId(): String = "close-while-loading"

      override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

      override fun createEditor(project: Project, file: VirtualFile): FileEditor = throw UnsupportedOperationException()

      override suspend fun createFileEditor(
        project: Project,
        file: VirtualFile,
        document: Document?,
        editorCoroutineScope: CoroutineScope,
      ): FileEditor {
        // NonCancellable makes the race deterministic: the editor creation finishes after the file is already closed
        return withContext(NonCancellable) {
          creationStarted.complete(Unit)
          proceedWithCreation.await()
          withContext(Dispatchers.EDT) {
            val textEditor = MyTextEditor(file, checkNotNull(document), "close-while-loading", 0)
            createdEditor.complete(textEditor.editor)
            textEditor
          }
        }
      }
    })

    // the window must exist before the non-waiting open below
    openSourceFile("2.txt", focusEditor = false)
    val window = currentWindow()
    manager.openFileImpl2(window, file, FileEditorOpenOptions(waitForCompositeOpen = false))
    creationStarted.await()
    manager.closeFile(file, window)
    proceedWithCreation.complete(Unit)

    val editor = createdEditor.await()
    waitUntil("editor created after the file was closed must be released") {
      !EditorFactory.getInstance().allEditors.contains(editor)
    }
  }

  /**
   * The real text editor path: [TextEditorWithPreviewProvider] creates the main editor first and only then suspends to build the
   * preview, so a close landing in between used to abandon a fully created `EditorImpl`.
   */
  @Test
  fun testCloseFileWhileSplitPreviewEditorIsBeingCreated(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = getSourceFile("1.txt")
    val previewCreationStarted = CompletableDeferred<Unit>()

    val previewProvider = object : AsyncFileEditorProvider {
      override fun accept(project: Project, fileToAccept: VirtualFile): Boolean = fileToAccept == file

      override fun acceptRequiresReadAction(): Boolean = false

      override fun getEditorTypeId(): String = "split-preview-never-finishes"

      override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.NONE

      override fun createEditor(project: Project, file: VirtualFile): FileEditor = throw UnsupportedOperationException()

      override suspend fun createFileEditor(
        project: Project,
        file: VirtualFile,
        document: Document?,
        editorCoroutineScope: CoroutineScope,
      ): FileEditor {
        previewCreationStarted.complete(Unit)
        // the main editor already exists at this point, so cancelling here is exactly the window that used to leak it
        awaitCancellation()
      }
    }
    registerProvider(object : TextEditorWithPreviewProvider(previewProvider) {})

    // the window must exist before the non-waiting open below
    openSourceFile("2.txt", focusEditor = false)
    val window = currentWindow()
    manager.openFileImpl2(window, file, FileEditorOpenOptions(waitForCompositeOpen = false))
    previewCreationStarted.await()

    val document = readAction { checkNotNull(FileDocumentManager.getInstance().getDocument(file)) }
    assertThat(EditorFactory.getInstance().allEditors.filter { it.document == document }).isNotEmpty()

    manager.closeFile(file, window)
    waitUntil("the main editor of a cancelled split open must be released") {
      EditorFactory.getInstance().allEditors.none { it.document == document }
    }
  }

  /**
   * The non-[AsyncFileEditorProvider] branch of the composite open: the editor is created in one EDT block, and a cancellation
   * arriving right after that block used to discard it.
   */
  @Test
  fun testCloseFileWhileNonAsyncEditorIsBeingCreated(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = getSourceFile("1.txt")
    val blockingEditorCreated = CompletableDeferred<Editor>()

    registerProvider(object : FileEditorProvider, DumbAware {
      override fun accept(project: Project, fileToAccept: VirtualFile): Boolean = fileToAccept == file

      override fun acceptRequiresReadAction(): Boolean = false

      override fun getEditorTypeId(): String = "non-async-close-while-loading"

      override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

      override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val document = checkNotNull(FileDocumentManager.getInstance().getDocument(file))
        return MyTextEditor(file, document, "non-async-close-while-loading", 0)
          .also { blockingEditorCreated.complete(it.editor) }
      }
    })
    // a sibling that never finishes keeps the composite open cancellable after the editor above already exists
    registerProvider(object : AsyncFileEditorProvider {
      override fun accept(project: Project, fileToAccept: VirtualFile): Boolean = fileToAccept == file

      override fun acceptRequiresReadAction(): Boolean = false

      override fun getEditorTypeId(): String = "sibling-never-finishes"

      override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.NONE

      override fun createEditor(project: Project, file: VirtualFile): FileEditor = throw UnsupportedOperationException()

      override suspend fun createFileEditor(
        project: Project,
        file: VirtualFile,
        document: Document?,
        editorCoroutineScope: CoroutineScope,
      ): FileEditor = awaitCancellation()
    })

    // the window must exist before the non-waiting open below
    openSourceFile("2.txt", focusEditor = false)
    val window = currentWindow()
    manager.openFileImpl2(window, file, FileEditorOpenOptions(waitForCompositeOpen = false))

    val editor = blockingEditorCreated.await()
    manager.closeFile(file, window)
    waitUntil("editor of a cancelled open must be released even for a non-async provider") {
      !EditorFactory.getInstance().allEditors.contains(editor)
    }
  }

  @Test
  fun testMustNotAllowToTypeIntoFileRenamedToUnknownExtension(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val ioFile = IoTestUtil.createTestFile("test.txt", "")
    var file: VirtualFile? = null
    try {
      FileUtil.writeToFile(ioFile, byteArrayOf(1, 2, 3, 4, 29)) // to convince IDEA it's binary when renamed to an unknown extension
      file = assertThatNotNull(StandardFileSystems.local().refreshAndFindFileByPath(ioFile.absolutePath))
      assertThat(file.fileType).isEqualTo(PlainTextFileType.INSTANCE)
      FileEditorManager.getInstance(project).openFile(file, true)
      //noinspection SpellCheckingInspection
      HeavyPlatformTestCase.rename(file, "test.unkneownExtensiosn")
      assertThat(file.fileType).isEqualTo(UnknownFileType.INSTANCE)
      assertThat(FileEditorManager.getInstance(project).isFileOpen(file)).isFalse() // must close
    }
    finally {
      try {
        ioFile.delete()
      }
      catch (_: IOException) {
      }
      VfsTestUtil.deleteFile(file!!)
    }
  }

  @Test
  fun testOpenInRightSplitLeavesNoEmptySplitForFileWithoutProviders(): Unit = timeoutRunBlocking {
    val fileWithoutProviders = withContext(Dispatchers.UiWithModelAccess) {
      // a window to split has to exist, and its editor has to be loaded before the test ends, or it outlives the closed composite
      openSingleTextEditor(getSourceFile("1.txt"))
      createTempFooBar()
    }
    assertThat(manager.canOpenFile(fileWithoutProviders)).isFalse()

    val composite = manager.openFile(
      file = fileWithoutProviders,
      options = FileEditorOpenOptions(openMode = FileEditorManagerImpl.OpenMode.RIGHT_SPLIT, requestFocus = true),
    )

    assertThat(composite.allEditors).isEmpty()
    assertThat(manager.windows).hasSize(1)
  }

  /**
   * A requested window wins over an editor of the same file which is already open elsewhere.
   */
  @Test
  fun testRequestedWindowWinsOverAnAlreadyOpenEditor(): Unit = timeoutRunBlocking {
    val (file, secondaryWindow) = withContext(Dispatchers.UiWithModelAccess) {
      createSecondaryWindowWithSecondFileSelected()
    }
    assertThat(secondaryWindow.fileList).doesNotContain(file)

    manager.openFile(file = file, options = FileEditorOpenOptions(reuseOpen = true, window = secondaryWindow))
    assertThat(secondaryWindow.fileList).contains(file)
  }

  /**
   * Without a requested window, the manager reuses the open editor.
   * A right-split batch overrides this behavior.
   */
  @Test
  fun testAlreadyOpenEditorIsReusedWithoutARequestedWindow(): Unit = timeoutRunBlocking {
    val (file, secondaryWindow) = withContext(Dispatchers.UiWithModelAccess) {
      createSecondaryWindowWithSecondFileSelected()
    }

    manager.openFile(file = file, options = FileEditorOpenOptions(reuseOpen = true))
    assertThat(secondaryWindow.fileList).doesNotContain(file)
  }

  /**
   * An editor a deferred selection notification can still reference after the file has been closed:
   * unlike a real text editor, [Mock.MyFileEditor] never becomes invalid.
   */
  private fun staleEditorWithProvider(file: VirtualFile): FileEditorWithProvider {
    val provider = MockFileEditorProvider()
    return FileEditorWithProvider(fileEditor = provider.createEditor(project, file), provider = provider)
  }

  private fun publishSelectionChangedFrom(oldEditorWithProvider: FileEditorWithProvider) {
    // the history update is postponed until all documents are committed, so commit first to get the notification handled synchronously
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).selectionChanged(
      FileEditorManagerEvent(manager = manager, oldEditorWithProvider = oldEditorWithProvider, newEditorWithProvider = null)
    )
  }

  private fun registerProvider(provider: FileEditorProvider) {
    val providerDisposable = Disposer.newDisposable()
    Disposer.register(disposable, providerDisposable)
    providerDisposables.add(providerDisposable)
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.point.registerExtension(provider, providerDisposable)
  }

  private fun registerPreCloseCheck(check: VirtualFilePreCloseCheck) {
    VirtualFilePreCloseCheck.EP_NAME.point.registerExtension(check, disposable)
  }

  private fun getSourceFile(name: String): VirtualFile = getFile("/src/$name")

  private fun openSourceFile(name: String, focusEditor: Boolean = true): VirtualFile {
    val file = getSourceFile(name)
    manager.openFile(file, focusEditor)
    return file
  }

  private fun openSourceFiles(vararg names: String) {
    names.forEach { openSourceFile(it) }
  }

  private fun selectedEditorName(file: VirtualFile): String {
    return assertThatNotNull(manager.getSelectedEditor(file)).name
  }

  private fun currentWindow(fileEditorManager: FileEditorManagerEx = manager): EditorWindow {
    return assertThatNotNull(fileEditorManager.currentWindow)
  }

  private fun nextWindow(window: EditorWindow, fileEditorManager: FileEditorManagerEx = manager): EditorWindow {
    return assertThatNotNull(fileEditorManager.getNextWindow(window))
  }

  private fun createVerticalSplitter(window: EditorWindow = currentWindow(), fileEditorManager: FileEditorManagerEx = manager): EditorWindow {
    fileEditorManager.createSplitter(SwingConstants.VERTICAL, window)
    return nextWindow(window, fileEditorManager)
  }

  private fun createSecondaryWindowWithSecondFileSelected(): SecondaryWindowFixture {
    val file = openSourceFile("1.txt", focusEditor = false)
    val primaryWindow = currentWindow()
    val secondaryWindow = createVerticalSplitter(primaryWindow)
    manager.openFileImpl2(secondaryWindow, getSourceFile("2.txt"), FileEditorOpenOptions().withRequestFocus(true))
    manager.closeFile(file, secondaryWindow)
    return SecondaryWindowFixture(file, secondaryWindow)
  }

  private fun openSingleTextEditor(file: VirtualFile): Editor {
    val editors = manager.openFile(file, false)
    assertThat(editors).hasSize(1)
    val editor = assertThatIsInstanceOf<TextEditor>(editors[0]).editor
    EditorTestUtil.waitForLoading(editor)
    return editor
  }

  private fun getFile(path: String): VirtualFile {
    val fullPath = testDataPath + path
    return assertThatNotNull(StandardFileSystems.local().refreshAndFindFileByPath(fullPath), "Can't find $fullPath")
  }

  private fun createTempFooBar(): VirtualFile {
    val io = Path.of(FileUtil.getTempDirectory(), "/src/foo.bar")
    io.write(byteArrayOf(1, 0, 2, 3))
    return assertThatNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(io), "Can't find $io")
  }

  private fun openFiles(femSerialisedText: String) {
    val rootElement = JDOMUtil.load(femSerialisedText)
    val map = ExpandMacroToPathMap()
    map.addMacroExpand(PathMacroUtil.PROJECT_DIR_MACRO_NAME, testDataPath)
    map.substitute(rootElement, true, true)
    runWithModalProgressBlocking(project, "") {
      manager.mainSplitters.restoreEditors(EditorSplitterState(rootElement))
      manager.mainSplitters.windows().flatMap { it.composites() }.forEach {
        it.waitForAvailable()
      }
    }
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)
  }

  private fun assertOpenFiles(vararg fileNames: String) {
    val names = manager.splitters.getAllComposites().map { it.file.name }
    assertThat(names).containsExactly(*fileNames)
  }

  private fun assertOpenedFileEditorsNames(file: VirtualFile, vararg allNames: String) {
    val editors = manager.getEditors(file)
    assertThat(editors.map { it.name }).containsExactlyInAnyOrder(*allNames)
  }

  private fun <T : Any> assertThatNotNull(actual: T?, description: String? = null): T {
    val assertion = assertThat(actual)
    description?.let { assertion.describedAs(it) }
    assertion.isNotNull()
    return actual!!
  }

  private inline fun <reified T : Any> assertThatIsInstanceOf(actual: Any?): T {
    assertThat(actual).isInstanceOf(T::class.java)
    return actual as T
  }

  private data class SecondaryWindowFixture(
    val file: VirtualFile,
    val secondaryWindow: EditorWindow,
  )

  private class MyDumbAwareProvider : MockFileEditorProvider, DumbAware {
    constructor() : super("dumbAware")

    constructor(editorTypeId: String, fileEditorName: String, policy: FileEditorPolicy) : super(editorTypeId, fileEditorName, policy)
  }

  private class MyDefaultEditorProvider(editorTypeId: String, fileEditorName: String) :
    MockFileEditorProvider(editorTypeId, fileEditorName, FileEditorPolicy.NONE),
    DefaultPlatformFileEditorProvider,
    DumbAware

  private class MyTextEditorProvider(
    private val id: String,
    private val targetOffset: Int,
  ) : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = true

    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
      val document = checkNotNull(FileDocumentManager.getInstance().getDocument(file))
      return MyTextEditor(file, document, id, targetOffset)
    }

    override fun getEditorTypeId(): String = id

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
  }

  private class MyTextEditor(
    private val file: VirtualFile,
    document: Document,
    private val name: String,
    private val targetOffset: Int,
  ) : Mock.MyFileEditor(), TextEditor {
    private val editor: Editor = EditorFactory.getInstance().createEditor(document)

    override fun dispose() {
      try {
        EditorFactory.getInstance().releaseEditor(editor)
      }
      finally {
        super.dispose()
      }
    }

    override fun getComponent(): JComponent = JLabel()

    override fun getName(): String = name

    override fun getEditor(): Editor = editor

    override fun canNavigateTo(navigatable: Navigatable): Boolean {
      return navigatable is OpenFileDescriptor && navigatable.offset == targetOffset
    }

    override fun navigateTo(navigatable: Navigatable) {
    }

    override fun getFile(): VirtualFile = file
  }

  companion object {
    private val projectDirMacro: String = '$' + PathMacroUtil.PROJECT_DIR_MACRO_NAME + '$'

    private val testDataPath: String
      get() = PlatformTestUtil.getPlatformTestDataPath() + "fileEditorManager"

    @JvmStatic
    fun activeSplitterNavigationCases(): List<Arguments> = listOf(
      Arguments.of("default behavior reuses existing splitter", true, false, 1),
      Arguments.of("disabled inactive-splitter setting opens in active splitter", false, false, 2),
      Arguments.of("use current window still opens in active splitter", false, true, 2),
    )

    @Language("XML")
    private fun getXMLText(fooPinned: Boolean = true): String = """
      <component name="FileEditorManager">
          <leaf>
            <file pinned="false" current="false" current-in-tab="false">
              <entry file="file://$projectDirMacro/src/1.txt">
                <provider selected="true" editor-type-id="text-editor">
                  <state line="0" column="0" selection-start="0" selection-end="0" vertical-scroll-proportion="0.0">
                  </state>
                </provider>
              </entry>
            </file>
            <file pinned="$fooPinned" current="false" current-in-tab="false">
              <entry file="file://$projectDirMacro/src/foo.xml">
                <provider selected="true" editor-type-id="text-editor">
                  <state line="0" column="0" selection-start="0" selection-end="0" vertical-scroll-proportion="0.0">
                  </state>
                </provider>
              </entry>
            </file>
            <file pinned="false" current="true" current-in-tab="true">
              <entry file="file://$projectDirMacro/src/2.txt">
                <provider selected="true" editor-type-id="text-editor">
                  <state line="0" column="0" selection-start="0" selection-end="0" vertical-scroll-proportion="0.0">
                  </state>
                </provider>
              </entry>
            </file>
            <file pinned="false" current="false" current-in-tab="false">
              <entry file="file://$projectDirMacro/src/3.txt">
                <provider selected="true" editor-type-id="text-editor">
                  <state line="0" column="0" selection-start="0" selection-end="0" vertical-scroll-proportion="0.0">
                  </state>
                </provider>
              </entry>
            </file>
          </leaf>
        </component>
      """
  }
}

open class MockFileEditorProvider(
  private val editorTypeId: String = "mock",
  private val fileEditorName: String = DEFAULT_FILE_EDITOR_NAME,
  private val policy: FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR,
) : FileEditorProvider {
  override fun getEditorTypeId(): String = editorTypeId

  override fun accept(project: Project, file: VirtualFile): Boolean = true

  override fun acceptRequiresReadAction(): Boolean = false

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return object : Mock.MyFileEditor() {
      override fun getComponent(): JComponent = JLabel()

      override fun getName(): String = fileEditorName

      override fun getFile(): VirtualFile = file
    }
  }

  override fun disposeEditor(editor: FileEditor) {
  }

  override fun getPolicy(): FileEditorPolicy = policy

  companion object {
    const val DEFAULT_FILE_EDITOR_NAME: String = "MockEditor"
  }
}

internal fun dispatchOpenInRightSplitGesture(disposable: Disposable, action: () -> Unit) {
  val shortcut = KeymapManager.getInstance().activeKeymap.getShortcuts(IdeActions.ACTION_OPEN_IN_RIGHT_SPLIT)
    .filterIsInstance<KeyboardShortcut>().firstOrNull()
  assertThat(shortcut).describedAs("The active keymap must bind Open in Right Split").isNotNull()
  val keyStroke = shortcut!!.firstKeyStroke
  val gesture = KeyEvent(JLabel(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                         keyStroke.modifiers, keyStroke.keyCode, KeyEvent.CHAR_UNDEFINED)
  val dispatcherDisposable = Disposer.newDisposable(disposable, "open mode gesture")
  var dispatched = false
  try {
    IdeEventQueue.getInstance().addDispatcher(object : IdeEventQueue.NonLockedEventDispatcher {
      override fun dispatch(e: AWTEvent): Boolean {
        if (e !== gesture) return false
        dispatched = true
        action()
        return true
      }
    }, dispatcherDisposable)
    IdeEventQueue.getInstance().dispatchEvent(gesture)
    assertThat(dispatched).isTrue()
  }
  finally {
    Disposer.dispose(dispatcherDisposable)
  }
}
