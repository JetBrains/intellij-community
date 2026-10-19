// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.frontend.SeFrontendOnlyItemsProviderFactory
import com.intellij.platform.searchEverywhere.frontend.SeSeparateTabOnlyItemsProviderFactory
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Creates [SeRankerActionsProvider] when the `search.everywhere.actions.ranker` registry key selects a [SeActionsRanker].
 */
@Internal
class SeRankerActionsProviderFactory : SeItemsProviderFactory, SeFrontendOnlyItemsProviderFactory, SeSeparateTabOnlyItemsProviderFactory {
  override val id: String get() = SeProviderIdUtils.RANKER_ACTIONS_ID

  override suspend fun getItemsProvider(project: Project?, dataContext: DataContext): SeItemsProvider? {
    val ranker = SeActionsRanker.findSelected() ?: return null

    val contributor = withContext(Dispatchers.EDT) {
      ActionSearchEverywhereContributor(project,
                                        dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT),
                                        dataContext.getData(CommonDataKeys.EDITOR),
                                        dataContext)
    }

    return SeRankerActionsProvider(SeActionsAdaptedProvider(SeAsyncContributorWrapper(contributor)), ranker)
  }
}
