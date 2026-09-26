// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@Deprecated("Should not be referenced directly. Only exists to add suspend functions to public ChangeListManager API",
            level = DeprecationLevel.HIDDEN)
interface ChangeListManagerKotlinExtension {
  /**
   * Wait until state update is complete.
   *
   * @see ChangeListManager.invokeAfterUpdate
   */
  suspend fun awaitUpdate()
}

@get:ApiStatus.Internal
val ChangeListManager.unversionedFiles: List<VirtualFile>
  get() = getUnversionedFilesPaths().mapNotNull { it.virtualFile }