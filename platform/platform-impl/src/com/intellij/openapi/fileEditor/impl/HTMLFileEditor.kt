// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JEditorPane

class HTMLFileEditor(private val vFile: VirtualFile) : FileEditor {
  private val component: JComponent =
    ScrollPaneFactory.createScrollPane(
      JEditorPane().also {
        it.editorKit = UIUtil.getHTMLEditorKit()
        it.text = VfsUtil.loadText(vFile)
        it.border = JBUI.Borders.empty(8, 12)
        it.isEditable = false
      })

  override fun getComponent(): JComponent = component
  override fun getPreferredFocusedComponent() = component
  override fun getName() = "HTML Preview"
  override fun setState(state: FileEditorState) {}
  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun <T : Any?> getUserData(key: Key<T>): T? = null
  override fun <T : Any?> putUserData(key: Key<T>, value: T?) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun getCurrentLocation(): FileEditorLocation? = null
  override fun dispose() {}
}