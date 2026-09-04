// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.report

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile

internal object JUnitReportEditorBannerDismissState {
  private val DISMISSED_AT_REVISION_KEY = Key.create<Pair<Long, Long>>("junit.report.editor.banner.dismissed.revision")

  fun isDismissed(fileEditor: FileEditor, file: VirtualFile): Boolean {
    val (dismissedModStamp, dismissedLength) = fileEditor.getUserData(DISMISSED_AT_REVISION_KEY) ?: return false
    return dismissedModStamp == file.modificationStamp && dismissedLength == file.length
  }

  fun dismiss(fileEditor: FileEditor, file: VirtualFile) {
    fileEditor.putUserData(DISMISSED_AT_REVISION_KEY, file.modificationStamp to file.length)
  }
}
