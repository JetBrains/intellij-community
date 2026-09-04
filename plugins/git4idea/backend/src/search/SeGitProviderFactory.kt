// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.search

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProviderFactory
import com.intellij.vcs.git.SeGitProviderIdUtils
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeGitProviderFactory : SeWrappedLegacyContributorItemsProviderFactory {
  override val id: String
    get() = SeGitProviderIdUtils.GIT_OBJECTS_ID

  override suspend fun getItemsProvider(project: Project?, legacyContributor: SearchEverywhereContributor<Any>): SeItemsProvider? {
    if (project == null || legacyContributor !is WeightedSearchEverywhereContributor<Any>) return null
    return SeGitItemsProvider(SeAsyncContributorWrapper(legacyContributor), project)
  }
}