// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.util.indexing.StorageException
import com.intellij.vcs.log.VcsUser
import it.unimi.dsi.fastutil.ints.IntSet
import java.io.IOException

internal interface VcsLogUsersStorage {
  fun getCommitterForCommit(commitId: Int): VcsUser?

  fun getAuthorForCommit(commitId: Int): VcsUser?

  fun getAuthorForCommits(commitIds: Iterable<Int>): Map<Int, VcsUser> {
    return commitIds.mapNotNull { commitId -> getAuthorForCommit(commitId)?.let { user -> commitId to user } }.toMap()
  }

  fun getCommitterForCommits(commitIds: Iterable<Int>): Map<Int, VcsUser> {
    return commitIds.mapNotNull { commitId -> getCommitterForCommit(commitId)?.let { user -> commitId to user } }.toMap()
  }

  @Throws(IOException::class, StorageException::class)
  fun getCommitsForUsers(users: Set<VcsUser>): IntSet?
}
