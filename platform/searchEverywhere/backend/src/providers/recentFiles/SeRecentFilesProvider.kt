// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.recentFiles

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeExtendedInfoProvider
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.backend.providers.target.SeTargetsProviderDelegate
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProvider
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

@Internal
class SeRecentFilesProvider(private val contributorWrapper: SeAsyncContributorWrapper<Any>) : SeWrappedLegacyContributorItemsProvider(),
                                                                                              SeItemsPreviewProvider,
                                                                                              SeExtendedInfoProvider {
  override val id: String get() = SeProviderIdUtils.RECENT_FILES_ID
  override val displayName: @Nls String
    get() = contributorWrapper.contributor.fullGroupName
  override val contributor: SearchEverywhereContributor<*> get() = contributorWrapper.contributor

  private val targetsProviderDelegate = SeTargetsProviderDelegate(contributorWrapper, this)

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    targetsProviderDelegate.collectItems<FileTypeRef>(params, collector)
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    return targetsProviderDelegate.itemSelected(item, modifiers, searchText)
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return targetsProviderDelegate.canBeShownInFindResults()
  }

  override suspend fun performExtendedAction(item: SeItem): Boolean {
    return targetsProviderDelegate.performExtendedAction(item)
  }

  override suspend fun getPreviewInfo(item: SeItem, project: Project): SePreviewInfo? {
    return targetsProviderDelegate.getPreviewInfo(item, project)
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }
}