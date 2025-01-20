// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.math.min

internal class BytecodeToolWindowPanel(private val project: Project, private val file: PsiFile) : JPanel(BorderLayout()), Disposable {
  private val bytecodeEditor: Editor = EditorFactory.getInstance()
    .createEditor(EditorFactory.getInstance().createDocument(""), project, JavaClassFileType.INSTANCE, true)
    .also { bytecodeEditor ->
      setBorder(null)
      val byteCode = generateBytecodeText() ?: return@also
      runWriteAction {
        bytecodeEditor.document.putUserData<String?>(BYTECODE_WITH_DEBUG_INFO, byteCode.withDebugInfo)
        bytecodeEditor.document.setText(StringUtil.convertLineSeparators(byteCode.withoutDebugInfo))
      }

      val sourceEditor = FileEditorManager.getInstance(project).getSelectedTextEditor()?.takeIf {
        it.virtualFile == file.virtualFile
      } ?: return@also

      updateBytecodeSelection(sourceEditor, bytecodeEditor)

      EditorFactory.getInstance().getEventMulticaster().addSelectionListener(object : SelectionListener {
        override fun selectionChanged(e: SelectionEvent) {
          if (e.editor != sourceEditor) return
          updateBytecodeSelection(sourceEditor, bytecodeEditor)
        }
      }, this@BytecodeToolWindowPanel)
    }

  init {
    add(bytecodeEditor.getComponent())
  }

  /** Update only text selection ranges. Do not read bytecode again.
   *
   * @param sourceEditor an editor that displays Java code (either real source Java or decompiled Java). If not, this method does nothing.
   */
  @RequiresEdt
  private fun updateBytecodeSelection(sourceEditor: Editor, bytecodeEditor: Editor) {
    if (sourceEditor.getCaretModel().getCaretCount() != 1) return
    val virtualFile = sourceEditor.virtualFile ?: return
    if (virtualFile.fileType !== JavaFileType.INSTANCE) return

    val selectedPsiElement = getPsiElement(project, sourceEditor)
    if (selectedPsiElement == null) return
    val containingClass = ByteCodeViewerManager.getContainingClass(selectedPsiElement)
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

    val bytecodeDocument = bytecodeEditor.getDocument()

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

  private fun generateBytecodeText(): Bytecode? = runReadAction {
    val selectedEditor = FileEditorManager.getInstance(project).getSelectedTextEditor() ?: return@runReadAction null
    val psiElement = getPsiElement(project, selectedEditor) ?: return@runReadAction null
    getByteCodeVariants(psiElement)
  }

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(bytecodeEditor)
  }

  companion object {
    const val TOOL_WINDOW_ID: String = "Bytecode"

    private val LOG = Logger.getInstance(BytecodeToolWindowPanel::class.java)

    private val BYTECODE_WITH_DEBUG_INFO = Key.create<String>("BYTECODE_WITH_DEBUG_INFO")
  }
}
