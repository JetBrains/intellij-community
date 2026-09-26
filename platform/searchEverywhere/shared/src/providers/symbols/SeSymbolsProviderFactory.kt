// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.symbols

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProviderFactory
import com.intellij.platform.searchEverywhere.providers.target.SeTargetItemsProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeSymbolsProviderFactory : SeWrappedLegacyContributorItemsProviderFactory {
  override val id: String
    get() = SeProviderIdUtils.SYMBOLS_ID

  override suspend fun getItemsProvider(project: Project?,
                                        legacyContributor: SearchEverywhereContributor<Any>,
                                        isAllTab: Boolean,
                                        dataContext: DataContext): SeItemsProvider? {
    if (project == null) return null
    if (!SeTargetItemsProvider.isCoroutineBasedGotoEnabled(legacyContributor, id)) {
      return getItemsProvider(project, legacyContributor)
    }

    return SeSymbolsProvider.create(project, dataContext)
  }

  /** The fallback for a caller that has no [DataContext]. It always builds the legacy provider. */
  override suspend fun getItemsProvider(project: Project?, legacyContributor: SearchEverywhereContributor<Any>): SeItemsProvider? {
    if (project == null || legacyContributor !is WeightedSearchEverywhereContributor<Any>) return null
    return SeSymbolsLegacyBasedProvider(SeAsyncContributorWrapper(legacyContributor))
  }
}
