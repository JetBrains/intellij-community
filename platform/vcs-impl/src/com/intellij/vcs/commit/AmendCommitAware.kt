// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitDetails
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsUser
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.CancellablePromise

@ApiStatus.Experimental
interface AmendCommitAware {
  fun isAmendCommitSupported(): Boolean

  @Throws(VcsException::class)
  fun getLastCommitMessage(root: VirtualFile): String?

  fun getAmendCommitDetails(root: VirtualFile): CancellablePromise<EditedCommitDetails>
}

@ApiStatus.Internal
@ApiStatus.Experimental
class EditedCommitDetailsImpl(
  override val currentUser: VcsUser?,
  commit: VcsFullCommitDetails
) : EditedCommitDetails {
  override val commitHash: Hash = commit.id
  override val committer: VcsUser = commit.committer
  override val author: VcsUser = commit.author
  override val subject: String = commit.subject
  override val fullMessage: String = commit.fullMessage
  override val changes: Collection<Change> = commit.changes
}
