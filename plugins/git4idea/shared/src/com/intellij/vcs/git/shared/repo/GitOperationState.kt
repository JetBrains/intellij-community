// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.repo

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
enum class GitOperationState {
  NORMAL,
  REBASE,
  MERGE,
  CHERRY_PICK,
  REVERT,
  DETACHED_HEAD,
}