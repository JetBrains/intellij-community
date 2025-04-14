// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.target

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.ItemWithPresentation
import com.intellij.ide.actions.searcheverywhere.ScopeChooserAction
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.AsyncProcessor
import com.intellij.platform.searchEverywhere.providers.SeAsyncWeightedContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeTypeVisibilityStateProviderDelegate
import com.intellij.platform.searchEverywhere.providers.getExtendedDescription
import com.intellij.platform.searchEverywhere.providers.target.SeTargetsFilter
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import com.intellij.psi.codeStyle.NameUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import java.util.concurrent.ConcurrentHashMap


@Internal
class SeTargetItem(val legacyItem: ItemWithPresentation<*>, private val matchers: ItemMatchers?, private val weight: Int, val extendedDescription: String?) : SeItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeTargetItemPresentation.create(legacyItem.presentation, matchers, extendedDescription)
}

@Internal
class SeTargetsProviderDelegate(private val contributorWrapper: SeAsyncWeightedContributorWrapper<Any>) {
  @Volatile
  private var scopeIdToScope: ConcurrentHashMap<String, ScopeDescriptor> = ConcurrentHashMap()

  suspend fun <T> collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val inputQuery = params.inputQuery
    val defaultMatchers = createDefaultMatchers(inputQuery)
    val filter = SeTargetsFilter.from(params.filter)

    applyScope(filter.selectedScopeId)
    SeTypeVisibilityStateProviderDelegate.applyTypeVisibilityStates<T>(contributorWrapper.contributor, filter.hiddenTypes)

    coroutineToIndicator {
      val indicator = DelegatingProgressIndicator(ProgressManager.getGlobalProgressIndicator())

      contributorWrapper.fetchWeightedElements(inputQuery, indicator, object : AsyncProcessor<FoundItemDescriptor<Any>> {
        override suspend fun process(t: FoundItemDescriptor<Any>): Boolean {
          val weight = t.weight
          val legacyItem = t.item as? ItemWithPresentation<*> ?: return true
          val matchers = (contributorWrapper.contributor as? PSIPresentationBgRendererWrapper)
            ?.getNonComponentItemMatchers({ _ -> defaultMatchers }, t.item)

          return collector.put(SeTargetItem(legacyItem, matchers, weight, getExtendedDescription(legacyItem)))
        }
      })
    }
  }

  fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeTargetItem)?.legacyItem ?: return false
    return contributorWrapper.contributor.processSelectedItem(legacyItem, modifiers, searchText)
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
    if (scopeId == null) return
    val scope = scopeIdToScope[scopeId] ?: return

    contributorWrapper.contributor.getActions { }.filterIsInstance<ScopeChooserAction>().firstOrNull()?.onScopeSelected(scope)
  }

  suspend fun getSearchScopesInfo(): SeSearchScopesInfo? {
    val contributor = contributorWrapper.contributor
    val scopeChooserAction: ScopeChooserAction = contributor.getActions({ }).filterIsInstance<ScopeChooserAction>().firstOrNull()
                                                 ?: return null

    val all = mutableMapOf<String, ScopeDescriptor>()
    val selectedScopeName = scopeChooserAction.selectedScope.displayName
    var selectedScopeId: String? = null

    val scopeDataList = readAction {
      scopeChooserAction.scopesWithSeparators
    }.mapNotNull { scope ->
      val key = UUID.randomUUID().toString()
      if (selectedScopeName == scope.displayName) selectedScopeId = key

      val data = SeSearchScopeData.from(scope, key)
      if (data != null) all[key] = scope
      data
    }

    scopeIdToScope = ConcurrentHashMap(all)
    val everywhereScopeId = scopeChooserAction.everywhereScopeName?.let { name ->
      scopeDataList.firstOrNull {
        @Suppress("HardCodedStringLiteral")
        it.name == name
      }?.scopeId
    }

    val projectScopeId = scopeChooserAction.projectScopeName?.let { name ->
      scopeDataList.firstOrNull {
        @Suppress("HardCodedStringLiteral")
        it.name == name
      }?.scopeId
    }

    return SeSearchScopesInfo(scopeDataList,
                              selectedScopeId,
                              scopeChooserAction.canToggleEverywhere(),
                              projectScopeId,
                              everywhereScopeId)
  }

  fun <T> getTypeVisibilityStates(): List<SeTypeVisibilityStatePresentation> {
    val contributor = contributorWrapper.contributor
    return SeTypeVisibilityStateProviderDelegate.getStates<T>(contributor)
  }
}