// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.topHit

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.AsyncProcessor
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProvider
import com.intellij.platform.searchEverywhere.providers.getExtendedInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SeTopHitItem(
  override val rawObject: Any,
  override val contributor: SearchEverywhereContributor<*>,
  private val weight: Int, private val project: Project,
  val extendedInfo: SeExtendedInfo?,
  val isMultiSelectionSupported: Boolean
) : SeLegacyItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeTopHitItemPresentationProvider.getPresentation(rawObject, project, extendedInfo, isMultiSelectionSupported)
}

@ApiStatus.Internal
open class SeTopHitItemsProvider(
  private val isHost: Boolean,
  private val project: Project,
  private val contributorWrapper: SeAsyncContributorWrapper<Any>,
  override val displayName: @Nls String,
) : SeWrappedLegacyContributorItemsProvider(), SeCommandsProviderInterface {
  override val contributor: SearchEverywhereContributor<Any> = contributorWrapper.contributor
  override val id: String get() = id(isHost)

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val inputQuery = params.inputQuery
    val additionalWeight = if (isHost) 0 else 1

    contributorWrapper.fetchElements(inputQuery, object : AsyncProcessor<Any> {
      override suspend fun process(item: Any, weight: Int): Boolean {
        return collector.put(SeTopHitItem(item, contributor, weight + additionalWeight, project, contributor.getExtendedInfo(item), contributorWrapper.contributor.isMultiSelectionSupported))
      }
    })
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeTopHitItem)?.rawObject ?: return false
    return withContext(Dispatchers.EDT) {
      contributor.processSelectedItem(legacyItem, modifiers, searchText)
    }
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return contributor.showInFindResults()
  }

  override fun getSupportedCommands(): List<SeCommandInfo> {
    return contributor.supportedCommands.map { commandInfo -> SeCommandInfo(commandInfo, id) }
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }

  companion object {
    fun id(isHost: Boolean): String = if (isHost) SeProviderIdUtils.TOP_HIT_HOST_ID else SeProviderIdUtils.TOP_HIT_ID
  }
}