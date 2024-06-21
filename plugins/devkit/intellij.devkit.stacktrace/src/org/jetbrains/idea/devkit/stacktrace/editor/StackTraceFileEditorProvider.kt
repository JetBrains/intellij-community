// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.stacktrace.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore.loadText
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.unscramble.UnscrambleUtils

/**
 * Java stacktrace weighed file editor provider.
 */
class StackTraceFileEditorProvider : WeighedFileEditorProvider() {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file.extension == "txt" && UnscrambleUtils.isStackTrace(loadText(file))
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor = StackTraceFileEditor(project, file)

  override fun getEditorTypeId(): String = "stacktrace-preview-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}
