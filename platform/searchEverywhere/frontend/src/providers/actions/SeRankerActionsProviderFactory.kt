// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.frontend.SeFrontendOnlyItemsProviderFactory
import com.intellij.platform.searchEverywhere.frontend.SeSeparateTabOnlyItemsProviderFactory
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.time.Duration.Companion.milliseconds

private const val DEBOUNCE_REGISTRY_KEY: String = "search.everywhere.actions.ranker.debounce.ms"

/**
 * Creates [SeRankerActionsProvider] when the `search.everywhere.actions.ranker` registry key selects a [SeActionsRanker].
 */
@Internal
class SeRankerActionsProviderFactory : SeItemsProviderFactory, SeFrontendOnlyItemsProviderFactory, SeSeparateTabOnlyItemsProviderFactory {
  override val id: String get() = SeProviderIdUtils.RANKER_ACTIONS_ID

  override suspend fun getItemsProvider(project: Project?, dataContext: DataContext): SeItemsProvider? {
    val ranker = SeActionsRanker.findSelected() ?: return null

    val actionsProvider = createActionsProvider(project, dataContext, withDataContext = true)

    return SeRankerActionsProvider(actionsProvider,
                                   ranker,
                                   SeActionsCandidatesStrategy.findSelected(),
                                   RegistryManager.getInstanceAsync().intValue(DEBOUNCE_REGISTRY_KEY).coerceAtLeast(0).milliseconds)
  }
}

