// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.equalityProviders

import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.withSafeCatch
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeLegacyItem
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.withUuidToReplace
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.locks.ReentrantLock
import javax.swing.ListCellRenderer
import kotlin.concurrent.withLock

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
    val itemObject = item.rawObject
    val contributor = (item as? SeLegacyItem)?.contributor ?: dummyContributor

    return readAction {
      lock.withLock {
        val newItemInfo = SearchEverywhereFoundElementInfo(newItemData.uuid, itemObject, newItemData.weight, contributor)
        val action = {
          equalityProvider.compareItemsCollection(newItemInfo, alreadyFoundItems.values)
        }.withSafeCatch {
          SeLog.error(it)
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

  companion object {
    private val dummyContributor = DummySearchEverywhereContributor<Any>()
  }
}

private class DummySearchEverywhereContributor<Any>: SearchEverywhereContributor<Any> {
  override fun getSearchProviderId(): String = "Dummy"
  override fun getGroupName(): @Nls String =
    @Suppress("HardCodedStringLiteral")
    "Dummy"

  override fun getSortWeight(): Int = 0
  override fun showInFindResults(): Boolean = false

  override fun fetchElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: Processor<in Any>,
  ) { }

  override fun processSelectedItem(selected: Any & kotlin.Any, modifiers: Int, searchText: String): Boolean = true

  @Suppress("HardCodedStringLiteral")
  override fun getElementsRenderer(): ListCellRenderer<in Any> = SimpleListCellRenderer.create("Dummy") { "Dummy" }
}