// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsState
import com.intellij.mock.Mock
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingModel
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.FileEditorOpenRequest
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.DefaultPlatformFileEditorProvider
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.EditorSplitterState
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl
import com.intellij.openapi.fileEditor.impl.blockingWaitForCompositeFileOpen
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.pom.Navigatable
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.executeSomeCoroutineTasksAndDispatchAllInvocationEvents
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.io.write
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.IOException
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

@TestApplication
class FileEditorManagerTest {
  @TestDisposable
  private lateinit var disposable: Disposable
  private val providerDisposables = mutableListOf<Disposable>()

  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInit = { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
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
    registerProvider(MockFileEditorProvider())
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
  fun testWindowClosingRetainsOtherWindows(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = openSourceFile("1.txt", focusEditor = false)
    val primaryWindow = currentWindow()
    val secondaryWindow = createVerticalSplitter(primaryWindow)
    manager.createSplitter(SwingConstants.VERTICAL, secondaryWindow)
    manager.closeFile(file, primaryWindow)
    assertThat(manager.windows).hasSize(2)
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
    manager.openTextEditor(OpenFileDescriptor(project, file, 1), true)
    assertThat(selectedEditorName(file)).isEqualTo("one")
    manager.openTextEditor(OpenFileDescriptor(project, file, 2), true)
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

  @Test
  fun testFileEditorOpenRequestOptions(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val exManager: FileEditorManagerEx = manager

    val file = getSourceFile("1.txt")
    val file2 = getSourceFile("2.txt")

    exManager.openFile(file, false)
    val primaryWindow = currentWindow(exManager)
    val secondaryWindow = createVerticalSplitter(primaryWindow, exManager)
    exManager.openFile(
      file2,
      FileEditorOpenRequest()
        .withTargetWindow(secondaryWindow)
        .withSelectAsCurrent(true)
        .withPin(true)
        .withRequestFocus(true),
    )
    exManager.closeFile(file, secondaryWindow)
  }

  @Test
  fun testMustNotAllowToTypeIntoFileRenamedToUnknownExtension(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val ioFile = IoTestUtil.createTestFile("test.txt", "")
    var file: VirtualFile? = null
    try {
      FileUtil.writeToFile(ioFile, byteArrayOf(1, 2, 3, 4, 29)) // to convince IDEA it's binary when renamed to an unknown extension
      file = assertThatNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile))
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

  private fun registerProvider(provider: FileEditorProvider) {
    val providerDisposable = Disposer.newDisposable()
    Disposer.register(disposable, providerDisposable)
    providerDisposables.add(providerDisposable)
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.point.registerExtension(provider, providerDisposable)
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
    return assertThatNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath), "Can't find $fullPath")
  }

  private fun createTempFooBar(): VirtualFile {
    val io = Path.of(FileUtil.getTempDirectory(), "/src/foo.bar")
    io.write(byteArrayOf(1, 0, 2, 3))
    return assertThatNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(io), "Can't find $io")
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
