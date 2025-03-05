// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.options.newEditor.settings.SettingsVirtualFileHolder.SettingsVirtualFile
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.beans.PropertyChangeListener
import javax.swing.JComponent

@ApiStatus.Internal
internal class SettingsFileEditor(
  private val settingsFile: SettingsVirtualFile,
  component: JComponent,
  private val preferredFocusedComponent: JComponent?,
  dialogDisposable: Disposable
) : FileEditor {

  private val userDataHolder: UserDataHolder = UserDataHolderBase()
  private val myPanel = component

  init {
    userDataHolder.putUserData(FileEditorManagerKeys.DUMB_AWARE, true)
    userDataHolder.putUserData(FileEditorManagerKeys.FORBID_PREVIEW_TAB, true)
    userDataHolder.putUserData(FileEditorManagerKeys.SINGLETON_EDITOR_IN_WINDOW, true)
    Disposer.register(dialogDisposable, this)
  }

  override fun getFile(): VirtualFile {
    return settingsFile
  }

  override fun getComponent(): JComponent {
    return myPanel
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return preferredFocusedComponent
  }

  override fun getName(): @TabTitle String = com.intellij.CommonBundle.settingsTitle()

  override fun setState(state: FileEditorState) {
    if (state !is NavigationState)
      return
    settingsFile.setConfigurableId(state.pathId)
  }


  override fun getState(level: FileEditorStateLevel): FileEditorState {
    if (level != FileEditorStateLevel.NAVIGATION)
      return super.getState(level)
    val configurableId = settingsFile.getConfigurableId() ?: return super.getState(level)
    return NavigationState(configurableId)
  }

  override fun isModified(): Boolean {
    return settingsFile.isModified()
  }

  override fun isValid(): Boolean {
    return true
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
  }

  override fun dispose() {
    settingsFile.disposeDialog()
  }

  override fun <T> getUserData(key: Key<T?>): T? {
    return userDataHolder.getUserData(key)
  }

  override fun <T> putUserData(key: Key<T?>, value: T?) {
    // do nothing
  }


  private class NavigationState(val pathId: String) : FileEditorState {

    override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean {
      if (otherState !is NavigationState || level != FileEditorStateLevel.NAVIGATION) {
        return false
      }
      return pathId == otherState.pathId
    }

    override fun toString(): String {
      return "NavigationState(pathId='$pathId')"
    }


  }
}
