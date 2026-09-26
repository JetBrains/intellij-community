// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

/**
 * Supplies the part of the presentation of a recent file that needs the file name index.
 *
 * The recent files model builds every other field itself, so it works without a contributor. The backend module
 * registers the one contributor of the platform: a monolith and a remote development host have the index, and a light
 * session does not, so a light session shows an empty path text.
 */
@ApiStatus.Internal
interface RecentFilePresentationContributor {
  /**
   * Returns the directory that tells [file] apart from another file of the project with the same name,
   * or null when there is no such file.
   */
  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun getPathText(project: Project, file: VirtualFile): String?

  companion object {
    internal val EP_NAME: ExtensionPointName<RecentFilePresentationContributor> =
      ExtensionPointName("com.intellij.recentFiles.presentationContributor")
  }
}
