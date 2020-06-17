// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.KeyWithDefaultValue
import com.intellij.openapi.vfs.VirtualFile

class HTMLEditorProvider : FileEditorProvider, DumbAware {
  override fun createEditor(project: Project, file: VirtualFile): FileEditor = HTMLFileEditor()

  override fun accept(project: Project, file: VirtualFile) = file.getUserData(HTML_CONTENT_TYPE)!!

  override fun getEditorTypeId() = "html-editor"

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  companion object {
    val HTML_CONTENT_TYPE: Key<Boolean> = KeyWithDefaultValue.create("HTML_CONTENT_TYPE", false)
  }
}