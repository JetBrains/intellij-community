// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/settings-contexts.spec.md

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

/**
 * Supplies the roots that a file context anchors on. Only the first extension answers. With no extension, the base
 * directory of the project is the only root. See [fileContextRootUrls].
 */
@ApiStatus.Internal
interface SettingsContentRootsProvider {
  /**
   * Returns the urls of the topmost content roots of [project]. The roots do not overlap, so at most one of them holds
   * a file.
   *
   * @param project the project whose roots to return
   * @return the root urls, or an empty list when the project has no roots yet
   */
  @RequiresReadLock
  fun rootUrls(project: Project): List<String>

  companion object {
    val EP_NAME: ExtensionPointName<SettingsContentRootsProvider> =
      ExtensionPointName("com.intellij.settings.v2.contentRootsProvider")
  }
}
