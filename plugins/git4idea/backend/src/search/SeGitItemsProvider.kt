// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.search

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.util.gotoByName.LanguageRef
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeLegacyItem
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeSearchScopesProvider
import com.intellij.platform.searchEverywhere.SeTypeVisibilityStateProvider
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.providers.AsyncProcessor
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeTypeVisibilityStateProviderDelegate
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProvider
import com.intellij.platform.searchEverywhere.providers.getExtendedInfo
import com.intellij.platform.searchEverywhere.providers.target.SeTargetsFilter
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import com.intellij.vcs.git.SeGitProviderIdUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SeGitItem(
  override val rawObject: Any,
  override val contributor: SearchEverywhereContributor<*>,
  private val weight: Int,
  val project: Project,
  val extendedInfo: SeExtendedInfo?,
  val isMultiSelectionSupported: Boolean
) : SeLegacyItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeGitPresentationProvider.getPresentation(rawObject, extendedInfo, project, isMultiSelectionSupported)
}

@ApiStatus.Internal
class SeGitItemsProvider(private val contributorWrapper: SeAsyncContributorWrapper<Any>, private val project: Project) : SeWrappedLegacyContributorItemsProvider(), SeSearchScopesProvider, SeTypeVisibilityStateProvider {
  override val id: String get() = SeGitProviderIdUtils.GIT_OBJECTS_ID
  override val displayName: @Nls String get() = contributor.groupName
  override val contributor: SearchEverywhereContributor<Any> get() = contributorWrapper.contributor

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val filter = SeTargetsFilter.from(params.filter)
    SeTypeVisibilityStateProviderDelegate.applyTypeVisibilityStates<LanguageRef>(contributor, filter.hiddenTypes)

    contributorWrapper.fetchElements(params.inputQuery, object : AsyncProcessor<Any> {
      override suspend fun process(item: Any, weight: Int): Boolean {
        return collector.put(SeGitItem(item, contributor, weight, project, contributor.getExtendedInfo(item), contributorWrapper.contributor.isMultiSelectionSupported))
      }
    })
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeGitItem)?.rawObject ?: return false

    return withContext(Dispatchers.EDT) {
      contributor.processSelectedItem(legacyItem, modifiers, searchText)
    }
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return contributor.showInFindResults()
  }

  override suspend fun getSearchScopesInfo(): SearchScopesInfo? = null

  override suspend fun getTypeVisibilityStates(index: Int): List<SeTypeVisibilityStatePresentation> {
    return SeTypeVisibilityStateProviderDelegate.getStates<LanguageRef>(contributor, index)
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }
}