// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.shared.RecentFileKind

/**
 * Handles the application of backend-related file change events to relevant recent file models.
 *
 * To ensure that only relevant data is transmitted via RPC flow, we have to check whether the file received from one of the
 * model listeners (VFS, VCS, DaemonAnalyser, etc.) actually belongs to the recent files model. If it does, the corresponding event
 * is going to be eventually delivered to the frontend model. Otherwise, the event is ignored.
 */
internal object BackendRecentFileEventsController {
  fun applyRelevantEventsToModel(files: List<VirtualFile>, changeKind: FileChangeKind, project: Project) {
    when (changeKind) {
      FileChangeKind.ADDED, FileChangeKind.REMOVED -> {
        BackendRecentFileEventsModel.getInstance(project).scheduleApplyBackendChangesToAllFileKinds(changeKind, files)
      }
      FileChangeKind.UPDATED, FileChangeKind.UPDATED_AND_PUT_ON_TOP -> {
        for (filesKind in RecentFileKind.entries) {
          val knownFilesByKind = BackendRecentFilesModel.getInstance(project).getFilesByKind(filesKind).toSet()
          val relevantUpdates = files.filter { it in knownFilesByKind }
          BackendRecentFileEventsModel.getInstance(project).scheduleApplyBackendChanges(filesKind, changeKind, relevantUpdates)
        }
      }
    }
  }
}