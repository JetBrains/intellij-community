// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * To open any JComponent in editor tab you can call
 * JComponentEditorProvider.openEditor(project, "Title", jComponent)
 */
class JComponentFileEditor(private val file: VirtualFile, private val component: JComponent) : UserDataHolderBase(), FileEditor {
  override fun getComponent(): JComponent = this.component
  override fun getPreferredFocusedComponent(): JComponent = this.component
  override fun getName(): String = file.name
  override fun setState(state: FileEditorState) {}
  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun getCurrentLocation(): FileEditorLocation? = null
  override fun dispose() {}
  override fun getFile(): VirtualFile = file
}