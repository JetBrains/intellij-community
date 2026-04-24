// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
class HTMLFileEditorMock(
  private val file: VirtualFile,
) : UserDataHolderBase(), HTMLEditorProvider.HTMLFileEditor {

  companion object {
    @ApiStatus.Internal
    @NlsSafe
    const val UNIT_TEST_EDITOR_NAME: String = "unit-test-html-editor"
  }

  override fun getComponent(): JComponent = JPanel()
  override fun getPreferredFocusedComponent(): JComponent? = null
  override fun getName(): String = UNIT_TEST_EDITOR_NAME
  override fun setState(state: FileEditorState) {}
  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun dispose() {}
  override fun getFile(): VirtualFile = file
}
