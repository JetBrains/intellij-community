// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.diff

import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.frontend.RecentFilesNavigator

internal class DiffRecentFilesNavigator : RecentFilesNavigator {
  override fun getEditorOpenOptions(project: Project, file: VirtualFile): FileEditorManagerImpl.OpenMode? {
    // TODO - "Open Diff as Editor Tab" advanced setting is duplicated on client and host
    return if (file.isDiffVirtualFile() && DiffEditorTabFilesManager.isDiffInWindow)
      FileEditorManagerImpl.OpenMode.NEW_WINDOW
    else null
  }
}