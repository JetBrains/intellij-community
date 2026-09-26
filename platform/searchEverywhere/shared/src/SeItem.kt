// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import org.jetbrains.annotations.ApiStatus

/**
 * Search result item coming from SeItemsProvider
 * see [SeItemsProvider]
 */
@ApiStatus.Experimental
interface SeItem {
  /**
   * Raw unwrapped object inside the SeItem
   */
  val rawObject: Any get() = this

  /**
   * Weight of the item. Higher weight means higher the item is supposed to be placed in the results list.
   */
  fun weight(): Int

  /**
   * Serializable presentation of the item used in the search results list.
   */
  suspend fun presentation(): SeItemPresentation
}