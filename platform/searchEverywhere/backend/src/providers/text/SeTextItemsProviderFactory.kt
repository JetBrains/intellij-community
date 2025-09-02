// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.text

import com.intellij.find.impl.TextSearchContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.providers.SeAsyncWeightedContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProviderFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTextItemsProviderFactory : SeWrappedLegacyContributorItemsProviderFactory {
  override val id: String get() = SeProviderIdUtils.TEXT_ID

  override suspend fun getItemsProvider(project: Project?, legacyContributor: SearchEverywhereContributor<Any>): SeItemsProvider? {
    if (project == null || !TextSearchContributor.enabled() || legacyContributor !is WeightedSearchEverywhereContributor<Any>) return null
    return SeTextItemsProvider(project, SeAsyncWeightedContributorWrapper(legacyContributor))
  }
}