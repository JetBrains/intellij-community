// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor.settings

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.options.newEditor.settings.SettingsVirtualFileHolder.SettingsVirtualFile
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile

class SettingsFileEditorProvider : FileEditorProvider, DumbAware {
  companion object{
    const val ID = "SettingsFileEditor"
  }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file is SettingsVirtualFile
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val settingsFile = file as SettingsVirtualFile

    return SettingsFileEditor(settingsFile)
  }

  override fun getEditorTypeId(): String {
    return ID
  }

  override fun getPolicy(): FileEditorPolicy {
    return FileEditorPolicy.HIDE_OTHER_EDITORS
  }

  override fun isDumbAware(): Boolean {
    return true
  }

  override fun disposeEditor(editor: FileEditor) {
    Disposer.dispose(editor as SettingsFileEditor)
  }
}
