// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.files

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributorFactory
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeFilesProviderFactory : SeItemsProviderFactory {
  override val id: String
    get() = SeFilesProvider.ID

  override suspend fun getItemsProvider(project: Project, dataContext: DataContext): SeItemsProvider {
    val legacyContributor = readAction {
      val actionEvent = AnActionEvent.createEvent(dataContext, null, "", ActionUiKind.NONE, null)
      @Suppress("UNCHECKED_CAST")
      FileSearchEverywhereContributorFactory().createContributor(actionEvent) as WeightedSearchEverywhereContributor<Any>
    }

    return SeFilesProvider(project, SeAsyncContributorWrapper(legacyContributor))
  }
}