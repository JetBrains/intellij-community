// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.recentFiles

import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.backend.providers.target.SeTargetsProviderDelegate
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

@Internal
class SeRecentFilesProvider(private val contributorWrapper: SeAsyncContributorWrapper<Any>) : SeItemsProvider {
  override val id: String get() = SeProviderIdUtils.RECENT_FILES_ID
  override val displayName: @Nls String
    get() = contributorWrapper.contributor.fullGroupName

  private val targetsProviderDelegate = SeTargetsProviderDelegate(contributorWrapper)

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

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }
}