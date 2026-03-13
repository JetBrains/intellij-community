// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

/**
 * Listener that is called before VCS Log refresh in [com.intellij.vcs.log.ui.actions.RefreshLogAction] is performed.
 *
 * Can be used to trigger git repository refresh or other preparatory actions
 * before the log data is refreshed.
 */
@ApiStatus.Internal
interface VcsLogRefreshActionListener {
  @RequiresBackgroundThread
  fun beforeRefresh(project: Project, roots: Collection<VirtualFile>)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<VcsLogRefreshActionListener> = ExtensionPointName("com.intellij.vcsLogRefreshActionListener")
  }
}