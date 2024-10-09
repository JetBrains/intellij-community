// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolderBase
import java.beans.PropertyChangeListener
import javax.swing.JComponent


class ToolWindowTabFileEditor(private val project: Project, private val file: ToolWindowTabFileImpl) : UserDataHolderBase(), FileEditor {

  private val widgetParentDisposable: Disposable = Disposer.newDisposable("${file.name} tool window tab widget parent")

  override fun getComponent(): JComponent = file.content.component

  override fun getPreferredFocusedComponent(): JComponent? = file.content.component.let {
    it.focusTraversalPolicy?.getDefaultComponent(it) as? JComponent ?: it
  }

  override fun getName(): @NlsSafe String = file.name

  override fun setState(state: FileEditorState) {}

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun getFile(): ToolWindowTabFileImpl = file

  override fun dispose() {
    FileEditorManager.getInstance(project).closeFile(this.file)
    Disposer.dispose(widgetParentDisposable)
  }
}