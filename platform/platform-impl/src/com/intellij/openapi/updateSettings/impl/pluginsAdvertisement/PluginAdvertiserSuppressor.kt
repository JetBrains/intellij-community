// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Suppresses plugin advertiser editor notifications for files that are already handled by user-configured tooling
 * (e.g., a manually configured LSP server).
 */
@ApiStatus.Internal
interface PluginAdvertiserSuppressor {
  /**
   * Called in a non-blocking read action on a background thread; must be fast (do not access file content or file type).
   */
  fun isSuppressedFor(project: Project, file: VirtualFile): Boolean
}
