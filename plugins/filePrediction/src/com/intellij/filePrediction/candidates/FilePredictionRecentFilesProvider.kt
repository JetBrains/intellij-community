// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.candidates

import com.intellij.filePrediction.candidates.FilePredictionCandidateSource.OPEN
import com.intellij.filePrediction.candidates.FilePredictionCandidateSource.RECENT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class FilePredictionRecentFilesProvider : FilePredictionBaseCandidateProvider(30) {
  override fun provideCandidates(project: Project, file: VirtualFile?, refs: Set<VirtualFile>, limit: Int): Collection<FilePredictionCandidateFile> {
    val result = HashSet<FilePredictionCandidateFile>()
    val openFiles = FileEditorManager.getInstance(project).openFiles
    addWithLimit(openFiles.iterator(), result, OPEN, file, limit)

    val left = limit - result.size
    if (left > 0) {
      val addedFiles = result.map { it.file }.toSet()
      val recentFiles = EditorHistoryManager.getInstance(project).files.filter { !addedFiles.contains(it) }.takeLast(left + 1)
      addWithLimit(recentFiles.iterator(), result, RECENT, file, limit)
    }
    return result
  }
}