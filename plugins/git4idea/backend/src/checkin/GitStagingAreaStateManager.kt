// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import git4idea.repo.GitRepository
import java.io.Closeable

/**
 * Prepares the staging area for a commit and restores it after
 * It is intended to be used only once
 */
internal interface GitStagingAreaStateManager : Closeable {
  /**
   * Takes which paths are intended to be added to the staging area and which to
   * be removed for the commit. Doesn't stage or unstage them,
   * only excludes all other paths from the staging area
   */
  fun prepareStagingArea(toAdd: Set<FilePath>, toRemove: Set<FilePath>)

  /**
   * Rollback the changes done by [prepareStagingArea]
   */
  fun restore()

  override fun close() {
    restore()
  }

  companion object {
    fun create(repository: GitRepository): GitStagingAreaStateManager {
      if (Registry.`is`("git.commit.staged.saver.use.index.info")) {
        return GitIndexInfoStagingAreaStateManager(repository)
      }
      return GitResetAddStagingAreaStateManager(repository)
    }
  }
}