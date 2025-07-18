// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.runConfigurations

import com.intellij.execution.actions.ChooseRunConfigurationPopup
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemPresentation
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.getExtendedDescription
import com.intellij.platform.searchEverywhere.providers.runConfigurations.SeRunConfigurationsPresentationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import kotlin.Any

@ApiStatus.Internal
class SeRunConfigurationsItem(val item: ChooseRunConfigurationPopup.ItemWrapper<*>, private val weight: Int, val extendedDescription: String?, val isMultiSelectionSupported: Boolean) : SeItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeRunConfigurationsPresentationProvider.getPresentation(item, extendedDescription, isMultiSelectionSupported)
}

@ApiStatus.Internal
class SeRunConfigurationsProvider(private val contributorWrapper: SeAsyncContributorWrapper<Any>) : SeItemsProvider {
  override val id: String get() = SeProviderIdUtils.RUN_CONFIGURATIONS_ID
  override val displayName: @Nls String get() = contributorWrapper.contributor.fullGroupName

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    coroutineToIndicator {
      val indicator = DelegatingProgressIndicator(ProgressManager.getGlobalProgressIndicator())
      contributorWrapper.contributor.fetchElements(params.inputQuery, indicator) { item ->
        (item as? ChooseRunConfigurationPopup.ItemWrapper<*>)?.let {
          val weight = contributorWrapper.contributor.getElementPriority(item, params.inputQuery)
          runBlockingCancellable { collector.put(SeRunConfigurationsItem(it, weight, getExtendedDescription(it), contributorWrapper.contributor.isMultiSelectionSupported)) }
        } ?: true
      }
    }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeRunConfigurationsItem)?.item ?: return false
    return withContext(Dispatchers.EDT) {
      contributorWrapper.contributor.processSelectedItem(legacyItem, modifiers, searchText)
    }
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return contributorWrapper.contributor.showInFindResults()
  }

  fun getExtendedDescription(item: Any): String? {
    return contributorWrapper.contributor.getExtendedDescription(item)
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }
}