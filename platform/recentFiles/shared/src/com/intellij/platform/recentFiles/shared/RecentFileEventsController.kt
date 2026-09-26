// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.ide.actions.shouldUseFallbackSwitcher
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.runtime.product.ProductMode
import org.jetbrains.annotations.ApiStatus

/**
 * The entry point of the file events into the recent files model of this process.
 *
 * The backend module reports the files whose presentation depends on the VCS through [presentationChanged] and
 * [allPresentationsChanged]. The listeners of this module report the history order, the removals and the analysis
 * results through the internal functions. The model checks that a reported file belongs to it before it sends an
 * event, so a caller does not filter its files.
 *
 * Every function drops the event when this process does not host the model, see [doesProcessHostRecentFilesModel].
 */
@ApiStatus.Internal
object RecentFileEventsController {
  /**
   * Reports that the presentation of [files] can have changed: the name, the problem marker, the VCS status, or the highlighting.
   */
  fun presentationChanged(project: Project, files: List<VirtualFile>) {
    apply(project, files, FileChangeKind.UPDATED)
  }

  /**
   * Reports that the presentation of every file in the model can have changed.
   */
  fun allPresentationsChanged(project: Project) {
    if (!doesProcessHostRecentFilesModel()) return
    val files = RecentFileKind.entries.flatMap { RecentFilesModel.getInstance(project).getFilesByKind(it) }.distinct()
    apply(project, files, FileChangeKind.UPDATED)
  }

  /**
   * Reports the [files] that moved to the top of the editor history.
   */
  internal fun filesAdded(project: Project, files: List<VirtualFile>) {
    apply(project, files, FileChangeKind.ADDED)
  }

  /**
   * Reports the [files] that the VFS deleted.
   */
  internal fun filesRemoved(project: Project, files: List<VirtualFile>) {
    apply(project, files, FileChangeKind.REMOVED)
  }

  private fun apply(project: Project, files: List<VirtualFile>, changeKind: FileChangeKind) {
    if (!doesProcessHostRecentFilesModel()) return
    val filesWithoutDirectories = files.filter { !it.isDirectory }
    thisLogger().debug("Trying to apply changes for ${filesWithoutDirectories.size} files out of total ${files.size} virtual files to the model, change kind: $changeKind")
    thisLogger().trace { "Files to apply changes for: ${filesWithoutDirectories.joinToString { it.name }}" }

    RecentFileEventsModel.getInstance(project).scheduleApplyChanges(changeKind, filesWithoutDirectories)
  }
}

/**
 * Returns true when this process serves the recent files model itself.
 *
 * The rule mirrors [com.intellij.ide.rpc.awaitWithLocalFallback]: a strictly light session has no backend to ask, so it
 * serves the model, and every other frontend process gets the model from its backend. The fallback switcher keeps its
 * own model, so this process hosts nothing while that switcher is in use.
 */
internal fun doesProcessHostRecentFilesModel(): Boolean {
  if (shouldUseFallbackSwitcher()) return false
  // Strictly LIGHT, not isLight: LIGHT_WITH_RD_CONNECTION already holds the connection and takes the model from its backend.
  if (IdeProductMode.getInstance().currentMode == ProductMode.LIGHT) return true
  return !IdeProductMode.isFrontend
}
