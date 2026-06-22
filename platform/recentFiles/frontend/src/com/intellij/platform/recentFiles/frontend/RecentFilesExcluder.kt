// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.shared.RecentFileKind
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface RecentFilesExcluder {
  fun isExcludedFromRecentlyOpened(project: Project, file: VirtualFile): Boolean

  fun isExcludedFromRecentlyEdited(project: Project, file: VirtualFile): Boolean = false

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<RecentFilesExcluder> = ExtensionPointName<RecentFilesExcluder>("com.intellij.recentFiles.excluder")
  }
}

internal fun isExcludedFromRecentFiles(project: Project, fileKind: RecentFileKind, file: VirtualFile): Boolean {
  return RecentFilesExcluder.EP_NAME.findFirstSafe { ext ->
    when (fileKind) {
      RecentFileKind.RECENTLY_EDITED -> ext.isExcludedFromRecentlyEdited(project, file)
      RecentFileKind.RECENTLY_OPENED, RecentFileKind.RECENTLY_OPENED_UNPINNED -> ext.isExcludedFromRecentlyOpened(project, file)
    }
  } != null
}
