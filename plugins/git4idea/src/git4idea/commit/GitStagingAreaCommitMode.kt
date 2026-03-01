// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.vcs.commit.CommitMode

internal data class GitStagingAreaCommitMode(
  override val isCommitTwEnabled: Boolean,
) : CommitMode {
  override val isLocalChangesTabHidden: Boolean = true
  override val isDefaultCommitActionDisabled: Boolean = true
}