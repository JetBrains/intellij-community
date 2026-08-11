// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface RecentFilesExcluder {
  fun isExcludedFromRecentlyOpened(project: Project, file: VirtualFile): Boolean

  fun isExcludedFromRecentlyEdited(project: Project, file: VirtualFile): Boolean = false

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<RecentFilesExcluder> = ExtensionPointName("com.intellij.recentFiles.excluder")
  }
}

@ApiStatus.Internal
fun isAllowedInRecentFilesModel(project: Project, fileKind: RecentFileKind, file: VirtualFile): Boolean {
  return file.isValid &&
         (file !is IdeDocumentHistoryImpl.OptionallyIncluded || file.isIncludedInDocumentHistory(project)) &&
         RecentFilesExcluder.EP_NAME.findFirstSafe { ext ->
           when (fileKind) {
             RecentFileKind.RECENTLY_EDITED -> ext.isExcludedFromRecentlyEdited(project, file)
             RecentFileKind.RECENTLY_OPENED, RecentFileKind.RECENTLY_OPENED_UNPINNED -> ext.isExcludedFromRecentlyOpened(project, file)
           }
         } == null
}
