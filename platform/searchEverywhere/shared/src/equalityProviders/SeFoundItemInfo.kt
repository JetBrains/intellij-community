// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.equalityProviders

import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeProviderId
import org.jetbrains.annotations.ApiStatus

/**
 * Holds an item that a [com.intellij.platform.searchEverywhere.SeItemsProvider] has found. See [SeItemsEqualityProvider].
 *
 * Use this class instead of [com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo].
 */
@ApiStatus.Experimental
class SeFoundItemInfo @ApiStatus.Internal constructor(
  /**
   * The id of the item in the search results.
   */
  @get:ApiStatus.Internal
  val uuid: String,

  /**
   * The found item. [SeItem.rawObject] gives the found object.
   */
  val item: SeItem,

  /**
   * The id of the provider that has found the [item].
   */
  val providerId: SeProviderId,

  /**
   * The weight of the [item]. An item with a higher weight is higher in the results.
   */
  val weight: Int,

  /**
   * The priority of the provider that has found the [item]. A provider with a higher priority is more important.
   * For items with the same [weight], the item from the provider with the higher priority stays in the results.
   */
  val providerPriority: Int,
) {
  override fun toString(): String = "SeFoundItemInfo(uuid=$uuid, providerId=${providerId.value}, weight=$weight)"
}
