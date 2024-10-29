// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
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

@ApiStatus.Experimental
interface EditedCommitDetails {
  val currentUser: VcsUser?
  val commit: VcsFullCommitDetails
}

@ApiStatus.Internal
@ApiStatus.Experimental
class EditedCommitDetailsImpl(
  override val currentUser: VcsUser?,
  override val commit: VcsFullCommitDetails
) : EditedCommitDetails

sealed interface EditedCommitPresentation {
  object Loading : EditedCommitPresentation
  class Details(delegate: EditedCommitDetails) : EditedCommitPresentation, EditedCommitDetails by delegate
}
