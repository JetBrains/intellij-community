// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.runConfigurations

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProviderFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeRunConfigurationsProviderFactory : SeWrappedLegacyContributorItemsProviderFactory {
  override val id: String
    get() = SeProviderIdUtils.RUN_CONFIGURATIONS_ID

  override suspend fun getItemsProvider(project: Project?, dataContext: DataContext): SeItemsProvider =
    throw UnsupportedOperationException("Shouldn't be called")

  override suspend fun getItemsProvider(legacyContributor: SearchEverywhereContributor<Any>): SeItemsProvider {
    return SeRunConfigurationsProvider(SeAsyncContributorWrapper(legacyContributor))
  }
}