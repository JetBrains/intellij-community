// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.topHit

import com.intellij.ide.actions.searcheverywhere.TopHitSEContributor
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.AsyncProcessor
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.getExtendedDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SeTopHitItem(val item: Any, private val weight: Int, private val project: Project, val extendedDescription: String?, val isMultiSelectionSupported: Boolean) : SeItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeTopHitItemPresentationProvider.getPresentation(item, project, extendedDescription, isMultiSelectionSupported)
}

@ApiStatus.Internal
open class SeTopHitItemsProvider(
  private val isHost: Boolean,
  private val project: Project,
  private val contributorWrapper: SeAsyncContributorWrapper<Any>,
  override val displayName: @Nls String,
) : SeItemsProvider {
  override val id: String get() = id(isHost)

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val inputQuery = params.inputQuery
    val additionalWeight = if (isHost) 0 else 1
    val weight = TopHitSEContributor.TOP_HIT_ELEMENT_PRIORITY + additionalWeight

    coroutineToIndicator {
      val indicator = DelegatingProgressIndicator(ProgressManager.getGlobalProgressIndicator())

      contributorWrapper.fetchElements(inputQuery, indicator, object : AsyncProcessor<Any> {
        override suspend fun process(t: Any): Boolean {
          return collector.put(SeTopHitItem(t, weight, project, getExtendedDescription(t), contributorWrapper.contributor.isMultiSelectionSupported))
        }
      })
    }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeTopHitItem)?.item ?: return false
    return withContext(Dispatchers.EDT) {
      contributorWrapper.contributor.processSelectedItem(legacyItem, modifiers, searchText)
    }
  }

  fun getExtendedDescription(item: Any): String? {
    return contributorWrapper.contributor.getExtendedDescription(item)
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return contributorWrapper.contributor.showInFindResults()
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }

  companion object {
    fun id(isHost: Boolean): String = if (isHost) SeProviderIdUtils.TOP_HIT_HOST_ID else SeProviderIdUtils.TOP_HIT_ID
  }
}