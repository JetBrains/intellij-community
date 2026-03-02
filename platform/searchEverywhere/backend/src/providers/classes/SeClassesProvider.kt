// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.classes

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.util.gotoByName.LanguageRef
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.SeExtendedInfoProvider
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsPreviewProvider
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeSearchScopesProvider
import com.intellij.platform.searchEverywhere.SeTypeVisibilityStateProvider
import com.intellij.platform.searchEverywhere.backend.providers.target.SeTargetsProviderDelegate
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProvider
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SeClassesProvider(private val contributorWrapper: SeAsyncContributorWrapper<Any>) : SeWrappedLegacyContributorItemsProvider(),
                                                                                          SeSearchScopesProvider,
                                                                                          SeTypeVisibilityStateProvider,
                                                                                          SeItemsPreviewProvider,
                                                                                          SeExtendedInfoProvider {
  override val id: String get() = SeProviderIdUtils.CLASSES_ID
  override val displayName: @Nls String
    get() = contributorWrapper.contributor.fullGroupName
  override val contributor: SearchEverywhereContributor<*> get() = contributorWrapper.contributor

  private val targetsProviderDelegate = SeTargetsProviderDelegate(contributorWrapper, this)

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    targetsProviderDelegate.collectItems<LanguageRef>(params, collector)
  }

  override suspend fun collectItemsWithOperationLifetime(
    params: SeParams,
    operationDisposable: Disposable,
    collector: SeItemsProvider.Collector
  ) {
    targetsProviderDelegate.collectItems<LanguageRef>(params, collector, operationDisposable)
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    return targetsProviderDelegate.itemSelected(item, modifiers, searchText)
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return targetsProviderDelegate.canBeShownInFindResults()
  }

  override suspend fun getPreviewInfo(item: SeItem, project: Project): SePreviewInfo? {
    return targetsProviderDelegate.getPreviewInfo(item, project)
  }

  override suspend fun getSearchScopesInfo(): SearchScopesInfo? = targetsProviderDelegate.getSearchScopesInfo()

  override suspend fun getTypeVisibilityStates(index: Int): List<SeTypeVisibilityStatePresentation> =
    targetsProviderDelegate.getTypeVisibilityStates<LanguageRef>(index)

  override suspend fun performExtendedAction(item: SeItem): Boolean {
    return targetsProviderDelegate.performExtendedAction(item)
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }
}