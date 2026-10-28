// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.equalityProviders

import org.jetbrains.annotations.ApiStatus

/**
 * Tells Search Everywhere what to do with a new item. See [SeItemsEqualityProvider.compareItems].
 *
 * Use this type instead of [com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.SEEqualElementsActionType].
 */
@ApiStatus.Experimental
sealed interface SeEqualItemsAction {
  /**
   * Search Everywhere adds the new item.
   * Use it when the new item is not equal to an already found item.
   */
  data object DoNothing : SeEqualItemsAction

  /**
   * Search Everywhere drops the new item.
   * Use it when an already found item shows the same entity better.
   */
  data object Skip : SeEqualItemsAction

  /**
   * Search Everywhere adds the new item and removes the items in [toBeReplaced].
   * Use it when the new item shows the same entity better than the already found items.
   */
  data class Replace(val toBeReplaced: List<SeFoundItemInfo>) : SeEqualItemsAction {
    constructor(toBeReplaced: SeFoundItemInfo) : this(listOf(toBeReplaced))
  }
}
