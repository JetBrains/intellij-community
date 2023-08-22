// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.util.containers.MultiMap
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vfs.AsyncVfsEventsListener

class GitUntrackedDirtyScopeListener(val repositoryManager: GitRepositoryManager) : AsyncVfsEventsListener {
  override fun filesChanged(events: List<VFileEvent>) {
    if (repositoryManager.repositories.isEmpty()) return

    val map = MultiMap<GitRepository, FilePath>()

    for (event in events) {
      for (filePath in getAffectedFilePaths(event)) {
        val repo = repositoryManager.getRepositoryForFileQuick(filePath) ?: continue
        map.putValue(repo, filePath)
      }
    }

    for ((repo, filePaths) in map.entrySet()) {
      repo.untrackedFilesHolder.markPossiblyUntracked(filePaths)
    }
  }

  private fun getAffectedFilePaths(event: VFileEvent): List<FilePath> {
    if (event is VFileContentChangeEvent) return emptyList()
    if (event is VFilePropertyChangeEvent && !event.isRename) return emptyList()

    val affectedFilePaths = ArrayList<FilePath>(2)
    val isDirectory = if (event is VFileCreateEvent) event.isDirectory else event.file!!.isDirectory

    affectedFilePaths.add(VcsUtil.getFilePath(event.path, isDirectory))

    if (event is VFileMoveEvent) {
      affectedFilePaths.add(VcsUtil.getFilePath(event.oldPath, isDirectory))
    }
    else if (event is VFilePropertyChangeEvent && event.isRename) {
      affectedFilePaths.add(VcsUtil.getFilePath(event.oldPath, isDirectory))
    }

    return affectedFilePaths
  }
}