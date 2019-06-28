/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.history

import com.intellij.openapi.vcs.FilePath
import com.intellij.vcs.log.VcsLogDataPack
import com.intellij.vcs.log.visible.VisiblePack
import gnu.trove.THashSet

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
    return commitsToPathsMap.values.mapTo(THashSet(FILE_PATH_HASHING_STRATEGY)) { it.filePath }
  }
}

