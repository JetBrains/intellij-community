// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeCommandInfo
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeLegacyItem
import com.intellij.platform.searchEverywhere.SeLegacyItemPresentationProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.presentations.SeAdaptedItemEmptyPresentation
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@ApiStatus.Internal
class SeAdaptedItem(override val rawObject: Any,
                    private val weight: Int,
                    val presentationProvider: suspend () -> SeItemPresentation?,
                    override val contributor: SearchEverywhereContributor<*>): SeLegacyItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation =
    presentationProvider() ?: SeAdaptedItemEmptyPresentation(contributor.isMultiSelectionSupported)
}

@ApiStatus.Internal
@OptIn(ExperimentalAtomicApi::class)
class SeAdaptedItemsProvider(override val contributor: SearchEverywhereContributor<Any>,
                             private val presentationProvider: SeLegacyItemPresentationProvider?) : SeWrappedLegacyContributorItemsProvider() {
  override val id: String
    get() = contributorWrapper.contributor.searchProviderId
  override val displayName: @Nls String
    get() = contributorWrapper.contributor.fullGroupName
  val hasPresentationProvider: Boolean get() = presentationProvider != null

  private val contributorWrapper = SeAsyncContributorWrapper(contributor)
  private val isInSeparateTab = contributor.isShownInSeparateTab
  private val scopeProviderDelegate = ScopeChooserActionProviderDelegate.createOrNull(contributorWrapper)
  private val lastIsEverywhereFilter = AtomicReference<Boolean?>(null)

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    scopeProviderDelegate?.let { scopeProviderDelegate ->
      val isEverywhere = SeEverywhereFilter.isEverywhere(params.filter)

      if (isEverywhere != null) {
        // For adapted providers which are shown in a separate tab,
        // we should apply isEverywhere filter only if it's changed since the last request from All tab
        if (!isInSeparateTab || (lastIsEverywhereFilter.load()?.let { it != isEverywhere } ?: false)) {
          scopeProviderDelegate.searchScopesInfo.getValue()?.let { searchScopesInfo ->
            if (isEverywhere) searchScopesInfo.everywhereScopeId else searchScopesInfo.projectScopeId
          }?.let {
            scopeProviderDelegate.applyScope(it, false)
          }
        }

        if (isInSeparateTab) {
          lastIsEverywhereFilter.store(isEverywhere)
        }
      }
    }

    contributorWrapper.fetchElements(params.inputQuery, object : AsyncProcessor<Any> {
      override suspend fun process(item: Any, weight: Int): Boolean {
        return collector.put(SeAdaptedItem(item,
                                           weight,
                                           { presentationProvider?.getPresentation(item) },
                                           contributorWrapper.contributor))
      }
    })
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeAdaptedItem)?.rawObject ?: return false
    return withContext(Dispatchers.EDT) {
      contributorWrapper.contributor.processSelectedItem(legacyItem, modifiers, searchText)
    }
  }

  override suspend fun canBeShownInFindResults(): Boolean = contributorWrapper.contributor.showInFindResults()

  fun isCommandsSupported(): Boolean {
    return contributorWrapper.contributor.supportedCommands.isNotEmpty()
  }

  fun getSupportedCommands(): List<SeCommandInfo> = getSupportedCommandsFromContributor()

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }
}