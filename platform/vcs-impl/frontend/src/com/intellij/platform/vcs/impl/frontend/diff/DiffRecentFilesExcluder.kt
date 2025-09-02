// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.diff

import com.intellij.diff.impl.DiffSettingsHolder
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.frontend.RecentFilesExcluder

internal class DiffRecentFilesExcluder : RecentFilesExcluder {
  override fun isExcludedFromRecentlyOpened(project: Project, file: VirtualFile): Boolean {
    if (!file.isDiffVirtualFile()) return false

    val inclusionState = DiffSettingsHolder.DiffSettings.getSettings().isIncludedInNavigationHistory
    val shouldInclude = when (inclusionState) {
      DiffSettingsHolder.IncludeInNavigationHistory.OnlyIfOpen -> FileEditorManager.getInstance(project).isFileOpen(file)
      DiffSettingsHolder.IncludeInNavigationHistory.Always -> true
      DiffSettingsHolder.IncludeInNavigationHistory.Never -> false
    }
    return !shouldInclude
  }
}