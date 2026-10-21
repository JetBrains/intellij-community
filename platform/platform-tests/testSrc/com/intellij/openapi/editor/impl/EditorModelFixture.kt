// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.awaitPendingNavigationAndEditorOpenIfEnabled
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.EditorMouseFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.PathUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

/**
 * Creates an [EditorModelFixture] on [codeInsightFixture].
 * It is the JUnit 5 replacement of the JUnit 3 base `AbstractEditorTest`.
 *
 * Declare the result as an instance property, so that each test gets its own fixture.
 * The fixture sets a [MockFontLayoutService] before the test and resets it after the test.
 */
@TestOnly
fun editorModelFixture(codeInsightFixture: TestFixture<CodeInsightTestFixture>): TestFixture<EditorModelFixture> =
  testFixture("editorModelFixture") { context ->
    val fixture = codeInsightFixture.init()
    val testName = PlatformTestUtil.getTestName(context.extensionContext.requiredTestMethod.name, false)
    val testRootDisposable = Disposer.newCheckedDisposable(context.uniqueId)
    FontLayoutService.setInstance(MockFontLayoutService(EditorModelFixture.TEST_CHAR_WIDTH,
                                                        EditorModelFixture.TEST_LINE_HEIGHT,
                                                        EditorModelFixture.TEST_DESCENT))
    initialized(EditorModelFixture(fixture, testName, testRootDisposable)) {
      try {
        runInEdtAndWait { Disposer.dispose(testRootDisposable) }
      }
      finally {
        FontLayoutService.setInstance(null)
      }
    }
  }

/**
 * Editor model helpers for a JUnit 5 test: fold regions, inlays, soft wraps, carets, and editor actions.
 *
 * Each character is [TEST_CHAR_WIDTH] pixels wide, and each line is [TEST_LINE_HEIGHT] pixels high.
 * Call a helper on the EDT. [runTest] runs a test body on the EDT.
 */
