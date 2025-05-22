// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.github.git.GHShareProjectUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Moved to [com.intellij.vcs.github.git.GHShareProjectUtil]
 * @deprecated Use [com.intellij.vcs.github.git.GHShareProjectUtil] instead.
 */
@ApiStatus.Obsolete
@Suppress("unused")
object GHShareProjectUtil {
  /**
   * Moved to [com.intellij.vcs.github.git.GHShareProjectUtil.shareProjectOnGithub]
   * @deprecated Use [com.intellij.vcs.github.git.GHShareProjectUtil.shareProjectOnGithub] instead.
   */
  @JvmStatic
  @ApiStatus.Obsolete
  fun shareProjectOnGithub(project: Project, file: VirtualFile?): Unit =
    GHShareProjectUtil.shareProjectOnGithub(project, file)
}