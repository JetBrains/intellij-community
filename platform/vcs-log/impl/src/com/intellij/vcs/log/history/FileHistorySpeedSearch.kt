// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.ui.table.IndexSpeedSearch
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.visible.VisiblePack
import it.unimi.dsi.fastutil.ints.Int2ObjectMap

internal class FileHistorySpeedSearch(project: Project, index: VcsLogIndex, storage: VcsLogStorage, component: VcsLogGraphTable)
  : IndexSpeedSearch(project, index, storage, component) {
  // read in the background by the async speed search
  @Volatile
  var visiblePack: VisiblePack = VisiblePack.EMPTY
    set(value) {
      field = value
      refreshSelection()
    }

  override fun isSpeedSearchEnabled(): Boolean = true

  override fun getCachedCommitMetadataById(commitId: VcsLogCommitStorageIndex): VcsCommitMetadata? {
    return visiblePack.getUserData(COMMIT_METADATA)?.get(commitId)
  }

  companion object {
    internal val COMMIT_METADATA: Key<Int2ObjectMap<VcsCommitMetadata>> = Key.create("FileHistory.CommitMetadata")
  }
}