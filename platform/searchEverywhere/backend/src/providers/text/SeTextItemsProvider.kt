// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.text

import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.impl.JComboboxAction
import com.intellij.find.impl.SearchEverywhereItem
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.toSerializableTextChunk
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeTextSearchItemPresentation
import com.intellij.platform.searchEverywhere.providers.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent

@ApiStatus.Internal
class SeTextSearchItem(
  val item: SearchEverywhereItem,
  override val contributor: SearchEverywhereContributor<*>,
  private val weight: Int,
  val extendedInfo: SeExtendedInfo,
  val isMultiSelectionSupported: Boolean,
) : SeLegacyItem {
  override val rawObject: Any get() = item
  override fun weight(): Int = weight

  override suspend fun presentation(): SeItemPresentation =
    SeTextSearchItemPresentation(item.presentableText,
                                 extendedInfo,
                                 item.presentation.text.map { chunk ->
                                   chunk.toSerializableTextChunk()
                                 },
                                 item.presentation.backgroundColor?.rpcId(),
                                 item.presentation.fileString,
                                 isMultiSelectionSupported)
}

@ApiStatus.Internal
class SeTextItemsProvider(project: Project, private val contributorWrapper: SeAsyncContributorWrapper<Any>) : SeWrappedLegacyContributorItemsProvider(),
                                                                                                              SeSearchScopesProvider,
                                                                                                              SeItemsPreviewProvider,
                                                                                                              SeExtendedInfoProvider {
  override val id: String get() = SeProviderIdUtils.TEXT_ID
  override val displayName: @Nls String
    get() = contributor.fullGroupName
  override val contributor: SearchEverywhereContributor<Any> get() = contributorWrapper.contributor
  private val findModel = FindManager.getInstance(project).findInProjectModel
  private val scopeProviderDelegate = ScopeChooserActionProviderDelegate.createOrNull(contributorWrapper)

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val inputQuery = params.inputQuery

    val textFilter = SeTextFilter.from(params.filter)

    scopeProviderDelegate?.let { scopeProviderDelegate ->
      val scopeToApply: String? = SeEverywhereFilter.isEverywhere(params.filter)?.let { isEverywhere ->
        scopeProviderDelegate.searchScopesInfo.getValue()?.let { searchScopesInfo ->
          if (isEverywhere) searchScopesInfo.everywhereScopeId else searchScopesInfo.projectScopeId
        }
      } ?: run {
        textFilter?.selectedScopeId
      }

      scopeProviderDelegate.applyScope(scopeToApply, false)
    }

    var originalModel: FindModel? = null
    val isAllTab: Boolean = SeEverywhereFilter.isAllTab(params.filter) == true
    if (isAllTab) {
      originalModel = findModel.clone()
      findModel.fileFilter = null
      findModel.isCaseSensitive = false
      findModel.isWholeWordsOnly = false
      findModel.isRegularExpressions = false
    }
    else if (textFilter != null) {
      // Sync the file mask from the filter to the TextSearchContributor's JComboboxAction to prevent state conflicts
      contributorWrapper.contributor.getActions { }.filterIsInstance<JComboboxAction>().firstOrNull()?.onMaskChanged(textFilter.selectedType)

      findModel.fileFilter = textFilter.selectedType
      findModel.isCaseSensitive = SeTextFilter.isCaseSensitive(params.filter) ?: false
      findModel.isWholeWordsOnly = SeTextFilter.isWholeWordsOnly(params.filter) ?: false
      findModel.isRegularExpressions = SeTextFilter.isRegularExpressions(params.filter) ?: false
    }

    try {
      contributorWrapper.fetchElements(inputQuery, object : AsyncProcessor<Any> {
        override suspend fun process(item: Any, weight: Int): Boolean {
          if (item !is SearchEverywhereItem) return true
          return collector.put(SeTextSearchItem(item, contributor, weight, contributor.getExtendedInfo(item), contributorWrapper.contributor.isMultiSelectionSupported))
        }
      })
    }
    finally {
      originalModel?.let { findModel.copyFrom(originalModel) }
    }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeTextSearchItem)?.item ?: return false
    return withContext(Dispatchers.EDT) {
      contributor.processSelectedItem(legacyItem, modifiers, searchText)
    }
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return contributor.showInFindResults()
  }

  override suspend fun getSearchScopesInfo(): SearchScopesInfo? {
    return scopeProviderDelegate?.searchScopesInfo?.getValue()
  }

  override suspend fun performExtendedAction(item: SeItem): Boolean {
    val legacyItem = (item as? SeTextSearchItem)?.item ?: return false
    return withContext(Dispatchers.EDT) {
      contributor.processSelectedItem(legacyItem, InputEvent.SHIFT_DOWN_MASK, "")
    }
  }

  override suspend fun getPreviewInfo(item: SeItem, project: Project): SePreviewInfo? {
    val legacyItem = (item as? SeTextSearchItem)?.item ?: return null
    val navigationOffsets = legacyItem.usage.mergedInfos.map { it.navigationRange.startOffset to it.navigationRange.endOffset }

    return SePreviewInfoFactory().create(legacyItem.usage.file.rpcId(), navigationOffsets)
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }
}
