// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.candidates.vcs

import com.intellij.filePrediction.candidates.FilePredictionBaseCandidateProvider
import com.intellij.filePrediction.candidates.FilePredictionCandidateFile
import com.intellij.filePrediction.candidates.FilePredictionCandidateSource.VCS
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

internal class FilePredictionVcsFilesProvider : FilePredictionBaseCandidateProvider(5) {
  override fun provideCandidates(project: Project, file: VirtualFile?, refs: Set<VirtualFile>, limit: Int): Collection<FilePredictionCandidateFile> {
    val result = HashSet<FilePredictionCandidateFile>()
    val changes = ChangeListManager.getInstance(project).allChanges
    for (change in changes) {
      if (result.size >= limit) break

      change.virtualFile?.let {
        if (!it.isDirectory && file != it && it !is LightVirtualFile) {
          result.add(FilePredictionCandidateFile(it, VCS))
        }
      }
    }
    return result
  }
}