// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FilePath
import com.intellij.util.containers.CollectionFactory
import com.intellij.vcs.log.VcsLogDataPack
import com.intellij.vcs.log.visible.VisiblePack

object FileHistoryPaths {
  private val FILE_HISTORY = Key.create<FileHistory>("FILE_HISTORY")

  val VcsLogDataPack.fileHistory: FileHistory
    get() {
      if (this !is VisiblePack) return FileHistory.EMPTY
      return FILE_HISTORY.get(this) ?: FileHistory.EMPTY
    }

  private val VcsLogDataPack.commitsToPathsMap: Map<Int, MaybeDeletedFilePath>
    get() = fileHistory.commitsToPathsMap

  @JvmStatic
  fun VcsLogDataPack.hasPathsInformation(): Boolean {
    if (this !is VisiblePack) return false
    return FILE_HISTORY.isIn(this)
  }

  fun VisiblePack.withFileHistory(fileHistory: FileHistory): VisiblePack {
    putUserData(FILE_HISTORY, fileHistory)
    return this
  }

  @JvmStatic
  fun VcsLogDataPack.filePath(commit: Int): FilePath? = this.commitsToPathsMap[commit]?.filePath

  @JvmStatic
  fun VcsLogDataPack.filePathOrDefault(commit: Int): FilePath? {
    return this.commitsToPathsMap[commit]?.filePath ?: FileHistoryFilterer.getFilePath(filters)
  }

  @JvmStatic
  fun VcsLogDataPack.isDeletedInCommit(commit: Int): Boolean = this.commitsToPathsMap[commit]?.deleted ?: false

  @JvmStatic
  fun VcsLogDataPack.filePaths(): Set<FilePath> {
    return commitsToPathsMap.values.mapTo(CollectionFactory.createCustomHashingStrategySet(FILE_PATH_HASHING_STRATEGY)) { it.filePath }
  }
}

