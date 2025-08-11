// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ui.ChangeListDragBean
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserLockedFoldersNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Temporary interface to allow moving changes tree code to the shared module.
 * Should be reconsidered and replaced with proper services.
 */
@ApiStatus.Obsolete
@ApiStatus.Internal
interface ChangesTreeCompatibilityProvider {
  fun getBackgroundColorFor(project: Project, obj: Any?): Color?

  fun getPresentablePath(project: Project?, path: VirtualFile, useRelativeRootPaths: Boolean, acceptEmptyPath: Boolean): @NlsSafe String

  fun getPresentablePath(project: Project?, path: FilePath, useRelativeRootPaths: Boolean, acceptEmptyPath: Boolean): @NlsSafe String

  fun getFileStatus(project: Project, file: VirtualFile): FileStatus

  fun getLockedFilesCleanupWorker(project: Project, lockedFoldersNode: ChangesBrowserLockedFoldersNode): Runnable?

  fun getIcon(project: Project?, filePath: FilePath, isDirectory: Boolean): Icon?

  fun logInclusionToggle(project: Project, exclude: Boolean, event: MouseEvent)

  fun logInclusionToggle(project: Project, exclude: Boolean, event: AnActionEvent)

  fun logFileSelected(project: Project, event: MouseEvent)

  fun getSwitchedBranch(project: Project, file: VirtualFile): @NlsSafe String?

  fun showResolveConflictsDialog(project: Project, changes: List<Change>)

  fun acceptIgnoredFilesDrop(project: Project, dragOwner: ChangeListOwner, dragBean: ChangeListDragBean)

  fun showIgnoredViewDialog(project: Project)

  fun isIgnoredInUpdateMode(project: Project): Boolean

  fun showUnversionedViewDialog(project: Project)

  fun isUnversionedInUpdateMode(project: Project): Boolean

  fun resolveLocalFile(path: String): VirtualFile?

  companion object {
    @JvmStatic
    fun getInstance(): ChangesTreeCompatibilityProvider = application.service<ChangesTreeCompatibilityProvider>()
  }
}