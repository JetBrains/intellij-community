// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.util.indexing.StorageException
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.VcsLogIndexer
import it.unimi.dsi.fastutil.ints.IntSet
import java.io.IOException

internal interface VcsLogUserBiMap {
  @Throws(IOException::class)
  fun isEmpty(): Boolean

  fun getAuthorForCommit(commit: Int): VcsUser?

  fun getUserId(user: VcsUser): Int

  fun getUserById(id: Int): VcsUser?

  fun update(index: Int, detail: VcsLogIndexer.CompressedDetails)

  @Throws(StorageException::class)
  fun flush()

  @Throws(IOException::class, StorageException::class)
  fun getCommitsForUsers(users: Set<VcsUser?>): IntSet?
}