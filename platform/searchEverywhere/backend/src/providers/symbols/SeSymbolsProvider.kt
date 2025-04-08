// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.symbols

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.target.SeTargetsProviderDelegate
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeSymbolsProvider(val project: Project, private val contributorWrapper: SeAsyncContributorWrapper<Any>): SeItemsProvider, SeSearchScopesProvider {
  override val id: String get() = ID
  private val targetsProviderDelegate = SeTargetsProviderDelegate(contributorWrapper)

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    targetsProviderDelegate.collectItems(params, collector)
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    return targetsProviderDelegate.itemSelected(item, modifiers, searchText)
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }

  override suspend fun getSearchScopesInfo(): SeSearchScopesInfo? = targetsProviderDelegate.getSearchScopesInfo()

  companion object {
    const val ID: String = "com.intellij.SymbolSearchEverywhereItemProvider"
  }
}