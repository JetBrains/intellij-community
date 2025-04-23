// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.text

import com.intellij.find.impl.SearchEverywhereItem
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.AsyncProcessor
import com.intellij.platform.searchEverywhere.providers.SeAsyncWeightedContributorWrapper
import com.intellij.platform.searchEverywhere.providers.getExtendedDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SeTextSearchItem(val item: SearchEverywhereItem, private val weight: Int, val extendedDescription: String?) : SeItem {
  override fun weight(): Int = weight

  override suspend fun presentation(): SeItemPresentation =
    SeTextSearchItemPresentation(item.presentableText,
                                 extendedDescription,
                                 item.presentation.text.map { chunk ->
                                   SerializableTextChunk(
                                     chunk.text,
                                     chunk.attributes.foregroundColor.rpcId(),
                                     chunk.attributes.fontType
                                   )
                                 },
                                 item.presentation.backgroundColor?.rpcId(),
                                 item.presentation.fileString)
}

@ApiStatus.Internal
class SeTextItemsProvider(private val contributorWrapper: SeAsyncWeightedContributorWrapper<Any>) : SeItemsProvider {
  override val id: String get() = ID
  override val displayName: @Nls String
    get() = contributorWrapper.contributor.fullGroupName

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val inputQuery = params.inputQuery

    coroutineToIndicator {
      val indicator = DelegatingProgressIndicator(ProgressManager.getGlobalProgressIndicator())

      contributorWrapper.fetchWeightedElements(inputQuery, indicator, object : AsyncProcessor<FoundItemDescriptor<Any>> {
        override suspend fun process(t: FoundItemDescriptor<Any>): Boolean {
          val weight = t.weight
          val legacyItem = t.item as? SearchEverywhereItem ?: return true

          return collector.put(SeTextSearchItem(legacyItem, weight, getExtendedDescription(legacyItem)))
        }
      })
    }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeTextSearchItem)?.item ?: return false
    return withContext(Dispatchers.EDT) {
      contributorWrapper.contributor.processSelectedItem(legacyItem, modifiers, searchText)
    }
  }

  fun getExtendedDescription(item: SearchEverywhereItem): String? {
    return contributorWrapper.contributor.getExtendedDescription(item)
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return contributorWrapper.contributor.showInFindResults()
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }

  companion object {
    const val ID: String = "com.intellij.TextSearchEverywhereItemProvider"
  }
}
