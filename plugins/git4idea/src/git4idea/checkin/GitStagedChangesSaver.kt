// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.openapi.vcs.FilePath
import git4idea.repo.GitRepository

internal interface GitStagedChangesSaver {
  fun save(toCommitAdded: Set<FilePath>, toCommitRemoved: Set<FilePath>)
  fun load()

  companion object {
    fun create(repository: GitRepository): GitStagedChangesSaver {
      return GitResetAddStagedChangesSaver(repository)
    }
  }
}