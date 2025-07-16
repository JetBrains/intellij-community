// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.frontend.SeFrontendOnlyItemsProviderFactory
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProviderFactory
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeActionsProviderFactory : SeWrappedLegacyContributorItemsProviderFactory, SeFrontendOnlyItemsProviderFactory {
  override val id: String
    get() = SeProviderIdUtils.ACTIONS_ID

  override suspend fun getItemsProvider(project: Project?, legacyContributor: SearchEverywhereContributor<Any>): SeItemsProvider? {
    if (legacyContributor !is ActionSearchEverywhereContributor) return null
    return SeActionsAdaptedProvider(legacyContributor)
  }
}