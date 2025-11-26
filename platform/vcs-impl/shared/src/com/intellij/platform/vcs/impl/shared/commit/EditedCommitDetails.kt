// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.commit

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsUser
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class EditedCommitDetails @ApiStatus.Internal constructor(
  val currentUser: VcsUser?,
  val committer: VcsUser,
  val author: VcsUser,
  val commitHash: Hash,
  val subject: @NlsSafe String,
  val fullMessage: @NlsSafe String,
  val changes: Collection<Change>,
) : EditedCommitPresentation

sealed interface EditedCommitPresentation {
  object Loading : EditedCommitPresentation
}