@TestOnly
@Suppress("unused")
class EditorModelFixture internal constructor(
  val fixture: CodeInsightTestFixture,
  /** The test method name without the `test` prefix, as `getTestName(false)` of the JUnit 3 base gives. */
  val testName: String,
  /** It is disposed on the EDT after the test, before [fixture] releases the editor. */
  val testRootDisposable: Disposable,
) {
  val project: Project
    get() = fixture.project

  val editor: Editor
    get() = fixture.editor

  val file: PsiFile
    get() = fixture.file

  /**
   * Runs [test] on the EDT inside a command, as the JUnit 3 base ran each test.
   * Call it from any thread.
   */
  fun <E : Throwable> runTest(test: ThrowableRunnable<E>) {
    var error: Throwable? = null
    runInEdtAndWait {
      CommandProcessor.getInstance().executeCommand(project, {
        try {
          test.run()
        }
        catch (e: Throwable) {
          error = e
        }
      }, null, null)
    }
    error?.let { throw it }
  }

  /**
   * Runs [test] with this fixture as the receiver. See the other overload.
   */
  @JvmSynthetic
  fun runTest(test: EditorModelFixture.() -> Unit) {
    runTest(ThrowableRunnable<Throwable> { test() })
  }

  /**
   * Opens a plain text file with [fileText]. The file name is [testName] with the default extension.
   */
  fun initText(@NonNls fileText: String) {
    init(fileText, PlainTextFileType.INSTANCE)
  }

  /**
   * Opens a file of [type] with [fileText]. The file name is [testName] with the default extension of [type].
   */
  fun init(@NonNls fileText: String, type: FileType) {
    val name = testName + "." + type.defaultExtension
    assertFileTypeResolved(type, name)
    fixture.configureByText(name, fileText)
  }

  /**
   * Opens a file with the name [fileName] and [fileText], and returns its document.
   */
  fun configureFromFileText(@NonNls fileName: String, @NonNls fileText: String): Document {
    fixture.configureByText(fileName, fileText)
    return editor.document
  }

  fun addFoldRegion(startOffset: Int, endOffset: Int, placeholder: String): FoldRegion? {
    var result: FoldRegion? = null
    editor.foldingModel.runBatchFoldingOperation { result = editor.foldingModel.addFoldRegion(startOffset, endOffset, placeholder) }
    return result
  }

  fun addCollapsedFoldRegion(startOffset: Int, endOffset: Int, placeholder: String): FoldRegion {
    val region = addFoldRegion(startOffset, endOffset, placeholder)!!
    toggleFoldRegionState(region, false)
    return region
  }

  fun addCustomFoldRegion(startLine: Int, endLine: Int): CustomFoldRegion? =
    EditorTestUtil.addCustomFoldRegion(editor, startLine, endLine)

  fun addCustomFoldRegion(startLine: Int, endLine: Int, heightInPixels: Int): CustomFoldRegion? =
    EditorTestUtil.addCustomFoldRegion(editor, startLine, endLine, heightInPixels)

  fun addCustomFoldRegion(startLine: Int, endLine: Int, widthInPixels: Int, heightInPixels: Int): CustomFoldRegion? =
    EditorTestUtil.addCustomFoldRegion(editor, startLine, endLine, widthInPixels, heightInPixels)

  fun toggleFoldRegionState(foldRegion: FoldRegion, expanded: Boolean) {
    editor.foldingModel.runBatchFoldingOperation { foldRegion.isExpanded = expanded }
  }

  /**
   * Returns the fold region that starts at [startOffset], or throws an exception.
   */
  fun getFoldRegion(startOffset: Int): FoldRegion {
    val foldRegions = editor.foldingModel.allFoldRegions
    return foldRegions.firstOrNull { it.startOffset == startOffset }
           ?: throw IllegalArgumentException(
             "Can't find fold region with start offset $startOffset. Registered fold regions: ${foldRegions.contentToString()}. " +
             "Document text: '${editor.document.charsSequence}'")
  }

  /**
   * Adds a collapsed fold region for each match of [textToFoldRegexp].
   */
  fun foldOccurrences(textToFoldRegexp: String, placeholder: String) {
    val matcher = textToFoldRegexp.toPattern().matcher(editor.document.charsSequence)
    editor.foldingModel.runBatchFoldingOperation {
      while (matcher.find()) {
        val foldRegion = editor.foldingModel.addFoldRegion(matcher.start(), matcher.end(), placeholder)
        assertNotNull(foldRegion)
        foldRegion!!.isExpanded = false
      }
    }
  }

  fun runFoldingOperation(runnable: Runnable) {
    editor.foldingModel.runBatchFoldingOperation(runnable)
  }

  /**
   * Checks the block selection ranges. Each range is a pair of the start offset and the end offset.
   */
  fun assertSelectionRanges(ranges: Array<IntArray>) {
    val selectionStarts = editor.selectionModel.blockSelectionStarts
    val selectionEnds = editor.selectionModel.blockSelectionEnds
    val actualRanges = Array(selectionStarts.size) { intArrayOf(selectionStarts[it], selectionEnds[it]) }
    assertEquals(ranges.contentDeepToString(), actualRanges.contentDeepToString(), "Wrong selected ranges")
  }

  fun mouse(): EditorMouseFixture = EditorMouseFixture(editor as EditorImpl)

  fun setEditorVisibleSize(widthInChars: Int, heightInChars: Int) {
    EditorTestUtil.setEditorVisibleSize(editor, widthInChars, heightInChars)
  }

  /**
   * Checks the visual positions of the carets and their selection ranges.
   * Each caret and its selection range must be in the same visual line.
   * For each caret, give the line, the caret column, the selection start column, and the selection end column.
   */
  fun verifyCaretsAndSelections(vararg coordinates: Int) {
    val caretCount = coordinates.size / 4
    val carets = editor.caretModel.allCarets
    assertEquals(caretCount, carets.size, "Unexpected caret count")
    for (i in 0 until caretCount) {
      val caret = carets[i]
      val line = coordinates[i * 4]
      assertEquals(VisualPosition(line, coordinates[i * 4 + 1]), caret.visualPosition, "Unexpected position for caret ${i + 1}")
      assertEquals(VisualPosition(line, coordinates[i * 4 + 2]), caret.selectionStartPosition,
                   "Unexpected selection start for caret ${i + 1}")
      assertEquals(VisualPosition(line, coordinates[i * 4 + 3]), caret.selectionEndPosition, "Unexpected selection end for caret ${i + 1}")
    }
  }

  fun verifySoftWrapPositions(vararg positions: Int) {
    val softWrapPositions = editor.softWrapModel.getSoftWrapsForRange(0, editor.document.textLength).map { it.start }
    assertArrayEquals(positions, softWrapPositions.toIntArray())
  }

  fun verifyFoldingState(state: String) {
    assertEquals(state, editor.foldingModel.allFoldRegions.contentToString())
  }

  @JvmOverloads
  fun configureSoftWraps(charCountToWrapAt: Int, useCustomSoftWrapIndent: Boolean = true) {
    EditorTestUtil.configureSoftWraps(editor, charCountToWrapAt, useCustomSoftWrapIndent)
  }

  @JvmOverloads
  fun addInlay(offset: Int, relatesToPrecedingText: Boolean = false): Inlay<*> =
    EditorTestUtil.addInlay(editor, offset, relatesToPrecedingText)

  fun addInlay(offset: Int, widthInPixels: Int): Inlay<*> =
    EditorTestUtil.addInlay(editor, offset, false, widthInPixels)

  /**
   * Adds a block inlay, below the line by default.
   */
  @JvmOverloads
  fun addBlockInlay(offset: Int, showAbove: Boolean = false, widthInPixels: Int = 0): Inlay<*> =
    EditorTestUtil.addBlockInlay(editor, offset, false, showAbove, widthInPixels, null)

  fun addBlockInlay(offset: Int, showAbove: Boolean, relatesToPrecedingText: Boolean): Inlay<*> =
    EditorTestUtil.addBlockInlay(editor, offset, relatesToPrecedingText, showAbove, 0, null)

  fun addBlockInlay(offset: Int, showAbove: Boolean, widthInPixels: Int, heightInPixels: Int): Inlay<*> =
    EditorTestUtil.addBlockInlay(editor, offset, false, showAbove, widthInPixels, heightInPixels)

  fun addAfterLineEndInlay(offset: Int, widthInPixels: Int): Inlay<*> =
    EditorTestUtil.addAfterLineEndInlay(editor, offset, widthInPixels)

  /**
   * Enables the custom soft wraps until [testRootDisposable] is disposed.
   */
  fun setUpCustomWrapSupport() {
    Registry.get("editor.custom.soft.wraps.support.enabled").setValue(true, testRootDisposable)
  }

  fun runWriteCommand(runnable: ThrowableRunnable<*>) {
    WriteCommandAction.writeCommandAction(project).run(runnable)
  }

  /**
   * Runs the editor action [actionId] in a command, as `LightPlatformCodeInsightTestCase.executeAction` does.
   */
  fun executeAction(@NonNls actionId: String) {
    CommandProcessor.getInstance().executeCommand(project, { EditorTestUtil.executeAction(editor, actionId, true) }, "", null,
                                                  editor.document)
    awaitPendingNavigationAndEditorOpenIfEnabled(project)
  }

  /**
   * Types each character of [s]. See the other overload.
   */
  fun type(@NonNls s: String) {
    s.forEach { type(it) }
  }

  /**
   * Types [c], as `LightPlatformCodeInsightTestCase.type` does.
   * A line feed runs the Enter action in a command. Other characters go to the typed action.
   */
  fun type(c: Char) {
    if (c == '\n') {
      executeAction(IdeActions.ACTION_EDITOR_ENTER)
    }
    else {
      @Suppress("DEPRECATION") // LightPlatformCodeInsightTestCase.type uses the same data context
      TypedAction.getInstance().actionPerformed(editor, c, DataManager.getInstance().dataContext)
    }
  }

  fun backspace(): Unit = executeAction(IdeActions.ACTION_EDITOR_BACKSPACE)
  fun delete(): Unit = executeAction(IdeActions.ACTION_EDITOR_DELETE)
  fun deleteLine(): Unit = executeAction(IdeActions.ACTION_EDITOR_DELETE_LINE)
  fun deleteToLineStart(): Unit = executeAction("EditorDeleteToLineStart")
  fun deleteToLineEnd(): Unit = executeAction("EditorDeleteToLineEnd")
  fun ctrlD(): Unit = executeAction(IdeActions.ACTION_EDITOR_DUPLICATE)
  fun copy(): Unit = executeAction(IdeActions.ACTION_EDITOR_COPY)
  fun paste(): Unit = executeAction(IdeActions.ACTION_EDITOR_PASTE)
  fun indent(): Unit = executeAction("EditorIndentLineOrSelection")
  fun unindent(): Unit = executeAction("EditorUnindentSelection")
  fun textStart(): Unit = executeAction(IdeActions.ACTION_EDITOR_TEXT_START)
  fun textEnd(): Unit = executeAction(IdeActions.ACTION_EDITOR_TEXT_END)
  fun home(): Unit = executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START)
  fun end(): Unit = executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
  fun homeWithSelection(): Unit = executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION)
  fun endWithSelection(): Unit = executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION)
  fun previousWord(): Unit = executeAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD)
  fun nextWord(): Unit = executeAction(IdeActions.ACTION_EDITOR_NEXT_WORD)
  fun moveCaretToPreviousWordWithSelection(): Unit = executeAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION)
  fun moveCaretToNextWordWithSelection(): Unit = executeAction(IdeActions.ACTION_EDITOR_NEXT_WORD_WITH_SELECTION)
  fun left(): Unit = executeAction("EditorLeft")
  fun right(): Unit = executeAction("EditorRight")
  fun leftWithSelection(): Unit = executeAction("EditorLeftWithSelection")
  fun rightWithSelection(): Unit = executeAction("EditorRightWithSelection")
  fun up(): Unit = executeAction("EditorUp")
  fun down(): Unit = executeAction("EditorDown")

  /**
   * Checks the file text, and the carets and the selections of the editor,
   * as `LightPlatformCodeInsightTestCase.checkResultByText` does.
   */
  @JvmOverloads
  fun checkResultByText(message: String?, expectedFileText: String, ignoreTrailingSpaces: Boolean = false) {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    ApplicationManager.getApplication().runWriteAction {
      val document = EditorFactory.getInstance().createDocument(expectedFileText)
      if (ignoreTrailingSpaces) {
        (document as DocumentImpl).stripTrailingSpaces(project)
      }
      val carets = EditorTestUtil.extractCaretAndSelectionMarkers(document)
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
      val expectedText = document.text
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      assertEquals(expectedText, file.text, if (message == null) "Text mismatch" else "$message [Text mismatch]")
      EditorTestUtil.verifyCaretAndSelectionState(editor, carets, message)
    }
  }

  fun checkResultByText(expectedFileText: String) {
    checkResultByText(null, expectedFileText)
  }

  companion object {
    /** The character width of [MockFontLayoutService]. It matches the one of [EditorTestUtil.configureSoftWraps]. */
    const val TEST_CHAR_WIDTH: Int = 10
    const val TEST_LINE_HEIGHT: Int = 10
    const val TEST_DESCENT: Int = 2

    const val LOREM_IPSUM: String =
      "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."

    @JvmField
    val SURROGATE_PAIR: String = String(Character.toChars(Character.MIN_SUPPLEMENTARY_CODE_POINT))

    @JvmField
    val HIGH_SURROGATE: String = SURROGATE_PAIR.substring(0, 1)

    @JvmField
    val LOW_SURROGATE: String = SURROGATE_PAIR.substring(1, 2)

    /**
     * Checks that the file name of [path] gives [type].
     */
    @JvmStatic
    fun assertFileTypeResolved(type: FileType, path: String) {
      val name = PathUtil.getFileName(path)
      val fileType = FileTypeManager.getInstance().getFileTypeByFileName(name)
      assertEquals(type, fileType, "$type file type must be in this test classpath, but only $fileType was found by '$name' file name " +
                                   "(with default extension '${fileType.defaultExtension}')")
    }
  }
}
