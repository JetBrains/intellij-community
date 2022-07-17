// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.impl.FocusManagerImpl
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.DocumentUtil
import training.dsl.impl.LessonExecutor
import training.learn.ActionsRecorder
import java.awt.Component

@LearningDsl
open class TaskRuntimeContext internal constructor(private val lessonExecutor: LessonExecutor,
                                                   internal val actionsRecorder: ActionsRecorder,
                                                   val restorePreviousTaskCallback: () -> Unit,
                                                   private val previousGetter: () -> PreviousTaskInfo
) : LearningDslBase {
  internal constructor(base: TaskRuntimeContext)
    : this(base.lessonExecutor, base.actionsRecorder, base.restorePreviousTaskCallback, base.previousGetter)

  val taskDisposable: Disposable = actionsRecorder
  val disposed: Boolean get() = actionsRecorder.disposed

  val editor: Editor get() = lessonExecutor.editor
  val project: Project get() = lessonExecutor.project
  val lessonDisposable: Disposable get() = lessonExecutor

  val focusOwner: Component?
    get() = IdeFocusManager.getInstance(project).focusOwner

  val previous: PreviousTaskInfo
    get() = previousGetter()

  val virtualFile: VirtualFile
    get() = FileDocumentManager.getInstance().getFile(editor.document) ?: error("No virtual file for ${editor.document}")

  fun taskInvokeLater(modalityState: ModalityState? = null, runnable: () -> Unit) {
    lessonExecutor.taskInvokeLater(modalityState, runnable)
  }

  fun invokeInBackground(runnable: () -> Unit) {
    lessonExecutor.invokeInBackground(runnable)
  }

  /// Utility methods ///

  fun setSample(sample: LessonSample) {
    taskInvokeLater(ModalityState.NON_MODAL) {
      TemplateManagerImpl.getTemplateState(editor)?.gotoEnd()
      (editor as? EditorEx)?.isViewer = false
      editor.caretModel.removeSecondaryCarets()
      setDocumentCode(sample.text)
      setCaret(sample.getPosition(0))
    }
  }

  fun select(startLine: Int, startColumn: Int, endLine: Int, endColumn: Int) {
    val blockStart = LogicalPosition(startLine - 1, startColumn - 1)
    val blockEnd = LogicalPosition(endLine - 1, endColumn - 1)

    val startPosition = editor.logicalPositionToOffset(blockStart)
    val endPosition = editor.logicalPositionToOffset(blockEnd)
    editor.caretModel.moveToOffset(startPosition)
    editor.selectionModel.setSelection(startPosition, endPosition)
    requestEditorFocus()
  }

  fun caret(text: String, select: Boolean = false) {
    val start = getStartOffsetForText(text) ?: return
    editor.caretModel.moveToOffset(start)
    if (select) {
      editor.selectionModel.setSelection(start, start + text.length)
    }
    requestEditorFocus()
  }

  /** NOTE:  [line] and [column] starts from 1 not from zero. So these parameters should be same as in editors. */
  fun caret(line: Int, column: Int) {
    OpenFileDescriptor(project, virtualFile, line - 1, column - 1).navigateIn(editor)
    requestEditorFocus()
  }

  fun caret(offset: Int) {
    OpenFileDescriptor(project, virtualFile, offset).navigateIn(editor)
    requestEditorFocus()
  }

  fun caret(position: LessonSamplePosition) = setCaret(position)

  fun requestEditorFocus() {
    FocusManagerImpl.getInstance(project).requestFocus(editor.contentComponent, false)
  }

  private fun setDocumentCode(code: String) {
    val document = editor.document
    DocumentUtil.writeInRunUndoTransparentAction {
      val documentReference = DocumentReferenceManager.getInstance().create(document)
      UndoManager.getInstance(project).nonundoableActionPerformed(documentReference, false)
      document.replaceString(0, document.textLength, code)
    }
    PsiDocumentManager.getInstance(project).commitDocument(document)
    doUndoableAction(project)
    updateGutter(editor)
  }

  private fun doUndoableAction(project: Project) {
    CommandProcessor.getInstance().executeCommand(project, {
      UndoManager.getInstance(project).undoableActionPerformed(object : BasicUndoableAction() {
        override fun undo() {}
        override fun redo() {}
      })
    }, null, null)
  }

  private fun updateGutter(editor: Editor) {
    val editorGutterComponentEx = editor.gutter as EditorGutterComponentEx
    editorGutterComponentEx.revalidateMarkup()
  }

  private fun setCaret(position: LessonSamplePosition) {
    position.selection?.let { editor.selectionModel.setSelection(it.first, it.second) }
    editor.caretModel.moveToOffset(position.startOffset)
    requestEditorFocus()
  }

  private fun getStartOffsetForText(text: String): Int? {
    val document = editor.document

    val indexOf = document.charsSequence.indexOf(text)
    if (indexOf != -1) {
      return indexOf
    }
    return null
  }
}