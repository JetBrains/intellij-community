// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

internal class LightweightTestFileEditor(
  private val virtualFile: VirtualFile,
  private val editorName: String = "TestFileEditor",
) : UserDataHolderBase(), FileEditor {
  private val component = JPanel()

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent = component

  override fun getName(): String = editorName

  override fun setState(state: FileEditorState) = Unit

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun getFile(): VirtualFile = virtualFile

  override fun dispose() = Unit
}
