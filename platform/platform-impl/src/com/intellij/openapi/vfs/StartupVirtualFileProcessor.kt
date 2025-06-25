// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.components.service
import com.intellij.platform.fileEditor.FileEntry
import org.jetbrains.annotations.ApiStatus

/**
 * Provides a way to customize [VirtualFile]s which are opened in editor tabs on the startup.
 *
 * This is needed for RD, so we can initialize [VirtualFile] based on the persisted tabs data ([FileEntry]).
 */
@ApiStatus.Internal
interface StartupVirtualFileProcessor {
  fun processVirtualFileOnStartup(file: VirtualFile, fileEntry: FileEntry)

  companion object {
    fun getInstance(): StartupVirtualFileProcessor = service<StartupVirtualFileProcessor>()
  }
}