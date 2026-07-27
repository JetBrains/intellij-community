// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Binds an editor created by a frontend-owned provider to a mocked backend editor counterpart in split mode.
 *
 * The implementation is registered only by the split frontend. In monolith or non-split builds this service is absent,
 * and callers should keep their regular frontend-owned editor behavior.
 */
@ApiStatus.Internal
interface FrontendOwnedFileEditorSplitBinder {
  suspend fun tryBindExistingFrontendEditorToMockedBackendEditor(
    project: Project,
    file: VirtualFile,
    provider: FileEditorProvider,
    editor: FileEditor,
    editorCoroutineScope: CoroutineScope,
  ): Boolean

  companion object {
    suspend fun tryBindExistingFrontendEditorToMockedBackendEditor(
      project: Project,
      file: VirtualFile,
      provider: FileEditorProvider,
      editor: FileEditor,
      editorCoroutineScope: CoroutineScope,
    ): Boolean {
      return ApplicationManager.getApplication()
               .getService(FrontendOwnedFileEditorSplitBinder::class.java)
               ?.tryBindExistingFrontendEditorToMockedBackendEditor(project, file, provider, editor, editorCoroutineScope)
             ?: false
    }
  }
}
