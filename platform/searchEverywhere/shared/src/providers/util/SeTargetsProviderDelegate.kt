// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.util

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.ItemWithPresentation
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.AsyncProcessor
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.psi.codeStyle.NameUtil
import org.jetbrains.annotations.ApiStatus.Internal


@Internal
class SeTargetItem(val legacyItem: ItemWithPresentation<*>, private val matchers: ItemMatchers?, private val weight: Int) : SeItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeTargetItemPresentation.create(legacyItem.presentation, matchers)
}

@Internal
class SeTargetsProviderDelegate(private val contributorWrapper: SeAsyncContributorWrapper<Any>) {
  suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val inputQuery = params.inputQuery
    val defaultMatchers = createDefaultMatchers(inputQuery)

    coroutineToIndicator {
      val indicator = DelegatingProgressIndicator(ProgressManager.getGlobalProgressIndicator())

      contributorWrapper.fetchWeightedElements(inputQuery, indicator, object: AsyncProcessor<FoundItemDescriptor<Any>> {
        override suspend fun process(t: FoundItemDescriptor<Any>): Boolean {
          val weight = t.weight
          val legacyItem = t.item as? ItemWithPresentation<*> ?: return true
          val matchers = (contributorWrapper.contributor as? PSIPresentationBgRendererWrapper)
            ?.getNonComponentItemMatchers({ _ -> defaultMatchers }, t.item)

          return collector.put(SeTargetItem(legacyItem, matchers, weight))
        }
      })
    }
  }

  fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeTargetItem)?.legacyItem ?: return false
    return contributorWrapper.contributor.processSelectedItem(legacyItem, modifiers, searchText)
  }

  private fun createDefaultMatchers(rawPattern: String): ItemMatchers {
    val namePattern = contributorWrapper.contributor.filterControlSymbols(rawPattern)
    val matcher = NameUtil.buildMatcherWithFallback("*$rawPattern", "*$namePattern", NameUtil.MatchingCaseSensitivity.NONE)
    return ItemMatchers(matcher, null)
  }
}