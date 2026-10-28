// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.equalityProviders

import com.intellij.ide.actions.searcheverywhere.ActionsEqualityProvider
import com.intellij.ide.actions.searcheverywhere.OptionEqualityProvider
import com.intellij.ide.actions.searcheverywhere.PsiElementsEqualityProvider
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.TrivialElementsEqualityProvider
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.withSafeCatch
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeLegacyItem
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.withUuidToReplace
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.locks.ReentrantLock
import javax.swing.ListCellRenderer
import kotlin.concurrent.withLock

/**
 * Provides a way to check if the element should be added to the list of already found elements.
 * Don't forget to make SeItem implementation conforming SeLegacyItem interface.
 *
 * The checker asks the [SeItemsEqualityProvider]s first.
 * When all of them return [SeEqualItemsAction.DoNothing], the checker asks the legacy [SEResultsEqualityProvider]s.
 * The first answer other than [SeEqualItemsAction.DoNothing] wins.
 * The checker does not ask a legacy provider when its replacement is registered. See [legacyProvidersToAsk].
 */
@ApiStatus.Internal
@Suppress("DEPRECATION")
class SeEqualityChecker {
  private val equalityProviders: List<SeItemsEqualityProvider> = SeItemsEqualityProvider.EP_NAME.extensionList
  private val legacyEqualityProvider: SEResultsEqualityProvider =
    SEResultsEqualityProvider.composite(legacyProvidersToAsk(SEResultsEqualityProvider.EP_NAME.extensionList, equalityProviders))
  private val alreadyFoundLegacyItems = mutableMapOf<String, SearchEverywhereFoundElementInfo>()
  private val alreadyFoundItems = mutableMapOf<String, SeFoundItemInfo>()
  private val lock = ReentrantLock()

  suspend fun checkAndUpdateIfNeeded(newItemData: SeItemData): SeItemData? {
    val item = newItemData.fetchItemIfExists() ?: return newItemData
    val itemObject = item.rawObject
    val contributor = (item as? SeLegacyItem)?.contributor ?: dummyContributor(newItemData.providerId.value)

    return readAction {
      lock.withLock {
        val newLegacyItemInfo = SearchEverywhereFoundElementInfo(newItemData.uuid, itemObject, newItemData.weight, contributor)
        val providerPriority =
          if (item is SeLegacyItem) sortWeightToPriority(item.contributor.sortWeight)
          else platformProviderPriority(newItemData.providerId.value)
        val newItemInfo = SeFoundItemInfo(newItemData.uuid, item, newItemData.providerId, newItemData.weight, providerPriority)
        val action = compareWithProviders(newItemInfo) ?: compareWithLegacyProviders(newLegacyItemInfo) ?: SeEqualItemsAction.DoNothing

        when (action) {
          is SeEqualItemsAction.Replace -> {
            val toRemove = action.toBeReplaced.map { it.uuid }
            toRemove.forEach {
              alreadyFoundLegacyItems.remove(it)
              alreadyFoundItems.remove(it)
            }
            addToAlreadyFound(newLegacyItemInfo, newItemInfo)

            newItemData.withUuidToReplace(toRemove).logReplaceAndReturn()
          }
          SeEqualItemsAction.Skip -> {
            newItemData.logSkipAndReturnNull()
          }
          SeEqualItemsAction.DoNothing -> {
            addToAlreadyFound(newLegacyItemInfo, newItemInfo)
            newItemData.logAddAndReturn()
          }
        }
      }
    }
  }

  /** Returns `null` when the legacy providers have nothing to do with [newItemInfo]. */
  private fun compareWithLegacyProviders(newItemInfo: SearchEverywhereFoundElementInfo): SeEqualItemsAction? {
    val action = {
      legacyEqualityProvider.compareItemsCollection(newItemInfo, alreadyFoundLegacyItems.values)
    }.withSafeCatch {
      SeLog.error(it)
      SEResultsEqualityProvider.SEEqualElementsActionType.DoNothing
    }

    return when (action) {
      is SEResultsEqualityProvider.SEEqualElementsActionType.Replace -> {
        SeEqualItemsAction.Replace(action.toBeReplaced.mapNotNull { legacyItemInfo -> legacyItemInfo.uuid?.let { alreadyFoundItems[it] } })
      }
      is SEResultsEqualityProvider.SEEqualElementsActionType.Skip -> SeEqualItemsAction.Skip
      is SEResultsEqualityProvider.SEEqualElementsActionType.DoNothing, null -> null
    }
  }

