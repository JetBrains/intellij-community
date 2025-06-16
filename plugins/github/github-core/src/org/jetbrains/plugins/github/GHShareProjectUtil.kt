// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus


/**
 * Only used to avoid creating a circular dependency between the github-git and github-core modules.
 */
@ApiStatus.Internal
@ApiStatus.ScheduledForRemoval
@Deprecated("Do not use")
interface GHShareProjectCompatibilityExtension {
  fun shareProjectOnGithub(project: Project, file: VirtualFile?)
}

private val EP_NAME = ExtensionPointName<GHShareProjectCompatibilityExtension>("com.intellij.github.ghShareProjectCompatibilityExtension")

/**
 * Moved to [com.intellij.vcs.github.git.GHShareProjectUtil]
 * @deprecated Use [com.intellij.vcs.github.git.GHShareProjectUtil] instead.
 */
@Suppress("unused")
@Deprecated("Use com.intellij.vcs.github.git.GHShareProjectUtil instead")
object GHShareProjectUtil {
  /**
   * Moved to [com.intellij.vcs.github.git.GHShareProjectUtil.shareProjectOnGithub]
   * @deprecated Use [com.intellij.vcs.github.git.GHShareProjectUtil.shareProjectOnGithub] instead.
   */
  @JvmStatic
  @Deprecated("Use com.intellij.vcs.github.git.GHShareProjectUtil instead")
  fun shareProjectOnGithub(project: Project, file: VirtualFile?): Unit {
    EP_NAME.extensionList.firstOrNull()?.shareProjectOnGithub(project, file)
  }
}