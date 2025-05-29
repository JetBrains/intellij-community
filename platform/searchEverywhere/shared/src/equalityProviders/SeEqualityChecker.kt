// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.equalityProviders

import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.platform.searchEverywhere.providers.SeLog
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Provides a way to check if the element should be added to the list of already found elements.
 * Don't forget to make SeItem implementation conforming SeLegacyItem interface.
 */
@ApiStatus.Internal
class SeEqualityChecker {
  sealed interface Action
  object Add: Action {
    override fun toString(): String = "Add"
  }
  class Replace(val itemsIds: List<String>): Action {
    override fun toString(): String {
      return "Replace(itemsIds=$itemsIds)"
    }
  }
  object Skip: Action {
    override fun toString(): String = "Skip"
  }

  private val equalityProvider: SEResultsEqualityProvider = SEResultsEqualityProvider.composite(SEResultsEqualityProvider.EP_NAME.extensionList)
  private val alreadyFoundItems = mutableMapOf<String, SearchEverywhereFoundElementInfo>()
  private val lock = ReentrantLock()

  fun getAction(itemObject: Any, newItemUuid: String, priority: Int, contributor: SearchEverywhereContributor<*>): Action {
    lock.withLock {
      val newItemInfo = SearchEverywhereFoundElementInfo(newItemUuid, itemObject, priority, contributor)
      val action = equalityProvider.compareItems(newItemInfo, alreadyFoundItems.values.toList())

      val result = when (action) {
        is SEResultsEqualityProvider.SEEqualElementsActionType.Replace -> {
          val toRemove = action.toBeReplaced.mapNotNull { it.uuid }
          toRemove.forEach { alreadyFoundItems.remove(it) }

          Replace(toRemove)
        }
        is SEResultsEqualityProvider.SEEqualElementsActionType.Skip -> {
          Skip
        }
        else -> {
          alreadyFoundItems[newItemUuid] = newItemInfo
          Add
        }
      }

      SeLog.log(SeLog.EQUALITY) {
        "Equality result for ${contributor.searchProviderId}: $result for $newItemInfo"
      }

      return result
    }
  }
}