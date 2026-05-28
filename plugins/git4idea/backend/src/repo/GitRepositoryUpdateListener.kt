// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic

/**
 * Application-wide listener for [GitRepository] updates.
 *
 * Unlike [GitRepositoryChangeListener], this topic is broadcast from the application level
 * to all projects, allowing listeners to react to repository changes across the entire IDE.
 *
 * @see GitRepositoryChangeListener for project-level notifications with full repository access
 */
internal fun interface GitRepositoryUpdateListener {
  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<GitRepositoryUpdateListener> = Topic.create("GitRepository update", GitRepositoryUpdateListener::class.java)
  }

  fun repositoryUpdated(updatedInProject: Project, root: VirtualFile)
}
