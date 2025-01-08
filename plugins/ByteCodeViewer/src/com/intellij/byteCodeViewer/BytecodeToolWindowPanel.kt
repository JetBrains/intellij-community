// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilBase
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import java.awt.BorderLayout
import java.util.function.Consumer
import javax.swing.JPanel
import kotlin.math.min

internal class BytecodeToolWindowPanel(private val project: Project, private val file: PsiFile) : JPanel(BorderLayout()), Disposable {
  private val bytecodeDocument: Document = EditorFactory.getInstance().createDocument("")

  private val bytecodeEditor: Editor = EditorFactory.getInstance()
    .createEditor(bytecodeDocument, project, JavaClassFileType.INSTANCE, true)

  private var existingLoadBytecodeTask: LoadBytecodeTask? = null

  init {
    bytecodeEditor.setBorder(null)
    add(bytecodeEditor.getComponent())
    WriteAction.run<RuntimeException> {
      setBytecodeText(null, DEFAULT_TEXT)
    }
    setUpListeners()

    queueLoadBytecodeTask {
      val editor = FileEditorManager.getInstance(project)
        .getSelectedTextEditor()
        ?.takeIf { it.virtualFile == file.virtualFile } ?: return@queueLoadBytecodeTask
      updateBytecodeSelection(editor)
    }
  }

