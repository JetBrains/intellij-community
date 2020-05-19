// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.vcs.FilePath
import com.intellij.vcs.log.VcsLogDataPack
import com.intellij.vcs.log.visible.VisiblePack
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet

object FileHistoryPaths {
  val VcsLogDataPack.fileHistory: FileHistory
    get() {
      if (this !is VisiblePack) return EMPTY_HISTORY
      return this.getAdditionalData<Any>() as? FileHistory ?: EMPTY_HISTORY
    }

  private val VcsLogDataPack.commitsToPathsMap: Map<Int, MaybeDeletedFilePath>
    get() = fileHistory.commitsToPathsMap

  @JvmStatic
  fun VcsLogDataPack.hasPathsInformation(): Boolean {
    if (this !is VisiblePack) return false
    return this.getAdditionalData<Any>() is FileHistory
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
    return commitsToPathsMap.values.mapTo(ObjectOpenCustomHashSet(FILE_PATH_HASHING_STRATEGY)) { it.filePath }
  }
}

