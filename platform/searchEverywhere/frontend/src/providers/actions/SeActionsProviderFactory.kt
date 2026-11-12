// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.frontend.SeFrontendOnlyItemsProviderFactory
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.DataContextWithRpcId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Creates [SeActionsProvider]. The All tab and the separate tab use the same provider.
 */
@Internal
class SeActionsProviderFactory : SeItemsProviderFactory, SeFrontendOnlyItemsProviderFactory {
  override val id: String
    get() = SeProviderIdUtils.ACTIONS_ID

  override suspend fun getItemsProvider(project: Project?, dataContext: DataContext): SeItemsProvider {
    return createActionsProvider(project, dataContext, withDataContext = false)
  }
}

/**
 * Creates the provider with the context component and the editor of [dataContext] on the EDT.
 *
 * When [withDataContext] is `false`, the provider takes the data context of the context component, as the legacy contributor does.
 * When it is `true`, the provider uses [dataContext] itself.
 */
internal suspend fun createActionsProvider(
  project: Project?,
  dataContext: DataContext,
  withDataContext: Boolean,
): SeActionsProvider {
  // The action updater runs all updates on EDT when the data context is not async. DataContextWithRpcId is not async.
  val actionDataContext = (dataContext as? DataContextWithRpcId)?.dataContext ?: dataContext
  return withContext(Dispatchers.EDT) {
    SeActionsProvider(project,
                      actionDataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT),
                      actionDataContext.getData(CommonDataKeys.EDITOR),
                      actionDataContext.takeIf { withDataContext })
  }
}
