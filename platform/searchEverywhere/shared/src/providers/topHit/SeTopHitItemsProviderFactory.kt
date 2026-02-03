// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.topHit

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProviderFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
abstract class SeTopHitItemsProviderFactory : SeWrappedLegacyContributorItemsProviderFactory {
  protected abstract val isHost: Boolean
  protected abstract val displayName: @Nls String

  override val id: String
    get() = SeTopHitItemsProvider.id(isHost)

  override suspend fun getItemsProvider(project: Project?, legacyContributor: SearchEverywhereContributor<Any>): SeItemsProvider? {
    if (project == null) return null
    return SeTopHitItemsProvider(isHost, project, SeAsyncContributorWrapper(legacyContributor), displayName)
  }
}