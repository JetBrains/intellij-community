// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.report

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile

internal object JUnitReportEditorBannerDismissState {
  private val DISMISSED_AT_REVISION_KEY = Key.create<Pair<Long, Long>>("junit.report.editor.banner.dismissed.revision")

  fun isDismissed(file: VirtualFile): Boolean {
    val dismissed = file.getUserData(DISMISSED_AT_REVISION_KEY) ?: return false
    return dismissed.first == file.modificationStamp && dismissed.second == file.length
  }

  fun dismiss(file: VirtualFile) {
    file.putUserData(DISMISSED_AT_REVISION_KEY, file.modificationStamp to file.length)
  }

  /** Clears the close-button hide state so the banner can appear again for this file. */
  fun clearDismissState(file: VirtualFile) {
    file.putUserData(DISMISSED_AT_REVISION_KEY, null)
  }
}
