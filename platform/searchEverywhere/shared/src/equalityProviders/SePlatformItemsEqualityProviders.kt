// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.equalityProviders

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.actionSystem.AnAction
import org.jetbrains.annotations.ApiStatus

/**
 * Finds items with equal raw objects.
 *
 * It replaces [com.intellij.ide.actions.searcheverywhere.TrivialElementsEqualityProvider] in the new Search Everywhere.
 */
@ApiStatus.Internal
class SeTrivialItemsEqualityProvider : SeAbstractItemsEqualityProvider() {
  override fun areEqual(newItem: SeFoundItemInfo, alreadyFoundItem: SeFoundItemInfo): Boolean =
    newItem.item.rawObject == alreadyFoundItem.item.rawObject
}

/**
 * Finds items with equal PSI elements.
 *
 * It replaces [com.intellij.ide.actions.searcheverywhere.PsiElementsEqualityProvider] in the new Search Everywhere.
 */
@ApiStatus.Internal
class SePsiElementsEqualityProvider : SeAbstractItemsEqualityProvider() {
  @Suppress("DEPRECATION")
  override fun areEqual(newItem: SeFoundItemInfo, alreadyFoundItem: SeFoundItemInfo): Boolean {
    val newPsi = PSIPresentationBgRendererWrapper.toPsi(newItem.item.rawObject) ?: return false
    return newPsi == PSIPresentationBgRendererWrapper.toPsi(alreadyFoundItem.item.rawObject)
  }
}

/**
 * Finds items with equal actions.
 *
 * It replaces [com.intellij.ide.actions.searcheverywhere.ActionsEqualityProvider] in the new Search Everywhere.
 */
@ApiStatus.Internal
class SeActionsEqualityProvider : SeAbstractItemsEqualityProvider() {
  override fun areEqual(newItem: SeFoundItemInfo, alreadyFoundItem: SeFoundItemInfo): Boolean {
    val newAction = extractAction(newItem.item.rawObject) ?: return false
    return newAction == extractAction(alreadyFoundItem.item.rawObject)
  }

  private fun extractAction(item: Any): AnAction? {
    if (item is AnAction) return item

    val value = (item as? GotoActionModel.MatchedValue)?.value ?: item
    return (value as? GotoActionModel.ActionWrapper)?.action
  }
}

/**
 * Finds items with equal options.
 *
 * It replaces [com.intellij.ide.actions.searcheverywhere.OptionEqualityProvider] in the new Search Everywhere.
 */
@ApiStatus.Internal
class SeOptionsEqualityProvider : SeAbstractItemsEqualityProvider() {
  override fun areEqual(newItem: SeFoundItemInfo, alreadyFoundItem: SeFoundItemInfo): Boolean {
    val newOption = extractOption(newItem.item.rawObject) ?: return false
    return newOption == extractOption(alreadyFoundItem.item.rawObject)
  }

  private fun extractOption(item: Any): OptionDescription? {
    val value = (item as? GotoActionModel.MatchedValue)?.value ?: item
    return value as? OptionDescription
  }
}
