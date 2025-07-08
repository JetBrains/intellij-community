// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.equalityProviders

import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeLegacyItem
import com.intellij.platform.searchEverywhere.providers.SeLog
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Provides a way to check if the element should be added to the list of already found elements.
 * Don't forget to make SeItem implementation conforming SeLegacyItem interface.
 */
@ApiStatus.Internal
class SeEqualityChecker {
  private val equalityProvider: SEResultsEqualityProvider = SEResultsEqualityProvider.composite(SEResultsEqualityProvider.EP_NAME.extensionList)
  private val alreadyFoundItems = mutableMapOf<String, SearchEverywhereFoundElementInfo>()
  private val lock = ReentrantLock()

  suspend fun checkAndUpdateIfNeeded(newItemData: SeItemData): SeItemData? {
    val item = newItemData.fetchItemIfExists() ?: return newItemData
    val itemObject = (item as? SeLegacyItem)?.rawObject ?: return newItemData

    return readAction {
      lock.withLock {
        val newItemInfo = SearchEverywhereFoundElementInfo(newItemData.uuid, itemObject, newItemData.weight, item.contributor)
        val action = try {
          equalityProvider.compareItemsCollection(newItemInfo, alreadyFoundItems.values)
        }
        catch (e: Exception) {
          if (e is ControlFlowException || e is CancellationException) throw e

          SeLog.error(e)
          SEResultsEqualityProvider.SEEqualElementsActionType.DoNothing
        }

        when (action) {
          is SEResultsEqualityProvider.SEEqualElementsActionType.Replace -> {
            val toRemove = action.toBeReplaced.mapNotNull { it.uuid }
            toRemove.forEach { alreadyFoundItems.remove(it) }
            alreadyFoundItems[newItemData.uuid] = newItemInfo

            newItemData.withUuidToReplace(toRemove).logReplaceAndReturn()
          }
          is SEResultsEqualityProvider.SEEqualElementsActionType.Skip -> {
            newItemData.logSkipAndReturnNull()
          }
          else -> {
            alreadyFoundItems[newItemData.uuid] = newItemInfo
            newItemData.logAddAndReturn()
          }
        }
      }
    }
  }

  private fun SeItemData.logSkipAndReturnNull(): SeItemData? {
    SeLog.log(SeLog.EQUALITY) {
      "Equality result SKIP: for ${providerId}: ${uuid}"
    }
    return null
  }

  private fun SeItemData.logAddAndReturn(): SeItemData {
    SeLog.log(SeLog.EQUALITY) {
      "Equality result ADD: for ${providerId}: ${uuid}"
    }
    return this
  }

  private fun SeItemData.logReplaceAndReturn(): SeItemData {
    SeLog.log(SeLog.EQUALITY) {
      "Equality result REPLACE(itemsIds=$uuidsToReplace): for ${providerId}: ${uuid}"
    }
    return this
  }
}