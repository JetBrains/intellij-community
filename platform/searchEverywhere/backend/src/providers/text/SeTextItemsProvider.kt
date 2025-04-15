// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.text

import com.intellij.find.impl.SearchEverywhereItem
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.AsyncProcessor
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.psi.codeStyle.NameUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTextSearchItem(val item: SearchEverywhereItem, private val weight: Int) : SeItem {
  override fun weight(): Int = weight

  override suspend fun presentation(): SeItemPresentation =
    SeTextSearchItemPresentation(item.presentableText,
                                 item.presentation.text.map { chunk ->
                                   SeTextSearchItemPresentation.SerializableTextChunk(
                                     chunk.text,
                                     chunk.attributes.foregroundColor.rpcId(),
                                     chunk.attributes.fontType
                                   )
                                 },
                                 item.presentation.backgroundColor?.rpcId(),
                                 item.presentation.fileString)
}

@ApiStatus.Internal
class SeTextItemsProvider(val project: Project, private val contributorWrapper: SeAsyncContributorWrapper<Any>): SeItemsProvider {
  override val id: String get() = ID

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val inputQuery = params.inputQuery

    coroutineToIndicator {
      val indicator = DelegatingProgressIndicator(ProgressManager.getGlobalProgressIndicator())

      contributorWrapper.fetchWeightedElements(inputQuery, indicator, object: AsyncProcessor<FoundItemDescriptor<Any>> {
        override suspend fun process(t: FoundItemDescriptor<Any>): Boolean {
          val weight = t.weight
          val legacyItem = t.item as? SearchEverywhereItem ?: return true

          return collector.put(SeTextSearchItem(legacyItem, weight))
        }
      })
    }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeTextSearchItem)?.item ?: return false
    return contributorWrapper.contributor.processSelectedItem(legacyItem, modifiers, searchText)
  }

  private fun createDefaultMatchers(rawPattern: String): ItemMatchers {
    val namePattern = contributorWrapper.contributor.filterControlSymbols(rawPattern)
    val matcher = NameUtil.buildMatcherWithFallback("*$rawPattern", "*$namePattern", NameUtil.MatchingCaseSensitivity.NONE)
    return ItemMatchers(matcher, null)
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }

  companion object {
    const val ID: String = "com.intellij.TextSearchEverywhereItemProvider"
  }
}
