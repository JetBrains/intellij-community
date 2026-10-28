// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.equalityProviders

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Finds items from [com.intellij.platform.searchEverywhere.SeItemsProvider]s that point to the same entity.
 * Search Everywhere shows only one item for such an entity.
 *
 * For example, these items can be instances of different classes, but point to the same entity:
 * - the same file from the Files provider and from the Recent Files provider
 * - a Java class and the `.java` file that contains this class
 * - different wrappers of the same [com.intellij.psi.PsiElement]
 *
 * Register an implementation in the `com.intellij.searchEverywhere.itemsEqualityProvider` extension point.
 * To compare the new item with one already found item at a time, extend [SeAbstractItemsEqualityProvider].
 *
 * Use this interface instead of [com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider].
 */
@ApiStatus.Experimental
interface SeItemsEqualityProvider {
  /**
   * Compares [newItem] with [alreadyFoundItems] and tells what Search Everywhere must do with [newItem].
   *
   * Search Everywhere calls this function in a read action for each found item, so keep it fast.
   * Do not keep a reference to [alreadyFoundItems], because the collection changes after the call.
   *
   * @param newItem the item that a provider has just found.
   * @param alreadyFoundItems the items that Search Everywhere already shows for the search.
   */
  fun compareItems(newItem: SeFoundItemInfo, alreadyFoundItems: Collection<SeFoundItemInfo>): SeEqualItemsAction

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<SeItemsEqualityProvider> = ExtensionPointName("com.intellij.searchEverywhere.itemsEqualityProvider")
  }
}
