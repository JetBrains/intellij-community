// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Tells the frontend what only the backend knows about one tab.
 *
 * `SeTabsCustomizer` is the frontend counterpart. It shapes a tab from what the frontend already
 * holds, such as the name and the order. This extension answers from the backend model instead, so a
 * thin client gets the same answer as a monolith.
 *
 * One extension answers for one tab. A tab with no extension keeps every default.
 */
@ApiStatus.Internal
interface SeTabBackendInfoProvider {
  val tabId: String

  suspend fun isTabAvailable(project: Project): Boolean

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<SeTabBackendInfoProvider> =
      ExtensionPointName("com.intellij.searchEverywhere.tabBackendInfoProvider")

    @ApiStatus.Internal
    suspend fun isTabAvailable(project: Project, tabId: String): Boolean =
      EP_NAME.extensionList.firstOrNull { it.tabId == tabId }?.isTabAvailable(project) ?: true
  }
}
