// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

internal class ToolWindowEditorTabFileIconProvider : FileIconProvider {
  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    val editorTabFile = file as? ToolWindowEditorTabFile ?: return null
    return if (editorTabFile.fileType === ToolWindowEditorTabFileType) editorTabFile.tabIcon else null
  }
}
