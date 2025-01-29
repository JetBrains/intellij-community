// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.files

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributorFactory
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereAsyncContributor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.api.SeItemsProvider
import com.intellij.platform.searchEverywhere.api.SeItemsProviderFactory
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeFilesProviderFactory : SeItemsProviderFactory {
  override fun getItemsProvider(project: Project): SeItemsProvider {
    val legacyContributor = runBlockingCancellable {
      readAction {
        FileSearchEverywhereContributorFactory().createContributor(createActionEvent(project)) as SearchEverywhereAsyncContributor<Any?>
      }
    }

    return SeFilesProvider(project, legacyContributor)
  }

  private fun createActionEvent(project: Project) = AnActionEvent.createFromDataContext("", null) {
    if (CommonDataKeys.PROJECT.`is`(it)) project
    else null
  }
}