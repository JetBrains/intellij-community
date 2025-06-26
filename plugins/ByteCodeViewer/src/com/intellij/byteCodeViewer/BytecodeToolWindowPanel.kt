// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.util.Textifier
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor
import java.awt.BorderLayout
import java.awt.GridLayout
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.min

/**
 * Displays JVM bytecode in a tool window.
 *
 * It has 2 states:
 * - there is bytecode: display a non-editable editor with source<->bytecode line matching
 * - there is no bytecode: display a label instructing the user to build the project first.
 */
internal class BytecodeToolWindowPanel(
  private val project: Project,
  private val psiClass: PsiClass,
  private val classFile: VirtualFile?,
) : JBPanel<Nothing>(BorderLayout()), Disposable {
  private val bytecodeEditor: Editor = EditorFactory.getInstance()
    .createEditor(EditorFactory.getInstance().createDocument(""), project, JavaClassFileType.INSTANCE, true)

  init {
    if (classFile == null) {
      val labelsPanel = JPanel(GridLayout(0, 1))
      labelsPanel.setBorder(JBUI.Borders.empty())

      val className = psiClass.name
      val message = if (className != null) BytecodeViewerBundle.message("bytecode.not.found.for.class", className)
      else BytecodeViewerBundle.message("bytecode.not.found")

      JBLabel(wrapWithHtml(message), SwingConstants.CENTER).apply {
        foreground = UIUtil.getLabelDisabledForeground()
        setBorder(JBUI.Borders.empty(2, 0))
        labelsPanel.add(this)
      }

      JBLabel(wrapWithHtml(BytecodeViewerBundle.message("please.build.project")), SwingConstants.CENTER).apply {
        foreground = UIUtil.getLabelDisabledForeground()
        setBorder(JBUI.Borders.empty(2, 0))
        labelsPanel.add(this)
      }

      val centerPanel = JPanel(VerticalFlowLayout(VerticalFlowLayout.MIDDLE))
      centerPanel.add(labelsPanel)

      add(centerPanel, BorderLayout.CENTER)
    }
    else {
      add(bytecodeEditor.getComponent())
      updateTextInEditor()
      EditorFactory.getInstance().getEventMulticaster().addCaretListener(object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
          val sourceEditor = selectedMatchingEditor
          if (event.editor != sourceEditor) return
          updateBytecodeSelection(sourceEditor, bytecodeEditor)
        }

        override fun caretAdded(event: CaretEvent) {
          val sourceEditor = selectedMatchingEditor
          if (event.editor != sourceEditor) return
          updateBytecodeSelection(sourceEditor, bytecodeEditor)
        }

        override fun caretRemoved(event: CaretEvent) {
          val sourceEditor = selectedMatchingEditor
          if (event.editor != sourceEditor) return
          updateBytecodeSelection(sourceEditor, bytecodeEditor)
        }
      }, this@BytecodeToolWindowPanel)

      EditorFactory.getInstance().getEventMulticaster().addSelectionListener(object : SelectionListener {
        override fun selectionChanged(e: SelectionEvent) {
          val sourceEditor = selectedMatchingEditor
          if (e.editor != sourceEditor) return
          updateBytecodeSelection(sourceEditor, bytecodeEditor)
        }
      }, this@BytecodeToolWindowPanel)
    }
  }

  fun updateTextInEditor() {
    if (classFile == null) throw IllegalStateException("Class file must not be null")
    val byteCodeText = deserializeBytecode(classFile)
    bytecodeEditor.document.putUserData(BYTECODE_WITH_DEBUG_INFO, byteCodeText) // include debug info for selection matching
    val byteCodeToShow = if (BytecodeViewerSettings.getInstance().state.showDebugInfo) byteCodeText else removeDebugInfo(byteCodeText)
    ApplicationManager.getApplication().runWriteAction {
      bytecodeEditor.document.setText(byteCodeToShow)
    }

    val sourceEditor = selectedMatchingEditor ?: return
    updateBytecodeSelection(sourceEditor, bytecodeEditor)
  }

  /**
   * If the editor in which `psiClass` is edited is selected, returns it. Otherwise returns null.
   */
  private val selectedMatchingEditor: Editor?
    get() = FileEditorManager.getInstance(project).getSelectedTextEditor()?.takeIf { editor ->
      val document = editor.getDocument()
      val virtualFile = FileDocumentManager.getInstance().getFile(document)
      virtualFile == psiClass.containingFile.virtualFile
    }

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(bytecodeEditor)
  }

  companion object {
    const val TOOL_WINDOW_ID: String = "Bytecode"

    private val LOG = Logger.getInstance(BytecodeToolWindowPanel::class.java)

    private val BYTECODE_WITH_DEBUG_INFO = Key.create<String>("BYTECODE_WITH_DEBUG_INFO")

    /**
     * Wrapping with HTML tags ensures the text wraps and is not cut off when the tool window becomes too narrow.
     */
    @NlsContexts.Label
    private fun wrapWithHtml(@NlsContexts.Label text: String): String {
      return "<html>$text</html>"
    }

    private fun updateBytecodeSelection(sourceEditor: Editor, bytecodeEditor: Editor) {
      if (sourceEditor.getCaretModel().getCaretCount() != 1) {
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

      val bytecodeWithDebugInfo = bytecodeDocument.getUserData(BYTECODE_WITH_DEBUG_INFO)
      if (bytecodeWithDebugInfo == null) {
        LOG.warn("Bytecode with debug information is null. Ensure the bytecode has been generated correctly.")
        return
      }

      val linesRange = mapLines(bytecodeWithDebugInfo, sourceStartLine, sourceEndLine, showDebugInfo = BytecodeViewerSettings.getInstance().state.showDebugInfo)

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

    private fun deserializeBytecode(classFile: VirtualFile): String {
      try {
        val bytes = classFile.contentsToByteArray(false)
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { printWriter ->
          ClassReader(bytes).accept(TraceClassVisitor(null, Textifier(), printWriter), ClassReader.SKIP_FRAMES)
        }
        return stringWriter.toString()
      }
      catch (e: IOException) {
        LOG.warn(e)
        return BytecodeViewerBundle.message("deserialization.error")
      }
    }
  }
}
