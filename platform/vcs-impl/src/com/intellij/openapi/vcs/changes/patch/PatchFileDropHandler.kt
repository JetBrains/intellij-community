// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FileDropHandler
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.datatransfer.Transferable
import java.io.File

internal class PatchFileDropHandler : FileDropHandler {
  override suspend fun handleDrop(project: Project,
                                  t: Transferable,
                                  files: Collection<File>,
                                  editor: Editor?): Boolean {
    if (files.size != 1) return false
    if (!looksLikePatchFile(files.first())) return false
    val patchFile = ApplyPatchUtil.getPatchFile(files.first()) ?: return false

    withContext(Dispatchers.EDT) {
      ApplyPatchUtil.showAndGetApplyPatch(project, patchFile)
    }
    return true
  }

  private fun looksLikePatchFile(file: File): Boolean {
    return FileTypeRegistry.getInstance().getFileTypeByFileName(file.name) === PatchFileType.INSTANCE
  }
}
