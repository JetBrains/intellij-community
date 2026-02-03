// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.FileDropEvent
import com.intellij.openapi.editor.FileDropHandler
import com.intellij.openapi.fileTypes.FileTypeRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal class PatchFileDropHandler : FileDropHandler {
  override suspend fun handleDrop(e: FileDropEvent): Boolean {
    if (e.files.size != 1) return false
    if (!looksLikePatchFile(e.files.first())) return false
    val patchFile = ApplyPatchUtil.getPatchFile(e.files.first()) ?: return false

    withContext(Dispatchers.EDT) {
      ApplyPatchUtil.showAndGetApplyPatch(e.project, patchFile)
    }
    return true
  }

  private fun looksLikePatchFile(file: File): Boolean {
    return FileTypeRegistry.getInstance().getFileTypeByFileName(file.name) === PatchFileType.INSTANCE
  }
}
