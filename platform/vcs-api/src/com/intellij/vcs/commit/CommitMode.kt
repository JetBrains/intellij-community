// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import org.jetbrains.annotations.ApiStatus

interface CommitMode {
  val isCommitTwEnabled: Boolean
  val isLocalChangesTabHidden: Boolean get() = false
  val isDefaultCommitActionDisabled: Boolean get() = false

  object PendingCommitMode : CommitMode {
    // Enable 'Commit' toolwindow before vcses are activated
    override val isCommitTwEnabled: Boolean = true

    // Disable `Commit` action until vcses are activated
    override val isDefaultCommitActionDisabled: Boolean = true
  }

  object ModalCommitMode : CommitMode {
    override val isCommitTwEnabled: Boolean = false
  }

  data class NonModalCommitMode @ApiStatus.Internal constructor(
    override val isCommitTwEnabled: Boolean,
    val isToggleMode: Boolean,
  ) : CommitMode
}
