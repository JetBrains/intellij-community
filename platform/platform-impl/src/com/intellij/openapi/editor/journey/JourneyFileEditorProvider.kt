// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.journey

import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class JourneyFileEditorProvider : FileEditorProvider {

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file.extension == "journey"
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return JourneyFileEditor(project, file)
  }

  override fun getEditorTypeId(): String {
    return "JourneyEditor"
  }

  override fun getPolicy(): FileEditorPolicy {
    return FileEditorPolicy.HIDE_OTHER_EDITORS
  }
}
