// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.filesFuzzy

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import org.jetbrains.annotations.ApiStatus

/**
 * Factory for creating FuzzyFileSearch providers.
 *
 * The provider is only created if:
 * 1. The registry flag "fuzzySearch.enabled" is set to true
 * 2. A valid project context is available
 *
 * This allows the feature to be enabled/disabled at runtime for testing and gradual rollout.
 */
@ApiStatus.Internal
class SeFuzzyFileSearchProviderFactory : SeItemsProviderFactory {
  override val id: String = "FuzzyFileSearch"

  override suspend fun getItemsProvider(
    project: Project?,
    dataContext: DataContext
  ): SeItemsProvider? {
    if (!Registry.`is`("search.everywhere.fuzzy.files.enabled", false)) {
      return null
    }

    if (project == null) {
      return null
    }

    return SeFuzzyFileSearchProvider(project)
  }
}
