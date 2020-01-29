// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.data.VcsLogData
import com.jetbrains.changeReminder.getGitRootFiles
import com.jetbrains.changeReminder.repository.Commit
import com.jetbrains.changeReminder.repository.FilesHistoryProvider

class FileProbabilityRequest(private val project: Project,
                             private val dataManager: VcsLogData,
                             private val changeListFiles: Collection<FilePath>) {

  private fun predict(candidate: FilePath, files: Collection<FilePath>, root: VirtualFile, history: FilesHistoryProvider): Double {
    val commit = Commit(-1, System.currentTimeMillis(), dataManager.currentUser[root]?.name ?: "", changeListFiles.toSet())
    return ProbabilityProvider.predict(commit, candidate, history.getFilesHistory(root, files).toSet())
  }

  fun calculate(candidate: FilePath): Double {
    val history = dataManager.index.dataGetter?.let { FilesHistoryProvider(project, dataManager, it) } ?: return 0.0

    val roots = getGitRootFiles(project, changeListFiles)
    return roots.mapNotNull { (root, files) ->
      if (dataManager.index.isIndexed(root)) {
        predict(candidate, files, root, history)
      }
      else {
        null
      }
    }.max() ?: 0.0
  }
}

