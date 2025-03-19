// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ShelveSilentlyTitleProvider {
  suspend fun getTitle(project: Project, changes: Collection<Change>): String?

  companion object {
    val EP_NAME: ExtensionPointName<ShelveSilentlyTitleProvider> = ExtensionPointName("com.intellij.vcs.shelveSilentlyTitleProvider")

    @RequiresBackgroundThread
    @JvmStatic
    fun suggestTitle(project: Project, changes: Collection<Change>): String? = EP_NAME.computeSafeIfAny { t ->
      runBlockingCancellable {
        t.getTitle(project, changes)
      }
    }
  }
}