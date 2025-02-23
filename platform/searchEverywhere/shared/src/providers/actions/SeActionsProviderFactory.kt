// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.actions

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.api.SeItemsProvider
import com.intellij.platform.searchEverywhere.api.SeItemsProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeActionsProviderFactory: SeItemsProviderFactory {
  override val id: String
    get() = SeActionsAdaptedProvider.ID

  override fun getItemsProvider(project: Project, dataContext: DataContext): SeItemsProvider {
    // TODO: This is unacceptable here, rewrite ActionsContributor
    return runBlockingCancellable {
      withContext(Dispatchers.EDT) {
        // TODO: Provide proper context
        SeActionsAdaptedProvider(project, ActionSearchEverywhereContributor(project,
                                                                            dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT),
                                                                            dataContext.getData(CommonDataKeys.EDITOR),
                                                                            dataContext))
      }
    }
  }
}