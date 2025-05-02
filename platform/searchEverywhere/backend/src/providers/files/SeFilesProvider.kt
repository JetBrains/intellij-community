// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.files

import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.backend.providers.target.SeTargetsProviderDelegate
import com.intellij.platform.searchEverywhere.providers.SeAsyncWeightedContributorWrapper
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

@Internal
class SeFilesProvider(val project: Project, private val contributorWrapper: SeAsyncWeightedContributorWrapper<Any>): SeItemsProvider,
                                                                                                                     SeSearchScopesProvider,
                                                                                                                     SeTypeVisibilityStateProvider {
  override val id: String get() = ID
  override val displayName: @Nls String
    get() = contributorWrapper.contributor.fullGroupName

  private val targetsProviderDelegate = SeTargetsProviderDelegate(contributorWrapper)

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    targetsProviderDelegate.collectItems<FileTypeRef>(params, collector)
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    return targetsProviderDelegate.itemSelected(item, modifiers, searchText)
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }

  override suspend fun getSearchScopesInfo(): SeSearchScopesInfo? = targetsProviderDelegate.getSearchScopesInfo()

  override suspend fun getTypeVisibilityStates(): List<SeTypeVisibilityStatePresentation> =
    targetsProviderDelegate.getTypeVisibilityStates<FileTypeRef>()

  companion object {
    const val ID: String = "com.intellij.FileSearchEverywhereItemProvider"
  }
}