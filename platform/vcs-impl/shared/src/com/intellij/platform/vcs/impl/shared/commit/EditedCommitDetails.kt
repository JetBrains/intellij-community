// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.commit

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.changes.Change
import com.intellij.platform.vcs.impl.shared.rpc.serializers.ChangeCollectionSerializer
import com.intellij.platform.vcs.impl.shared.rpc.serializers.HashSerializer
import com.intellij.platform.vcs.impl.shared.rpc.serializers.VcsUserSerializer
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsUser
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Serializable
class EditedCommitDetails @ApiStatus.Internal constructor(
  @Serializable(with = VcsUserSerializer::class)
  val currentUser: VcsUser?,
  @Serializable(with = VcsUserSerializer::class)
  val committer: VcsUser,
  @Serializable(with = VcsUserSerializer::class)
  val author: VcsUser,
  @Serializable(with = HashSerializer::class)
  val commitHash: Hash,
  val subject: @NlsSafe String,
  val fullMessage: @NlsSafe String,
  @Serializable(with = ChangeCollectionSerializer::class)
  val changes: Collection<Change>,
) : EditedCommitPresentation

@Serializable
sealed interface EditedCommitPresentation {
  @Serializable
  object Loading : EditedCommitPresentation
}
