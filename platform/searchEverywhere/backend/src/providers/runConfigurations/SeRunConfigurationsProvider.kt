// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.runConfigurations

import com.intellij.ide.IdeBundle
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SeRunConfigurationsProvider(private val contributorWrapper: SeAsyncContributorWrapper<Any>) : SeItemsProvider {
  override val id: String get() = "RunConfigurationsSEContributor"
  override val displayName: @Nls String get() = IdeBundle.message("searcheverywhere.run.configs.tab.name")

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    return
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    return true
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return false
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }
}