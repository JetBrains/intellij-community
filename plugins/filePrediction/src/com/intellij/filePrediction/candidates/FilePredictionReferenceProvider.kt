// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.candidates

import com.intellij.filePrediction.candidates.FilePredictionCandidateSource.REFERENCE
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class FilePredictionReferenceProvider : FilePredictionBaseCandidateProvider(10) {
  override fun provideCandidates(project: Project, file: VirtualFile?, refs: Set<VirtualFile>, limit: Int): Collection<FilePredictionCandidateFile> {
    if (refs.isEmpty() || file == null) {
      return emptySet()
    }

    val result = HashSet<FilePredictionCandidateFile>()
    addWithLimit(refs.iterator(), result, REFERENCE, file, limit)
    return result
  }
}