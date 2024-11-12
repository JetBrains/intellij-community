// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor.settings

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.options.newEditor.settings.SettingsVirtualFileHolder.SettingsVirtualFile
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class SettingsFileEditor(private val settingsFile: SettingsVirtualFile) : FileEditor {

  private val userDataHolder: UserDataHolder = UserDataHolderBase()

  init {
    userDataHolder.putUserData(FileEditorManagerKeys.DUMB_AWARE, true)
  }

  override fun getFile(): VirtualFile {
    return settingsFile
  }

  override fun getComponent(): JComponent {
    return settingsFile.editor.rootPane!!
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return null
  }

  override fun getName(): @TabTitle String = com.intellij.CommonBundle.settingsTitle()

  override fun setState(state: FileEditorState) {}

  override fun isModified(): Boolean {
    return false
  }

  override fun isValid(): Boolean {
    return settingsFile.editor.rootPane != null
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
  }

  override fun dispose() {
  }

  override fun <T> getUserData(key: Key<T?>): T? {
    return userDataHolder.getUserData(key)
  }

  override fun <T> putUserData(key: Key<T?>, value: T?) {
    // do nothing
  }
}
