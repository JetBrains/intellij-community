// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor.settings

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.Nls
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class SettingsFileEditor(private val myComponent: JComponent) : FileEditor {
  private val myVirtualFile: LightVirtualFile = LightVirtualFile(SettingsFileEditorProvider.ID, )

  override fun getFile(): VirtualFile {
    return myVirtualFile
  }

  override fun getComponent(): JComponent {
    return myComponent
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return null
  }

  override fun getName(): @TabTitle String = com.intellij.CommonBundle.settingsTitle()

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

  override fun dispose() {
  }

  override fun <T> getUserData(key: Key<T?>): T? {
    return null
  }

  override fun <T> putUserData(key: Key<T?>, value: T?) {
  }
}
