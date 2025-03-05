// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.files

import com.intellij.ide.actions.searcheverywhere.AsyncProcessor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.ItemWithPresentation
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereAsyncContributor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemPresentation
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeTextItemPresentation
import com.intellij.platform.searchEverywhere.SeTextSearchParams
import com.intellij.platform.searchEverywhere.api.SeItem
import com.intellij.platform.searchEverywhere.api.SeItemsProvider
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeFileItem(val legacyItem: ItemWithPresentation<*>) : SeItem {
  override fun weight(): Int = 0
  override fun presentation(): SeItemPresentation = SeTextItemPresentation(legacyItem.presentation.presentableText)
}

@Internal
class SeFilesProvider(val project: Project, private val legacyContributor: SearchEverywhereAsyncContributor<Any?>): SeItemsProvider {
  override val id: String get() = ID

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val textSearchParams = params as? SeTextSearchParams ?: return
    val text = textSearchParams.text
    val filter = SeFilesFilterData.fromTabData(textSearchParams.filterData)

    coroutineToIndicator {
      val indicator = ProgressManager.getGlobalProgressIndicator()

      legacyContributor.fetchElements(text, indicator, object: AsyncProcessor<Any?> {
        override suspend fun process(t: Any?): Boolean {
          val legacyItem = t as? ItemWithPresentation<*> ?: return true
          return collector.put(SeFileItem(legacyItem))
        }
      })
    }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeFileItem)?.legacyItem ?: return false
    return legacyContributor.synchronousContributor.processSelectedItem(legacyItem, modifiers, searchText)
  }

  companion object {
    const val ID: String = "com.intellij.FileSearchEverywhereItemProvider"
  }
}