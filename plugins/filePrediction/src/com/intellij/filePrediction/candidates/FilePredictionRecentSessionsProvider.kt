// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.candidates

import com.intellij.filePrediction.FilePredictionSessionHistory
import com.intellij.filePrediction.candidates.FilePredictionCandidateSource.RECENT_SESSIONS
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager

internal class FilePredictionRecentSessionsProvider : FilePredictionBaseCandidateProvider(40) {
  override fun provideCandidates(project: Project,
                                 file: VirtualFile?,
                                 refs: Set<VirtualFile>,
                                 limit: Int): Collection<FilePredictionCandidateFile> {
    val result = HashSet<FilePredictionCandidateFile>()
    val paths = FilePredictionSessionHistory.getInstance(project).selectCandidates(limit)
    for (path in paths) {
      val candidate = VirtualFileManager.getInstance().findFileByUrl(VfsUtilCore.pathToUrl(path))
      candidate?.let {
        result.add(FilePredictionCandidateFile(it, RECENT_SESSIONS))
      }
    }
    return result
  }
}