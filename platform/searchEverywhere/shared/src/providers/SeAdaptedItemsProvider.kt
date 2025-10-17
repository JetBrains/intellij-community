// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywherePreviewProvider
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereExtendedInfoProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SeAdaptedItem(override val rawObject: Any, private val weight: Int, override val contributor: SearchEverywhereContributor<*>): SeLegacyItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeAdaptedItemEmptyPresentation(contributor.isMultiSelectionSupported)
}

@ApiStatus.Internal
class SeAdaptedItemsProvider(contributor: SearchEverywhereContributor<Any>) : SeItemsProvider {
  override val id: String
    get() = contributorWrapper.contributor.searchProviderId
  override val displayName: @Nls String
    get() = contributorWrapper.contributor.fullGroupName

  private val contributorWrapper = SeAsyncContributorWrapper(contributor)
  private val scopeProviderDelegate = ScopeChooserActionProviderDelegate(contributorWrapper)

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val scopeToApply: String? = SeEverywhereFilter.isEverywhere(params.filter)?.let { isEverywhere ->
      scopeProviderDelegate.searchScopesInfo.getValue()?.let { searchScopesInfo ->
        if (isEverywhere) searchScopesInfo.everywhereScopeId else searchScopesInfo.projectScopeId
      }
    }
    scopeProviderDelegate.applyScope(scopeToApply, false)

    contributorWrapper.fetchElements(params.inputQuery, object : AsyncProcessor<Any> {
      override suspend fun process(item: Any, weight: Int): Boolean {
        return collector.put(SeAdaptedItem(item, weight, contributorWrapper.contributor))
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

  fun isPreviewProvider(): Boolean {
    return contributorWrapper.contributor is SearchEverywherePreviewProvider
  }

  fun isExtendedInfoProvider(): Boolean {
    return contributorWrapper.contributor is SearchEverywhereExtendedInfoProvider
  }

  fun isCommandsSupported(): Boolean {
    return contributorWrapper.contributor.supportedCommands.isNotEmpty()
  }

  fun getSupportedCommands(): List<SeCommandInfo> {
    return contributorWrapper.contributor.supportedCommands.map { commandInfo -> SeCommandInfo(commandInfo, id) }
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }
}