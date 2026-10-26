// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.frontend.SeFrontendOnlyItemsProviderFactory
import com.intellij.platform.searchEverywhere.frontend.SeSeparateTabOnlyItemsProviderFactory
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.DataContextWithRpcId
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
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

    // The action updater runs all updates on EDT when the data context is not async. DataContextWithRpcId is not async.
    val actionDataContext = (dataContext as? DataContextWithRpcId)?.dataContext ?: dataContext
    val contributor = withContext(Dispatchers.EDT) {
      SeRankerActionsContributor(project,
                                 actionDataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT),
                                 actionDataContext.getData(CommonDataKeys.EDITOR),
                                 actionDataContext)
    }

    return SeRankerActionsProvider(SeActionsAdaptedProvider(SeAsyncContributorWrapper(contributor)),
                                   contributor.actionModel,
                                   ranker,
                                   SeActionsCandidatesStrategy.findSelected(),
                                   RegistryManager.getInstanceAsync().intValue(DEBOUNCE_REGISTRY_KEY).coerceAtLeast(0).milliseconds)
  }
}

/**
 * The actions contributor that gives [SeRankerActionsProvider] access to its action model.
 */
private class SeRankerActionsContributor(
  project: Project?,
  contextComponent: Component?,
  editor: Editor?,
  dataContext: DataContext,
) : ActionSearchEverywhereContributor(project, contextComponent, editor, dataContext) {
  val actionModel: GotoActionModel get() = model
}