  private fun setUpListeners() {
    val messageBus = project.getMessageBus()
    val messageBusConnection = messageBus.connect(this)
    messageBusConnection.subscribe<FileEditorManagerListener>(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        val newFile = event.newFile
        if (newFile == null) return
        if (!isValidFileType(newFile.fileType)) return

        val fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(newFile)
        if (fileEditor !is TextEditor) return
        if (fileEditor.file != file.virtualFile) return
        val sourceEditor = fileEditor.getEditor()

        updateBytecodeSelection(sourceEditor)
      }
    })

    val multicaster = EditorFactory.getInstance().getEventMulticaster()

    multicaster.addCaretListener(object : CaretListener {
      override fun caretPositionChanged(event: CaretEvent) {
        updateBytecodeSelection(event.editor)
      }
    }, this)
  }

  /** Update only text selection ranges. Do not read bytecode again.
   *
   * @param sourceEditor an editor that displays Java code (either real source Java or decompiled Java). If not, this method does nothing.
   */
  @RequiresEdt
  private fun updateBytecodeSelection(sourceEditor: Editor) {
    if (sourceEditor.getCaretModel().getCaretCount() != 1) return
    val virtualFile = sourceEditor.virtualFile ?: return
    if (virtualFile.fileType !== JavaFileType.INSTANCE) return

    val selectedPsiElement = getPsiElement(project, sourceEditor)
    if (selectedPsiElement == null) return
    val containingClass = BytecodeViewerManager.getContainingClass(selectedPsiElement)
    if (containingClass == null) {
      bytecodeEditor.getSelectionModel().removeSelection()
      return
    }

    val sourceStartOffset = sourceEditor.getCaretModel().getCurrentCaret().getSelectionStart()
    val sourceEndOffset = sourceEditor.getCaretModel().getCurrentCaret().getSelectionEnd()
    val sourceDocument = sourceEditor.getDocument()

    val sourceStartLine = sourceDocument.getLineNumber(sourceStartOffset)
    var sourceEndLine = sourceDocument.getLineNumber(sourceEndOffset)
    if (sourceEndLine > sourceStartLine && sourceEndOffset > 0 && sourceDocument.charsSequence[sourceEndOffset - 1] == '\n') {
      sourceEndLine--
    }

    val bytecodeWithDebugInfo = bytecodeDocument.getUserData<String?>(BYTECODE_WITH_DEBUG_INFO)
    if (bytecodeWithDebugInfo == null) {
      LOG.warn("Bytecode with debug information is null. Ensure the bytecode has been generated correctly.")
      return
    }

    val linesRange = mapLines(bytecodeWithDebugInfo, sourceStartLine, sourceEndLine, true)

    if (linesRange == IntRange(0, 0) || linesRange.first < 0 || linesRange.last < 0) {
      bytecodeEditor.getSelectionModel().removeSelection()
      return
    }

    val endSelectionLineIndex = min((linesRange.last + 1), bytecodeDocument.getLineCount())

    val startOffset = bytecodeDocument.getLineStartOffset(linesRange.first)
    val endOffset = min(bytecodeDocument.getLineEndOffset(endSelectionLineIndex), bytecodeDocument.textLength)

    if (bytecodeDocument.textLength <= startOffset || bytecodeDocument.textLength <= endOffset) {
      return
    }

    bytecodeEditor.getCaretModel().moveToOffset(endOffset)
    bytecodeEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE)

    bytecodeEditor.getCaretModel().moveToOffset(startOffset)
    bytecodeEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE)

    bytecodeEditor.getSelectionModel().setSelection(startOffset, endOffset)
  }

  /** Update the contents of the whole editor in the tool window, including reading bytecode again from the currently opened file. */
  @RequiresEdt
  private fun queueLoadBytecodeTask(@RequiresWriteLock @RequiresEdt onAfterBytecodeLoaded: Runnable?) {
    // If a new task was scheduled to update bytecode, we want to cancel the previous one.
    if (existingLoadBytecodeTask != null) {
      if (existingLoadBytecodeTask?.isRunning == true) {
        existingLoadBytecodeTask?.cancel()
      }
      existingLoadBytecodeTask = null
    }
    existingLoadBytecodeTask = LoadBytecodeTask(project) { bytecode ->
      ApplicationManager.getApplication().invokeLater {
        WriteAction.run<RuntimeException> {
          setBytecodeText(bytecode.withDebugInfo, bytecode.withoutDebugInfo)
          onAfterBytecodeLoaded?.run()
        }
      }
    }
    existingLoadBytecodeTask?.queue()
  }

  @RequiresEdt
  @RequiresWriteLock
  private fun setBytecodeText(bytecodeWithDebugInfo: String?, bytecodeWithoutDebugInfo: String) {
    val document = bytecodeEditor.getDocument()
    document.putUserData<String?>(BYTECODE_WITH_DEBUG_INFO, bytecodeWithDebugInfo)
    document.setText(StringUtil.convertLineSeparators(bytecodeWithoutDebugInfo))
  }

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(bytecodeEditor)
  }

  private class LoadBytecodeTask(
    project: Project,
    @param:RequiresEdt private val onBytecodeUpdated: Consumer<Bytecode>,
  ) : Task.Backgroundable(project, BytecodeViewerBundle.message("loading.bytecode"), true) {
    private var myProgressIndicator: ProgressIndicator? = null

    private var myBytecode: Bytecode? = null

    fun cancel() {
      myProgressIndicator?.cancel()
    }

    val isRunning: Boolean get() = myProgressIndicator != null && myProgressIndicator!!.isRunning()

    @RequiresBackgroundThread
    override fun run(indicator: ProgressIndicator) {
      val project = myProject ?: return
      myProgressIndicator = indicator
      myBytecode = ReadAction.computeCancellable<Bytecode, RuntimeException> {
        val selectedEditor = FileEditorManager.getInstance(project).getSelectedTextEditor()
        if (selectedEditor == null) return@computeCancellable null

        val psiFileInEditor = PsiUtilBase.getPsiFileInEditor(selectedEditor, project)
        if (psiFileInEditor == null) return@computeCancellable null

        if (!isValidFileType(psiFileInEditor.getFileType())) return@computeCancellable null

        val selectedPsiElement = getPsiElement(project, selectedEditor)
        if (selectedPsiElement == null) return@computeCancellable null

        val containingClass = BytecodeViewerManager.getContainingClass(selectedPsiElement)
        if (containingClass == null) return@computeCancellable null

        //CancellationUtil.sleepCancellable(1000); // Uncomment if you want to make sure we continue to not freeze the IDE
        getByteCodeVariants(selectedPsiElement)
      }
    }

    @RequiresEdt
    override fun onSuccess() {
      myBytecode?.let { onBytecodeUpdated.accept(it) }
    }

    @RequiresEdt
    override fun onCancel() {
      val progressIndicator = myProgressIndicator ?: return
      LOG.warn("task was canceled, task title: " + title + "task text: " + progressIndicator.getText())
    }
  }

  companion object {
    const val TOOL_WINDOW_ID: String = "Bytecode"

    private val LOG = Logger.getInstance(BytecodeToolWindowPanel::class.java)

    private val BYTECODE_WITH_DEBUG_INFO = Key.create<String>("BYTECODE_WITH_DEBUG_INFO")

    private val DEFAULT_TEXT = BytecodeViewerBundle.message("open.java.file.to.see.bytecode")
  }
}
