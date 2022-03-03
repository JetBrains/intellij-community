// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.ui.table.IndexSpeedSearch
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.visible.VisiblePack

class FileHistorySpeedSearch(project: Project, index: VcsLogIndex, storage: VcsLogStorage, component: VcsLogGraphTable)
  : IndexSpeedSearch(project, index, storage, component) {
  var visiblePack: VisiblePack = VisiblePack.EMPTY
    set(value) {
      field = value
      refreshSelection()
    }

  override fun isSpeedSearchEnabled(): Boolean = true

  override fun getCommitMetadata(row: Int): VcsCommitMetadata? {
    val commits = visiblePack.getUserData(COMMIT_METADATA) ?: return super.getCommitMetadata(row)
    return commits[getCommitId(row)] ?: return super.getCommitMetadata(row)
  }

  companion object {
    val COMMIT_METADATA = Key.create<Map<Int, VcsCommitMetadata>>("FileHistory.CommitMetadata")
  }
}