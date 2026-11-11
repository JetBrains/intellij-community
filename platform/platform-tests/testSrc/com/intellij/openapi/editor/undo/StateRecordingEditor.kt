// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.undo

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.DocumentsEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

internal data class CaretState(val caret: Int) : FileEditorState {
  override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean = false
}

/**
 * An editor whose state is a caret offset. It records each state that undo applies to it.
 */
internal class StateRecordingEditor(
  private val file: VirtualFile,
  document: Document,
  private var caret: Int,
) : UserDataHolderBase(), DocumentsEditor {
  private val documents = arrayOf(document)
  private val component = JPanel()
  private val appliedStates = mutableListOf<FileEditorState>()

  fun appliedStates(): List<FileEditorState> {
    return appliedStates.toList()
  }

  fun moveCaret(offset: Int) {
    caret = offset
  }

  override fun getFile(): VirtualFile = file

  override fun getDocuments(): Array<Document> = documents

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun getName(): String = "StateRecordingEditor"

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    return CaretState(caret)
  }

  override fun setState(state: FileEditorState) {
    appliedStates.add(state)
    caret = (state as CaretState).caret
  }

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
  }

  override fun dispose() {
  }
}
