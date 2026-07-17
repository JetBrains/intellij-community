// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

internal object ToolWindowEditorTabFileType : FakeFileType() {
  override fun getName(): String = "TOOL_WINDOW_TAB"

  @NlsSafe
  override fun getDescription(): String = "$name Fake File Type"

  override fun getIcon(): Icon? = null

  override fun isMyFileType(file: VirtualFile): Boolean {
    return file is ToolWindowEditorTabFile && file.fileType === this
  }
}
