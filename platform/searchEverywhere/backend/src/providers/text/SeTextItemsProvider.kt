// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.text

import com.intellij.find.FindManager
import com.intellij.find.impl.JComboboxAction
import com.intellij.find.impl.SearchEverywhereItem
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.toSerializableTextChunk
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.backend.providers.ScopeChooserActionProviderDelegate
import com.intellij.platform.searchEverywhere.providers.*
import com.intellij.platform.searchEverywhere.providers.getExtendedInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent

@ApiStatus.Internal
class SeTextSearchItem(val item: SearchEverywhereItem, private val weight: Int, val extendedInfo: SeExtendedInfo, val isMultiSelectionSupported: Boolean) : SeItem {
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
class SeTextItemsProvider(project: Project, private val contributorWrapper: SeAsyncWeightedContributorWrapper<Any>) : SeItemsProvider, SeSearchScopesProvider {
  private val contributor = contributorWrapper.contributor
  override val id: String get() = SeProviderIdUtils.TEXT_ID
  override val displayName: @Nls String
    get() = contributor.fullGroupName
  private val findModel = FindManager.getInstance(project).findInProjectModel
  private val scopeProviderDelegate = ScopeChooserActionProviderDelegate(contributorWrapper)

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val inputQuery = params.inputQuery

    val textFilter = SeTextFilter.from(params.filter)

    val scopeToApply: String? = SeEverywhereFilter.isEverywhere(params.filter)?.let { isEverywhere ->
      scopeProviderDelegate.searchScopesInfo.getValue()?.let { searchScopesInfo ->
        if (isEverywhere) searchScopesInfo.everywhereScopeId else searchScopesInfo.projectScopeId
      }
    } ?: run {
      textFilter?.selectedScopeId
    }
    applyScope(scopeToApply)

    if (textFilter != null) {
      // Sync the file mask from the filter to the TextSearchContributor's JComboboxAction to prevent state conflicts
      contributorWrapper.contributor.getActions { }.filterIsInstance<JComboboxAction>().firstOrNull()?.onMaskChanged(textFilter.selectedType)
      // Apply type for the correct search
      findModel.fileFilter = textFilter.selectedType

      findModel.isCaseSensitive = SeTextFilter.isCaseSensitive(params.filter) ?: false
      findModel.isWholeWordsOnly = SeTextFilter.isWholeWordsOnly(params.filter) ?: false
      findModel.isRegularExpressions = SeTextFilter.isRegularExpressions(params.filter) ?: false
    }

    coroutineToIndicator {
      val indicator = DelegatingProgressIndicator(ProgressManager.getGlobalProgressIndicator())

      contributorWrapper.fetchWeightedElements(inputQuery, indicator, object : AsyncProcessor<FoundItemDescriptor<Any>> {
        override suspend fun process(t: FoundItemDescriptor<Any>): Boolean {
          val weight = t.weight
          val legacyItem = t.item as? SearchEverywhereItem ?: return true

          return collector.put(SeTextSearchItem(legacyItem, weight, contributor.getExtendedInfo(legacyItem), contributorWrapper.contributor.isMultiSelectionSupported))
        }
      })
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

  private fun applyScope(scopeId: String?) {
    scopeProviderDelegate.applyScope(scopeId)
  }

  override suspend fun getSearchScopesInfo(): SearchScopesInfo? {
    return scopeProviderDelegate.searchScopesInfo.getValue()
  }

  override suspend fun performExtendedAction(item: SeItem): Boolean {
    val legacyItem = (item as? SeTextSearchItem)?.item ?: return false
    return withContext(Dispatchers.EDT) {
      contributor.processSelectedItem(legacyItem, InputEvent.SHIFT_DOWN_MASK, "")
    }
  }

  override fun dispose() {
    Disposer.dispose(contributorWrapper)
  }
}
