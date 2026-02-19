// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface VcsSharedChecker {
  companion object {
    @JvmField
    val EP_NAME: ProjectExtensionPointName<VcsSharedChecker> = ProjectExtensionPointName<VcsSharedChecker>("com.intellij.vcsSharedChecker")
  }

  fun getSupportedVcs(): VcsKey

  /**
   * Check if a [path] is shared under [getSupportedVcs] VCS.
   * In compare to [com.intellij.vcsUtil.VcsImplUtil.isProjectSharedInVcs], execute specific "native" check in the corresponding VCS.
   *
   * [path] - The path (e.g., directory) to check
   */
  @RequiresBackgroundThread
  fun isPathSharedInVcs(project: Project, path: Path): Boolean
}
