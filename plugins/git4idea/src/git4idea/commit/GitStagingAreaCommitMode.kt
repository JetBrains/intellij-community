// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.vcs.commit.CommitMode
import git4idea.config.GitVcsApplicationSettings

class GitStagingAreaCommitModeProvider : GitCommitModeProvider {
  override fun getCommitMode(): CommitMode? {
    return if (GitVcsApplicationSettings.getInstance().isStagingAreaEnabled) GitStagingAreaCommitMode else null
  }
}

data object GitStagingAreaCommitMode : CommitMode {
  override fun useCommitToolWindow(): Boolean = true
  override fun hideLocalChangesTab(): Boolean = true
  override fun disableDefaultCommitAction(): Boolean = true
}