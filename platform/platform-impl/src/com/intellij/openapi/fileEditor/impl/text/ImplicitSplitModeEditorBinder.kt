// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.openapi.editor.Document
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Tries binding the editor supplied by a given `provider` to its backend counterpart, if the provider itself is called in split mode frontend.
 * This ensures proper document content synchronization via patch engine.
 *
 * The implementation handles binding and ensures that recursive editor binding is impossible.
 *
 * In split mode, both frontend-originated and backend-originated editor opening requests first wait for the backend `FileEditorCompositeModel`.
 * The frontend then creates UI from the backend `FileEditorModel`. For backend text models this normally means creating a backend-bound
 * frontend text editor. A frontend plugin may instead register a normal async `fileEditorProvider` that replaces the backend text editor
 * with `HIDE_DEFAULT_EDITOR` or `HIDE_OTHER_EDITORS` and creates arbitrary frontend UI.
 *
 * If such a replacement provider creates a nested text editor through [TextEditorProvider.createEditor], async
 * [TextEditorProvider.createFileEditor], or the corresponding `PsiAwareTextEditorProvider` path, this hook lets split frontend bind that nested
 * editor to the backend text model. Plugin code does not need to call split-specific APIs.
 *
 * Unsupported split frontend cases currently fall back to the backend-bound text editor: sync-only replacement providers, frontend providers
 * with non-replacing policies, and custom nested editor creation that bypasses [TextEditorProvider].
 *
 * The extension point is optional and declared only by `platform.frontend.split`. In monolith or non-split builds it is absent, and tolerant
 * extension-point lookup keeps regular editor creation unchanged.
 *
 * Example: the Markdown editor with preview combines a JCEF-based editor and a plain text editor.
 * In that case a regular text editor must be bound to the backend editor instance already created for the same file
 * to ensure correct IDE features work through the patch engine
 */
@ApiStatus.Internal
interface ImplicitSplitModeEditorBinder {
  fun tryBindSuppliedEditorToBackend(provider: TextEditorProvider, project: Project, file: VirtualFile): TextEditor? = null

  suspend fun tryBindSuppliedEditorToBackendAsync(
    provider: TextEditorProvider,
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): TextEditor? = null

  companion object {
    val EP_NAME: ExtensionPointName<ImplicitSplitModeEditorBinder> = ExtensionPointName.create("com.intellij.textEditorCreationInterceptor")

    fun tryBindSuppliedEditorToBackend(provider: TextEditorProvider, project: Project, file: VirtualFile): TextEditor? {
      return EP_NAME.extensionsIfPointIsRegistered.firstNotNullOfOrNull { it.tryBindSuppliedEditorToBackend(provider, project, file) }
    }

    suspend fun tryBindSuppliedEditorToBackendAsync(
      provider: TextEditorProvider,
      project: Project,
      file: VirtualFile,
      document: Document?,
      editorCoroutineScope: CoroutineScope,
    ): TextEditor? {
      for (interceptor in EP_NAME.extensionsIfPointIsRegistered) {
        val editor = interceptor.tryBindSuppliedEditorToBackendAsync(provider, project, file, document, editorCoroutineScope)
        if (editor != null) {
          return editor
        }
      }
      return null
    }
  }
}
