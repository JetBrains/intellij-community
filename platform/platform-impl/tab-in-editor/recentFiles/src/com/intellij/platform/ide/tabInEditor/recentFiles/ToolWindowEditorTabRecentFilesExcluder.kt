// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.tabInEditor.recentFiles

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabFile
import com.intellij.platform.recentFiles.shared.RecentFilesExcluder

internal class ToolWindowEditorTabRecentFilesExcluder : RecentFilesExcluder {
  override fun isExcludedFromRecentlyOpened(project: Project, file: VirtualFile): Boolean {
    return false
  }

  override fun isExcludedFromRecentlyEdited(project: Project, file: VirtualFile): Boolean {
    return file is ToolWindowEditorTabFile
  }
}
