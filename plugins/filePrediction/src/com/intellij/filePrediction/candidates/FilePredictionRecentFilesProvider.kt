// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.candidates

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class FilePredictionRecentFilesProvider : FilePredictionBaseCandidateProvider(30) {
  override fun provideCandidates(project: Project, file: VirtualFile?, refs: Set<VirtualFile>, limit: Int): Collection<VirtualFile> {
    val result = HashSet<VirtualFile>()
    val openFiles = FileEditorManager.getInstance(project).openFiles
    addWithLimit(openFiles.iterator(), result, file, limit)

    val left = limit - result.size
    if (left > 0) {
      val recentFiles = EditorHistoryManager.getInstance(project).files.filter { !result.contains(it) }.takeLast(left + 1)
      addWithLimit(recentFiles.iterator(), result, file, limit)
    }
    return result
  }
}