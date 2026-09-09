// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.files

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorWrapper
import com.intellij.ide.actions.searcheverywhere.SemanticSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProviderFactory
import com.intellij.platform.searchEverywhere.providers.target.SeTargetItemsProvider.Companion.COROUTINE_BASED_GOTO_KEY
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeFilesProviderFactory : SeWrappedLegacyContributorItemsProviderFactory {
  override val id: String
    get() = SeProviderIdUtils.FILES_ID

  override suspend fun getItemsProvider(project: Project?,
                                        legacyContributor: SearchEverywhereContributor<Any>,
                                        isAllTab: Boolean,
                                        dataContext: DataContext): SeItemsProvider? {
    if (project == null) return null

    if (!RegistryManager.getInstanceAsync().`is`(COROUTINE_BASED_GOTO_KEY)) {
      return getItemsProvider(project, legacyContributor)
    }

    if (legacyContributor.isSemantic() && !ApplicationManager.getApplication().isInternal) {
      SeLog.log(SeLog.LIFE_CYCLE) { "SeFilesProviderFactory: the semantic contributor is on or the IDE is not IDEA, keeping the legacy provider" }
      return getItemsProvider(project, legacyContributor)
    }

    return SeFilesProvider.create(project, dataContext)
  }

  /**
   * True when the semantic file search replaced the plain contributor.
   *
   * `SearchEverywhereMlContributorReplacementImpl` swaps in `SemanticFileSearchEverywhereContributor`
   * only when the setting of the tab is on. So a false answer covers both a semantic search that is off
   * and a plugin that is absent. The check runs against the effective contributor, because
   * `PSIPresentationBgRendererWrapper` wraps it.
   */
  private fun SearchEverywhereContributor<Any>.isSemantic(): Boolean {
    val effectiveContributor = (this as? SearchEverywhereContributorWrapper)?.getEffectiveContributor() ?: this
    return effectiveContributor is SemanticSearchEverywhereContributor
  }

  /** The fallback for a caller that has no [DataContext]. It always builds the legacy provider. */
  override suspend fun getItemsProvider(project: Project?, legacyContributor: SearchEverywhereContributor<Any>): SeItemsProvider? {
    if (project == null || legacyContributor !is WeightedSearchEverywhereContributor<Any>) return null
    return SeFilesLegacyBasedProvider(SeAsyncContributorWrapper(legacyContributor))
  }
}
