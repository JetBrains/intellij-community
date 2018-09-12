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
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.data.DataPackBase
import com.intellij.vcs.log.graph.VisibleGraph
import com.intellij.vcs.log.visible.VisiblePack

class FileHistoryVisiblePack(dataPack: DataPackBase,
                             graph: VisibleGraph<Int>,
                             canRequestMore: Boolean,
                             filters: VcsLogFilterCollection,
                             private val commitsToPaths: Map<Int, MaybeDeletedFilePath>) : VisiblePack(dataPack, graph, canRequestMore,
                                                                                                       filters) {

  fun getFilePath(commit: Int): FilePath? {
    val filePath = commitsToPaths[commit] ?: return null
    return filePath.filePath
  }

  fun isFileDeletedInCommit(commit: Int): Boolean {
    val filePath = commitsToPaths[commit]
    return filePath != null && filePath.deleted
  }
}
