// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePreCloseCheck

internal class ToolWindowEditorTabPreCloseCheck : VirtualFilePreCloseCheck {
  override fun canCloseFile(file: VirtualFile): Boolean {
    val tabFile = file as? ToolWindowEditorTabFile ?: return true
    val support = ToolWindowEditorTabSupportUtil.getSupport(tabFile.toolWindowId) ?: return true
    return support.canCloseFile(tabFile.project, tabFile.content)
  }

  override fun filterFilesToClose(files: Collection<VirtualFile>): Collection<VirtualFile> = files.filter(::canCloseFile)
}
