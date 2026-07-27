// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Binds an editor created by a frontend-owned provider to a mocked backend editor counterpart in split mode.
 *
 * The implementation is registered only by the split frontend. In monolith or non-split builds this service is absent,
 * and callers should keep their regular frontend-owned editor behavior.
 */
@ApiStatus.Internal
const val FRONTEND_OWNED_BACKEND_MIRROR_PROTOCOL: String = "frontend-owned-backend-mirror"

@ApiStatus.Internal
@Serializable
data class FrontendOwnedBackendMirrorFileDescriptor(
  val namespace: String,
  val id: String,
  val presentableName: String,
)

@ApiStatus.Internal
fun FrontendOwnedBackendMirrorFileDescriptor.toFrontendOwnedBackendMirrorPathOrNull(): String? {
  val namespace = namespace.trim().takeIf { it.isNotEmpty() } ?: return null
  val id = id.trim().takeIf { it.isNotEmpty() } ?: return null
  return "$FRONTEND_OWNED_BACKEND_MIRROR_PATH_VERSION/${encodeFrontendOwnedBackendMirrorPathSegment(namespace)}/${
    encodeFrontendOwnedBackendMirrorPathSegment(id)
  }"
}

@ApiStatus.Internal
fun frontendOwnedBackendMirrorFilePath(protocol: String?, path: String): String? {
  if (protocol != FRONTEND_OWNED_BACKEND_MIRROR_PROTOCOL) return null
  return path.takeIf { it.isNotBlank() }
}

private const val FRONTEND_OWNED_BACKEND_MIRROR_PATH_VERSION = "1"

private fun encodeFrontendOwnedBackendMirrorPathSegment(value: String): String {
  return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}

@ApiStatus.Internal
interface FrontendOwnedFileEditorSplitBinder {
  suspend fun tryBindExistingFrontendEditorToBackendMirror(
    project: Project,
    file: VirtualFile,
    provider: FileEditorProvider,
    editor: FileEditor,
    mirrorFile: FrontendOwnedBackendMirrorFileDescriptor,
    editorCoroutineScope: CoroutineScope,
  ): Boolean

  fun findFrontendFileForBackendMirror(mirrorFilePath: String): VirtualFile? = null

  fun findBackendMirrorForFrontendFile(file: VirtualFile): String? = null

  companion object {
    suspend fun tryBindExistingFrontendEditorToBackendMirror(
      project: Project,
      file: VirtualFile,
      provider: FileEditorProvider,
      editor: FileEditor,
      mirrorFile: FrontendOwnedBackendMirrorFileDescriptor,
      editorCoroutineScope: CoroutineScope,
    ): Boolean {
      return ApplicationManager.getApplication()
               .getService(FrontendOwnedFileEditorSplitBinder::class.java)
               ?.tryBindExistingFrontendEditorToBackendMirror(project, file, provider, editor, mirrorFile, editorCoroutineScope)
             ?: false
    }

    fun findFrontendFileForBackendMirror(mirrorFilePath: String): VirtualFile? {
      return ApplicationManager.getApplication()
        .getService(FrontendOwnedFileEditorSplitBinder::class.java)
        ?.findFrontendFileForBackendMirror(mirrorFilePath)
    }

    fun findBackendMirrorForFrontendFile(file: VirtualFile): String? {
      return ApplicationManager.getApplication()
        .getService(FrontendOwnedFileEditorSplitBinder::class.java)
        ?.findBackendMirrorForFrontendFile(file)
    }
  }
}
