// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.editorTab

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NonNls

internal class LearnIdeFileEditorProvider : FileEditorProvider, DumbAware {
  companion object {
    const val ID: String = "LearnIdeFileEditor"
  }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file is LearnIdeVirtualFile
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val file = file as LearnIdeVirtualFile
    return LearnIdeVirtualFileEditor(file)
  }

  override fun getEditorTypeId(): @NonNls String = ID

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS

  override fun isDumbAware(): Boolean = true
}
