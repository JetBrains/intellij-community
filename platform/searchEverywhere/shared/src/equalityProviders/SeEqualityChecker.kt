// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.equalityProviders

import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


@ApiStatus.Internal
class SeEqualityChecker {
  sealed interface Action
  object Add: Action
  class Replace(val itemsIds: List<String>): Action
  object Skip: Action

  private val equalityProvider: SEResultsEqualityProvider = SEResultsEqualityProvider.composite(SEResultsEqualityProvider.EP_NAME.extensionList)
  private val alreadyFoundItems = mutableMapOf<String, SearchEverywhereFoundElementInfo>()
  private val lock = ReentrantLock()

  fun getAction(itemObject: Any, newItemUuid: String, priority: Int, contributor: SearchEverywhereContributor<*>): Action {
    lock.withLock {
      val newItemInfo = SearchEverywhereFoundElementInfo(newItemUuid, itemObject, priority, contributor)
      val result = equalityProvider.compareItems(newItemInfo, alreadyFoundItems.values.toList())

      if (result is SEResultsEqualityProvider.SEEqualElementsActionType.Replace) {
        val toRemove = result.toBeReplaced.mapNotNull { it.uuid }
        toRemove.forEach { alreadyFoundItems.remove(it) }

        return Replace(toRemove)
      }
      else if (result is SEResultsEqualityProvider.SEEqualElementsActionType.Skip) {
        return Skip
      }
      else {
        alreadyFoundItems[newItemUuid] = newItemInfo
        return Add
      }
    }
  }
}