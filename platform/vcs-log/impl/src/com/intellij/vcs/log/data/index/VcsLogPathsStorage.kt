// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.StorageException
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.history.EdgeData
import com.intellij.vcs.log.impl.VcsLogIndexer
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.util.function.ObjIntConsumer

internal interface VcsLogPathsStorage {
  @Throws(IOException::class, StorageException::class)
  fun iterateChangesInCommits(root: VirtualFile, path: FilePath, consumer: ObjIntConsumer<List<ChangeKind>>)

  @Throws(IOException::class)
  fun findRename(parent: VcsLogCommitStorageIndex, child: VcsLogCommitStorageIndex, root: VirtualFile, path: FilePath, isChildPath: Boolean): EdgeData<FilePath?>?

  fun getPathsEncoder(): VcsLogIndexer.PathsEncoder
}

@ApiStatus.Internal
enum class ChangeKind(@JvmField val id: Byte) {
  MODIFIED(0.toByte()),
  NOT_CHANGED(1.toByte()),  // we do not want to have nulls in lists
  ADDED(2.toByte()),
  REMOVED(3.toByte());

  companion object {
    private val KINDS = arrayOfNulls<ChangeKind>(4)

    init {
      for (kind in entries) {
        KINDS[kind.id.toInt()] = kind
      }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getChangeKindById(id: Byte): ChangeKind {
      val kind = if (id >= 0 && id < KINDS.size) KINDS[id.toInt()] else null
      if (kind == null) throw IOException("Change kind by id $id not found.")
      return kind
    }
  }
}