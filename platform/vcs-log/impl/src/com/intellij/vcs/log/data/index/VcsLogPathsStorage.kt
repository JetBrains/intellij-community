// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.StorageException
import com.intellij.vcs.log.history.EdgeData
import com.intellij.vcs.log.impl.VcsLogIndexer
import java.io.IOException
import java.util.function.ObjIntConsumer

internal interface VcsLogPathsStorage {
  @Throws(IOException::class, StorageException::class)
  fun iterateChangesInCommits(root: VirtualFile, path: FilePath, consumer: ObjIntConsumer<List<VcsLogPathsIndex.ChangeKind>>)

  @Throws(IOException::class)
  fun findRename(parent: Int, child: Int, root: VirtualFile, path: FilePath, isChildPath: Boolean): EdgeData<FilePath?>?

  fun getPathsEncoder(): VcsLogIndexer.PathsEncoder
}
