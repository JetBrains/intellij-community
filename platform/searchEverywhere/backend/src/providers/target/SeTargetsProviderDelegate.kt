// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.target

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.ItemWithPresentation
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.backend.providers.ScopeChooserActionProviderDelegate
import com.intellij.platform.searchEverywhere.providers.*
import com.intellij.platform.searchEverywhere.providers.target.SeTargetsFilter
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import com.intellij.psi.codeStyle.NameUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.concurrent.atomics.ExperimentalAtomicApi


@Internal
class SeTargetItem(val legacyItem: ItemWithPresentation<*>,
                   private val matchers: ItemMatchers?,
                   private val weight: Int,
                   override val contributor: SearchEverywhereContributor<*>,
                   val extendedDescription: String?) : SeLegacyItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeTargetItemPresentation.create(legacyItem.presentation, matchers, extendedDescription)
  override val rawObject: Any get() = legacyItem
}

@OptIn(ExperimentalAtomicApi::class)
@Internal
class SeTargetsProviderDelegate(private val contributorWrapper: SeAsyncWeightedContributorWrapper<Any>) {
  private val scopeProviderDelegate = ScopeChooserActionProviderDelegate(contributorWrapper)

  suspend fun <T> collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val inputQuery = params.inputQuery
    val defaultMatchers = createDefaultMatchers(inputQuery)

    val scopeToApply: String? = SeEverywhereFilter.isEverywhere(params.filter)?.let { isEverywhere ->
      scopeProviderDelegate.searchScopesInfo.getValue()?.let { searchScopesInfo ->
        if (isEverywhere) searchScopesInfo.everywhereScopeId else searchScopesInfo.projectScopeId
      }
    } ?: run {
      val targetsFilter = SeTargetsFilter.from(params.filter)
      SeTypeVisibilityStateProviderDelegate.applyTypeVisibilityStates<T>(
        contributorWrapper.contributor,
        targetsFilter.hiddenTypes
      )
      targetsFilter.selectedScopeId
    }
    applyScope(scopeToApply)

    coroutineToIndicator {
      val indicator = DelegatingProgressIndicator(ProgressManager.getGlobalProgressIndicator())

      contributorWrapper.fetchWeightedElements(inputQuery, indicator, object : AsyncProcessor<FoundItemDescriptor<Any>> {
        override suspend fun process(t: FoundItemDescriptor<Any>): Boolean {
          val weight = t.weight
          val legacyItem = t.item as? ItemWithPresentation<*> ?: return true
          val matchers = (contributorWrapper.contributor as? PSIPresentationBgRendererWrapper)
            ?.getNonComponentItemMatchers({ _ -> defaultMatchers }, legacyItem.getItem())

          return collector.put(SeTargetItem(legacyItem, matchers, weight, contributorWrapper.contributor, getExtendedDescription(legacyItem)))
        }
      })
    }
  }

  suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeTargetItem)?.legacyItem ?: return false

    return withContext(Dispatchers.EDT) {
      contributorWrapper.contributor.processSelectedItem(legacyItem, modifiers, searchText)
    }
  }

  fun getExtendedDescription(legacyItem: ItemWithPresentation<*>): String? {
    return contributorWrapper.contributor.getExtendedDescription(legacyItem)
  }

  /**
   * Defines if results found by this contributor can be shown in <i>Find</i> toolwindow.
   */
  fun canBeShownInFindResults(): Boolean {
    return contributorWrapper.contributor.showInFindResults()
  }

  private fun createDefaultMatchers(rawPattern: String): ItemMatchers {
    val namePattern = contributorWrapper.contributor.filterControlSymbols(rawPattern)
    val matcher = NameUtil.buildMatcherWithFallback("*$rawPattern", "*$namePattern", NameUtil.MatchingCaseSensitivity.NONE)
    return ItemMatchers(matcher, null)
  }

  private fun applyScope(scopeId: String?) {
    scopeProviderDelegate.applyScope(scopeId)
  }

  suspend fun getSearchScopesInfo(): SearchScopesInfo? {
    return scopeProviderDelegate.searchScopesInfo.getValue()
  }

  fun <T> getTypeVisibilityStates(index: Int): List<SeTypeVisibilityStatePresentation> {
    val contributor = contributorWrapper.contributor
    return SeTypeVisibilityStateProviderDelegate.getStates<T>(contributor, index)
  }
}