// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point for providing serializable presentation for items from the legacy [com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor]
 */
@ApiStatus.Experimental
interface SeLegacyItemPresentationProvider {
  /**
   * The id should match the id of the legacy contributor
   */
  val id: String

  /**
   * Retrieves the serializable presentation for the given item, if available.
   */
  suspend fun getPresentation(item: Any): SeItemPresentation?

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<SeLegacyItemPresentationProvider> =
      ExtensionPointName("com.intellij.searchEverywhere.legacyItemPresentationProvider")
  }
}