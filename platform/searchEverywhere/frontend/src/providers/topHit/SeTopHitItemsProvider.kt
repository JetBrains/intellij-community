// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.topHit

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.TopHitSEContributor
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemPresentation
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.providers.AsyncProcessor
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SeTopHitItem(val item: Any, private val weight: Int, private val project: Project): SeItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeTopHitItemPresentationProvider.getPresentation(item, project)
}

@ApiStatus.Internal
class SeTopHitItemsProvider(private val project: Project,
                            private val contributorWrapper: SeAsyncContributorWrapper<Any>): SeItemsProvider {
  override val id: String = ID
  override val displayName: @Nls String = IdeBundle.message("search.everywhere.group.name.top.hit")

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val inputQuery = params.inputQuery
    val weight = TopHitSEContributor.TOP_HIT_ELEMENT_PRIORITY

    coroutineToIndicator {
      val indicator = DelegatingProgressIndicator(ProgressManager.getGlobalProgressIndicator())

      contributorWrapper.fetchElements(inputQuery, indicator, object: AsyncProcessor<Any> {
        override suspend fun process(t: Any): Boolean {
          return collector.put(SeTopHitItem(t, weight, project))
        }
      })
    }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeTopHitItem)?.item ?: return false
    return withContext(Dispatchers.EDT) {
      contributorWrapper.contributor.processSelectedItem (legacyItem, modifiers, searchText)
    }
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }

  companion object {
    val ID: String = TopHitSEContributor::class.java.getSimpleName()
  }
}