  /** Returns `null` when the providers have nothing to do with [newItemInfo]. */
  private fun compareWithProviders(newItemInfo: SeFoundItemInfo): SeEqualItemsAction? {
    for (provider in equalityProviders) {
      val action = {
        provider.compareItems(newItemInfo, alreadyFoundItems.values)
      }.withSafeCatch {
        SeLog.error(it)
        SeEqualItemsAction.DoNothing
      }

      when (action) {
        is SeEqualItemsAction.Replace, SeEqualItemsAction.Skip -> return action
        SeEqualItemsAction.DoNothing, null -> {}
      }
    }
    return null
  }

  private fun addToAlreadyFound(legacyItemInfo: SearchEverywhereFoundElementInfo, itemInfo: SeFoundItemInfo) {
    alreadyFoundLegacyItems[itemInfo.uuid] = legacyItemInfo
    alreadyFoundItems[itemInfo.uuid] = itemInfo
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
    private fun dummyContributor(providerId: String) = DummySearchEverywhereContributor<Any>(providerId)

    /**
     * Returns the [legacyProviders] that have no registered replacement among the [providers].
     * The old Search Everywhere still asks all legacy providers.
     */
    @ApiStatus.Internal
    @VisibleForTesting
    fun legacyProvidersToAsk(
      legacyProviders: List<SEResultsEqualityProvider>,
      providers: List<SeItemsEqualityProvider>,
    ): List<SEResultsEqualityProvider> {
      val providerClasses: Set<Class<*>> = providers.mapTo(HashSet()) { it.javaClass }
      return legacyProviders.filter { legacyProvider ->
        val replacement = legacyProviderReplacements[legacyProvider.javaClass]
        replacement == null || replacement !in providerClasses
      }
    }
  }
}

/** The legacy platform providers and their replacements. */
@Suppress("DEPRECATION")
private val legacyProviderReplacements: Map<Class<out SEResultsEqualityProvider>, Class<out SeItemsEqualityProvider>> = mapOf(
  TrivialElementsEqualityProvider::class.java to SeTrivialItemsEqualityProvider::class.java,
  PsiElementsEqualityProvider::class.java to SePsiElementsEqualityProvider::class.java,
  ActionsEqualityProvider::class.java to SeActionsEqualityProvider::class.java,
  OptionEqualityProvider::class.java to SeOptionsEqualityProvider::class.java,
)

private class DummySearchEverywhereContributor<Any>(private val providerId: String): SearchEverywhereContributor<Any> {
  override fun getSearchProviderId(): String = providerId
  override fun getGroupName(): @Nls String =
    @Suppress("HardCodedStringLiteral")
    "Dummy"

  override fun getSortWeight(): Int = priorityToSortWeight(platformProviderPriority(providerId))
  override fun showInFindResults(): Boolean = false

  override fun fetchElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: Processor<in Any>,
  ) { }

  override fun processSelectedItem(selected: Any & kotlin.Any, modifiers: Int, searchText: String): Boolean = true

  @Suppress("HardCodedStringLiteral")
  override fun getElementsRenderer(): ListCellRenderer<in Any> = textListCellRenderer("Dummy") { "Dummy" }
}

/**
 * The priorities of the platform providers. A provider with a higher priority is more important.
 * Each priority comes from the sort weight of the legacy contributor with the same id. See [sortWeightToPriority].
 */
private val platformProviderPriorities: Map<String, Int> = mapOf(
  SeProviderIdUtils.TOP_HIT_HOST_ID to 9950,
  SeProviderIdUtils.TOP_HIT_ID to 9950,
  SeProviderIdUtils.RECENT_FILES_ID to 9930,
  SeProviderIdUtils.CLASSES_ID to 9900,
  SeProviderIdUtils.FILES_ID to 9800,
  SeProviderIdUtils.SYMBOLS_ID to 9700,
  SeProviderIdUtils.RUN_CONFIGURATIONS_ID to 9650,
  SeProviderIdUtils.ACTIONS_ID to 9600,
  SeProviderIdUtils.NON_INDEXABLE_FILES_ID to 9000,
  SeProviderIdUtils.TEXT_ID to 8500,
)

private const val DEFAULT_PROVIDER_PRIORITY = 0

/** A legacy contributor with this sort weight gets [DEFAULT_PROVIDER_PRIORITY]. */
private const val DEFAULT_SORT_WEIGHT = 10000

private fun platformProviderPriority(providerId: String): Int = platformProviderPriorities[providerId] ?: DEFAULT_PROVIDER_PRIORITY

/**
 * Converts the sort weight of a legacy contributor to a provider priority.
 * A lower sort weight gives a higher priority, so the order of the providers stays the same.
 */
private fun sortWeightToPriority(sortWeight: Int): Int =
  (DEFAULT_SORT_WEIGHT.toLong() - sortWeight).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

/** Converts a provider priority back to a sort weight. It is the reverse of [sortWeightToPriority]. */
private fun priorityToSortWeight(priority: Int): Int = DEFAULT_SORT_WEIGHT - priority