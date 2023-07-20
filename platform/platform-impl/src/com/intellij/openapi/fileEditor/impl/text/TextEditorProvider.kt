// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.ide.IdeBundle
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ClientFileEditorManager.Companion.assignClientId
import com.intellij.openapi.fileEditor.ex.StructureViewFileEditorProvider
import com.intellij.openapi.fileEditor.impl.DefaultPlatformFileEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl.Companion.createTextEditor
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.SingleRootFileViewProvider
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.update.UiNotifyConnector
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.beans.PropertyChangeListener
import javax.swing.JComponent

private val TEXT_EDITOR_KEY = Key.create<TextEditor>("textEditor")
private const val TYPE_ID: @NonNls String = "text-editor"
private const val LINE_ATTR: @NonNls String = "line"
private const val COLUMN_ATTR: @NonNls String = "column"
private const val LEAN_FORWARD_ATTR: @NonNls String = "lean-forward"
private const val SELECTION_START_LINE_ATTR: @NonNls String = "selection-start-line"
private const val SELECTION_START_COLUMN_ATTR: @NonNls String = "selection-start-column"
private const val SELECTION_END_LINE_ATTR: @NonNls String = "selection-end-line"
private const val SELECTION_END_COLUMN_ATTR: @NonNls String = "selection-end-column"
private const val RELATIVE_CARET_POSITION_ATTR: @NonNls String = "relative-caret-position"
private const val CARET_ELEMENT: @NonNls String = "caret"

