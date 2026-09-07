// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.files

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProviderFactory
import com.intellij.platform.searchEverywhere.providers.target.SeTargetItemsProvider.Companion.COROUTINE_BASED_GOTO_KEY
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeFilesProviderFactory : SeWrappedLegacyContributorItemsProviderFactory {
  override val id: String
    get() = SeProviderIdUtils.FILES_ID

  override suspend fun getItemsProvider(project: Project?,
                                        legacyContributor: SearchEverywhereContributor<Any>,
                                        isAllTab: Boolean,
                                        dataContext: DataContext): SeItemsProvider? {
    if (project == null) return null

    return if (RegistryManager.getInstanceAsync().`is`(COROUTINE_BASED_GOTO_KEY)) {
      SeFilesProvider.create(project, dataContext)
    }
    else getItemsProvider(project, legacyContributor)
  }

  /** The fallback for a caller that has no [DataContext]. It always builds the legacy provider. */
  override suspend fun getItemsProvider(project: Project?, legacyContributor: SearchEverywhereContributor<Any>): SeItemsProvider? {
    if (project == null || legacyContributor !is WeightedSearchEverywhereContributor<Any>) return null
    return SeFilesLegacyBasedProvider(SeAsyncContributorWrapper(legacyContributor))
  }
}
