// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VirtualFile

internal class ToolWindowEditorTabTitleProvider : EditorTabTitleProvider {
  override fun getEditorTabTitle(
    project: Project,
    file: VirtualFile,
  ): @NlsContexts.TabTitle String? {
    if (file !is ToolWindowEditorTabFile) return null
    return file.tabPresentation?.title
  }

  override fun getEditorTabTooltipHtml(project: Project, virtualFile: VirtualFile): HtmlChunk? {
    if (virtualFile !is ToolWindowEditorTabFile) return null
    return virtualFile.tabPresentation?.tooltip
  }
}
