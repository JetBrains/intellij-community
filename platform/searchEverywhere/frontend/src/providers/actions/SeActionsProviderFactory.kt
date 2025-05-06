// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeActionsProviderFactory : SeItemsProviderFactory {
  override val id: String
    get() = SeActionsAdaptedProvider.ID

  override suspend fun getItemsProvider(project: Project?, dataContext: DataContext): SeItemsProvider {
    return withContext(Dispatchers.EDT) {
      SeActionsAdaptedProvider(ActionSearchEverywhereContributor(project,
                                                                 dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT),
                                                                 dataContext.getData(CommonDataKeys.EDITOR),
                                                                 dataContext))
    }
  }
}