open class TextEditorProvider : DefaultPlatformFileEditorProvider, TextBasedFileEditorProvider, StructureViewFileEditorProvider,
                                QuickDefinitionProvider, DumbAware {
  companion object {
    @JvmStatic
    fun getInstance(): TextEditorProvider {
      return FileEditorProvider.EP_FILE_EDITOR_PROVIDER.findFirstAssignableExtension(TextEditorProvider::class.java)!!
    }

    @JvmStatic
    fun getDocuments(editor: FileEditor): Array<Document> {
      return when (editor) {
        is DocumentsEditor -> editor.documents
        is TextEditor -> arrayOf(editor.editor.document)
        else -> {
          var result = Document.EMPTY_ARRAY
          val document = editor.getFile()?.let { FileDocumentManager.getInstance().getDocument(it) }
          if (document != null) {
            result = arrayOf(document)
          }
          result
        }
      }
    }

    @ApiStatus.Internal
    fun putTextEditor(editor: Editor, textEditor: TextEditor) {
      editor.putUserData(TEXT_EDITOR_KEY, textEditor)
    }

    @JvmStatic
    fun isTextFile(file: VirtualFile): Boolean {
      if (file.isDirectory || !file.isValid) {
        return false
      }
      val fileType = file.fileType
      return !fileType.isBinary || BinaryFileTypeDecompilers.getInstance().forFileType(fileType) != null
    }
  }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return isTextFile(file) && !SingleRootFileViewProvider.isTooLargeForContentLoading(file)
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return TextEditorImpl(project, file, this, createTextEditor(project, file))
  }

  override fun readState(element: Element, project: Project, file: VirtualFile): FileEditorState {
    val state = TextEditorState()
    if (element.isEmpty) {
      return state
    }

    val caretElements = element.getChildren(CARET_ELEMENT)
    if (caretElements.isEmpty()) {
      state.CARETS = arrayOf(readCaretInfo(element))
    }
    else {
      state.CARETS = Array(caretElements.size) { i ->
        readCaretInfo(caretElements[i])
      }
    }
    state.relativeCaretPosition = StringUtilRt.parseInt(element.getAttributeValue(RELATIVE_CARET_POSITION_ATTR), 0)
    return state
  }

  override fun writeState(@Suppress("LocalVariableName") _state: FileEditorState, project: Project, element: Element) {
    val state = _state as TextEditorState
    if (state.relativeCaretPosition != 0) {
      element.setAttribute(RELATIVE_CARET_POSITION_ATTR, state.relativeCaretPosition.toString())
    }

    for (caretState in state.CARETS) {
      val e = Element(CARET_ELEMENT)
      writeIfNot0(e, LINE_ATTR, caretState.LINE)
      writeIfNot0(e, COLUMN_ATTR, caretState.COLUMN)
      if (caretState.LEAN_FORWARD) {
        e.setAttribute(LEAN_FORWARD_ATTR, true.toString())
      }
      writeIfNot0(e, SELECTION_START_LINE_ATTR, caretState.SELECTION_START_LINE)
      writeIfNot0(e, SELECTION_START_COLUMN_ATTR, caretState.SELECTION_START_COLUMN)
      writeIfNot0(e, SELECTION_END_LINE_ATTR, caretState.SELECTION_END_LINE)
      writeIfNot0(e, SELECTION_END_LINE_ATTR, caretState.SELECTION_END_LINE)
      writeIfNot0(e, SELECTION_END_COLUMN_ATTR, caretState.SELECTION_END_COLUMN)
      if (!e.isEmpty) {
        element.addContent(e)
      }
    }
  }

  override fun getEditorTypeId(): String = TYPE_ID

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.NONE

  open fun getTextEditor(editor: Editor): TextEditor {
    var textEditor = editor.getUserData(TEXT_EDITOR_KEY)
    if (textEditor == null) {
      textEditor = createWrapperForEditor(editor)
      putTextEditor(editor, textEditor)
    }
    return textEditor
  }

  protected open fun createWrapperForEditor(editor: Editor): EditorWrapper = EditorWrapper(editor)

  @RequiresReadLock
  open fun getStateImpl(project: Project?, editor: Editor, level: FileEditorStateLevel): TextEditorState {
    val state = TextEditorState()
    val caretModel = editor.caretModel
    val caretsAndSelections = caretModel.caretsAndSelections
    state.CARETS = Array(caretsAndSelections.size) { i ->
      val caretState = caretsAndSelections[i]
      val caretPosition = caretState.caretPosition
      val selectionStartPosition = caretState.selectionStart
      val selectionEndPosition = caretState.selectionEnd
      val s = TextEditorState.CaretState()
      s.LINE = getLine(caretPosition)
      s.COLUMN = getColumn(caretPosition)
      s.LEAN_FORWARD = caretPosition != null && caretPosition.leansForward
      s.VISUAL_COLUMN_ADJUSTMENT = caretState.visualColumnAdjustment
      s.SELECTION_START_LINE = getLine(selectionStartPosition)
      s.SELECTION_START_COLUMN = getColumn(selectionStartPosition)
      s.SELECTION_END_LINE = getLine(selectionEndPosition)
      s.SELECTION_END_COLUMN = getColumn(selectionEndPosition)
      s
    }

    // Saving a scrolling proportion on UNDO may cause undesirable results of undo action fails to perform since
    // scrolling proportion restored slightly differs from what have been saved.
    state.relativeCaretPosition = if (level == FileEditorStateLevel.UNDO) Int.MAX_VALUE else EditorUtil.calcRelativeCaretPosition(editor)
    return state
  }

  @RequiresEdt
  open fun setStateImpl(project: Project?, editor: Editor, state: TextEditorState, exactState: Boolean) {
    val carets = state.CARETS
    if (carets.isNotEmpty()) {
      val states = ArrayList<CaretState>(carets.size)
      for (caretState in carets) {
        states.add(CaretState(LogicalPosition(caretState.LINE, caretState.COLUMN, caretState.LEAN_FORWARD),
                              caretState.VISUAL_COLUMN_ADJUSTMENT,
                              LogicalPosition(caretState.SELECTION_START_LINE, caretState.SELECTION_START_COLUMN),
                              LogicalPosition(caretState.SELECTION_END_LINE, caretState.SELECTION_END_COLUMN)))
      }
      editor.caretModel.setCaretsAndSelections(states, false)
    }
    val relativeCaretPosition = state.relativeCaretPosition
    if (AsyncEditorLoader.isEditorLoaded(editor) || ApplicationManager.getApplication().isUnitTestMode) {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        scrollToCaret(editor = editor, exactState = exactState, relativeCaretPosition = relativeCaretPosition)
      }
      else {
        UiNotifyConnector.doWhenFirstShown(editor.contentComponent) {
          if (!editor.isDisposed) {
            scrollToCaret(editor = editor, exactState = exactState, relativeCaretPosition = relativeCaretPosition)
          }
        }
      }
    }
  }

  override fun getStructureViewBuilder(project: Project, file: VirtualFile): StructureViewBuilder? {
    return StructureViewBuilder.PROVIDER.getStructureViewBuilder(file.fileType, file, project)
  }

  protected open inner class EditorWrapper(private val editor: Editor) : UserDataHolderBase(), TextEditor {
    init {
      @Suppress("LeakingThis")
      assignClientId(fileEditor = this, clientId = ClientEditorManager.getClientId(editor))
    }

    override fun getEditor(): Editor = editor

    override fun getComponent(): JComponent = editor.component

    override fun getPreferredFocusedComponent(): JComponent? = editor.contentComponent

    override fun getName(): String = IdeBundle.message("tab.title.text")

    override fun getStructureViewBuilder(): StructureViewBuilder? {
      val file = file ?: return null
      val project = editor.project ?: return null
      return this@TextEditorProvider.getStructureViewBuilder(project, file)
    }

    override fun getState(level: FileEditorStateLevel): FileEditorState {
      return getStateImpl(project = null, editor = editor, level = level)
    }

    override fun setState(state: FileEditorState) {
      setState(state = state, exactState = false)
    }

    override fun setState(state: FileEditorState, exactState: Boolean) {
      setStateImpl(project = null, editor = editor, state = state as TextEditorState, exactState = exactState)
    }

    override fun isModified(): Boolean = false

    final override fun isValid(): Boolean = !editor.isDisposed

    override fun dispose() {}

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun canNavigateTo(navigatable: Navigatable): Boolean = false

    override fun navigateTo(navigatable: Navigatable) {}

    override fun getFile(): VirtualFile? {
      return FileDocumentManager.getInstance().getFile(editor.document)
    }
  }
}

