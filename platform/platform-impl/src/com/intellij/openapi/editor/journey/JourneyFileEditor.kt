// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.journey

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

internal class JourneyFileEditor(
  private val project: Project,
  private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {

  private val panel: JPanel = JPanel()

  init {
    val canvasComponent = JTextArea("Enjoy your journey!") // TODO: implement canvas component here
    panel.add(canvasComponent)
  }

  override fun getFile(): VirtualFile {
    return file
  }

  override fun getComponent(): JComponent {
    return panel
  }

  override fun getPreferredFocusedComponent(): JComponent {
    return panel
  }

  override fun dispose() {
  }

  override fun getName(): String {
    return "JourneyEditor"
  }

  override fun setState(state: FileEditorState) {
  }

  override fun isModified(): Boolean {
    return false
  }

  override fun isValid(): Boolean {
    return true
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
  }
}
