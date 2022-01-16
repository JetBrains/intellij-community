// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.data.VcsCommitExternalStatus
import com.intellij.vcs.log.data.util.VcsCommitsDataLoader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Provides additional status of a commit in an external system (build status, metadata, etc.)
 */
@ApiStatus.Experimental
interface VcsCommitExternalStatusProvider<T : VcsCommitExternalStatus> {

  @get:NonNls
  val id: String

  /**
   * Must be disposed when the list of EPs is changed
   */
  @RequiresEdt
  fun createLoader(project: Project): VcsCommitsDataLoader<T>

  @RequiresEdt
  fun getPresentation(project: Project, commit: CommitId, status: T): VcsCommitExternalStatusPresentation?

  companion object {

    internal val EP = ExtensionPointName<VcsCommitExternalStatusProvider<*>>("com.intellij.vcsLogCommitStatusProvider")

    @RequiresEdt
    @JvmStatic
    fun addProviderListChangeListener(disposable: Disposable, listener: () -> Unit) {
      EP.addChangeListener(listener, disposable)
    }
  }
}