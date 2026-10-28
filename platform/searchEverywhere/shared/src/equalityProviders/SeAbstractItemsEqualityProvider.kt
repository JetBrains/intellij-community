// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.equalityProviders

import org.jetbrains.annotations.ApiStatus

/**
 * A [SeItemsEqualityProvider] that compares the new item with one already found item at a time.
 *
 * When [areEqual] returns `true`, the item with the higher [SeFoundItemInfo.weight] stays in the results.
 * For the same weight, the item with the higher [SeFoundItemInfo.providerPriority] stays.
 *
 * Use this class instead of [com.intellij.ide.actions.searcheverywhere.AbstractEqualityProvider].
 */
@ApiStatus.Experimental
abstract class SeAbstractItemsEqualityProvider : SeItemsEqualityProvider {
  final override fun compareItems(newItem: SeFoundItemInfo, alreadyFoundItems: Collection<SeFoundItemInfo>): SeEqualItemsAction {
    val toBeReplaced = ArrayList<SeFoundItemInfo>()
    var hasBetterItem = false

    for (alreadyFoundItem in alreadyFoundItems) {
      if (!areEqual(newItem, alreadyFoundItem)) continue

      if (ITEM_COMPARATOR.compare(newItem, alreadyFoundItem) > 0) {
        toBeReplaced.add(alreadyFoundItem)
      }
      else {
        hasBetterItem = true
      }
    }

    return when {
      toBeReplaced.isNotEmpty() -> SeEqualItemsAction.Replace(toBeReplaced)
      hasBetterItem -> SeEqualItemsAction.Skip
      else -> SeEqualItemsAction.DoNothing
    }
  }

  /**
   * Returns `true` when [newItem] and [alreadyFoundItem] point to the same entity.
   */
  protected abstract fun areEqual(newItem: SeFoundItemInfo, alreadyFoundItem: SeFoundItemInfo): Boolean
}

private val ITEM_COMPARATOR: Comparator<SeFoundItemInfo> =
  compareBy<SeFoundItemInfo> { it.weight }.thenBy { it.providerPriority }