private fun getLine(position: LogicalPosition?): Int = position?.line ?: 0

private fun getColumn(position: LogicalPosition?): Int = position?.column ?: 0

internal fun scrollToCaret(editor: Editor, exactState: Boolean, relativeCaretPosition: Int) {
  editor.scrollingModel.disableAnimation()
  if (relativeCaretPosition != Int.MAX_VALUE) {
    EditorUtil.setRelativeCaretPosition(editor, relativeCaretPosition)
  }
  if (!exactState) {
    editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
  }
  editor.scrollingModel.enableAnimation()
}

private fun readCaretInfo(element: Element): TextEditorState.CaretState {
  val caretState = TextEditorState.CaretState()
  caretState.LINE = parseWithDefault(element, LINE_ATTR)
  caretState.COLUMN = parseWithDefault(element, COLUMN_ATTR)
  caretState.LEAN_FORWARD = element.getAttributeValue(LEAN_FORWARD_ATTR).toBoolean()
  caretState.SELECTION_START_LINE = parseWithDefault(element, SELECTION_START_LINE_ATTR)
  caretState.SELECTION_START_COLUMN = parseWithDefault(element, SELECTION_START_COLUMN_ATTR)
  caretState.SELECTION_END_LINE = parseWithDefault(element, SELECTION_END_LINE_ATTR)
  caretState.SELECTION_END_COLUMN = parseWithDefault(element, SELECTION_END_COLUMN_ATTR)
  return caretState
}

private fun parseWithDefault(element: Element, attributeName: String): Int {
  return StringUtilRt.parseInt(element.getAttributeValue(attributeName), 0)
}

private fun writeIfNot0(element: Element, name: String, value: Int) {
  if (value != 0) {
    element.setAttribute(name, value.toString())
  }
}