// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.CancellationException

@ApiStatus.Internal
interface GitWorktreeOpenPathProvider {
  companion object {
    val EP_NAME: ExtensionPointName<GitWorktreeOpenPathProvider> = ExtensionPointName("Git4Idea.gitWorktreeOpenPathProvider")
  }

  /**
   * Returns an alternate path that should be opened for the given worktree.
   * Returning `null` lets Git4Idea fall back to the worktree root.
   */
  suspend fun getPathToOpen(project: Project, worktreePath: Path): Path?
}

private val LOG = logger<GitWorktreeOpenPathProvider>()

internal suspend fun resolveWorktreeOpenPath(project: Project, worktreePath: Path): Path {
  val providers = mutableListOf<GitWorktreeOpenPathProvider>()
  GitWorktreeOpenPathProvider.EP_NAME.forEachExtensionSafe(providers::add)

  for (provider in providers) {
    try {
      provider.getPathToOpen(project, worktreePath)?.let {
        return it
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (_: IndexNotReadyException) {
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }
  return worktreePath
}
