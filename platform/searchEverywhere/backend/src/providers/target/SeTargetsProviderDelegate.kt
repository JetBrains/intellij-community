// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.target

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.ItemWithPresentation
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywherePreviewFetcher
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.ide.vfs.rpcId
import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeLegacyItem
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SePreviewInfoFactory
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeTargetItemPresentationBuilder
import com.intellij.platform.searchEverywhere.providers.AsyncProcessor
import com.intellij.platform.searchEverywhere.providers.ScopeChooserActionProviderDelegate
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilter
import com.intellij.platform.searchEverywhere.providers.SeTypeVisibilityStateProviderDelegate
import com.intellij.platform.searchEverywhere.providers.getExtendedInfo
import com.intellij.platform.searchEverywhere.providers.target.SeTargetsFilter
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.text.matching.MatchingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.event.InputEvent
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@Internal
class SeTargetItem(
  val legacyItem: ItemWithPresentation<*>,
  private val matchers: ItemMatchers?,
  private val weight: Int,
  override val contributor: SearchEverywhereContributor<*>,
  val extendedInfo: SeExtendedInfo,
  val isMultiSelectionSupported: Boolean,
) : SeLegacyItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeTargetItemPresentationBuilder()
    .withTargetPresentation(legacyItem.presentation, matchers, extendedInfo, isMultiSelectionSupported)
    .build()
  override val rawObject: Any get() = legacyItem
}

@OptIn(ExperimentalAtomicApi::class)
@Internal
class SeTargetsProviderDelegate(private val contributorWrapper: SeAsyncContributorWrapper<Any>, parentDisposable: Disposable): Disposable {
  private val scopeProviderDelegate = ScopeChooserActionProviderDelegate.createOrNull(contributorWrapper)
  private val contributor = contributorWrapper.contributor
  private val usagePreviewDisposableList = ConcurrentLinkedQueue<Disposable>()

  init {
    Disposer.register(parentDisposable, this)
  }

  suspend fun <T> collectItems(params: SeParams, collector: SeItemsProvider.Collector, operationDisposable: Disposable? = null) {
    val inputQuery = params.inputQuery
    val defaultMatchers = createDefaultMatchers(inputQuery)

    scopeProviderDelegate?.let { scopeProviderDelegate ->
      SeEverywhereFilter.isEverywhere(params.filter)?.let { isEverywhere ->
        scopeProviderDelegate.applyScope(isEverywhere, false)
      } ?: run {
        val targetsFilter = SeTargetsFilter.from(params.filter)
        SeTypeVisibilityStateProviderDelegate.applyTypeVisibilityStates<T>(contributor, targetsFilter.hiddenTypes)
        scopeProviderDelegate.applyScope(targetsFilter.selectedScopeId, targetsFilter.isAutoTogglePossible)
      }
    }

    contributorWrapper.fetchElements(inputQuery, object : AsyncProcessor<Any> {
      override suspend fun process(item: Any, weight: Int): Boolean {
        val legacyItem = item as? ItemWithPresentation<*> ?: return true
        val matchers = (contributor as? PSIPresentationBgRendererWrapper)
          ?.getNonComponentItemMatchers({ _ -> defaultMatchers }, legacyItem.getItem())

        return collector.put(SeTargetItem(legacyItem, matchers, weight, contributor, contributor.getExtendedInfo(legacyItem), contributorWrapper.contributor.isMultiSelectionSupported))
      }
    }, operationDisposable)
  }

  suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeTargetItem)?.legacyItem ?: return false

    return withContext(Dispatchers.EDT) {
      contributor.processSelectedItem(legacyItem, modifiers, searchText)
    }
  }

  suspend fun getPreviewInfo(item: SeItem, project: Project): SePreviewInfo? {
    val legacyItem = (item as? SeTargetItem)?.legacyItem ?: return null

    val usageInfo = readAction {
      SearchEverywherePreviewFetcher.findFirstChild(legacyItem, project) {
        usagePreviewDisposableList.add(it)
      }
    }
    if (usageInfo?.virtualFile == null) return null

    val fileIndex = ProjectFileIndex.getInstance(project)
    // PsiElement is null for non-decompiled class files, so hide all library files
    if (AppMode.isRemoteDevHost() && readAction {
        fileIndex.isInLibraryClasses(usageInfo.virtualFile!!) ||
         fileIndex.isInLibrarySource(usageInfo.virtualFile!!)
      }) return null

    val rangeResult = readAction {
      val range = usageInfo.smartPointer.psiRange ?: try {
        usageInfo.navigationRange
      }
      catch (_: Exception) {
        return@readAction null
      }
      range?.let { it.startOffset to it.endOffset }
    }
    val (startOffset, endOffset) = rangeResult ?: return null

    return SePreviewInfoFactory().create(usageInfo.virtualFile!!.rpcId(), listOf(startOffset to endOffset))
  }

  /**
   * Defines if results found by this contributor can be shown in <i>Find</i> toolwindow.
   */
  fun canBeShownInFindResults(): Boolean {
    return contributor.showInFindResults()
  }

  private fun createDefaultMatchers(rawPattern: String): ItemMatchers {
    val namePattern = contributor.filterControlSymbols(rawPattern)
    val matcher = NameUtil.buildMatcherWithFallback("*$rawPattern", "*$namePattern", MatchingMode.IGNORE_CASE)
    return ItemMatchers(matcher, null)
  }

  suspend fun getSearchScopesInfo(): SearchScopesInfo? {
    return scopeProviderDelegate?.searchScopesInfo?.getValue()
  }

  fun <T> getTypeVisibilityStates(index: Int): List<SeTypeVisibilityStatePresentation> {
    return SeTypeVisibilityStateProviderDelegate.getStates<T>(contributor, index)
  }

  suspend fun performExtendedAction(item: SeItem): Boolean {
    val legacyItem = (item as? SeTargetItem)?.legacyItem ?: return false
    return withContext(Dispatchers.EDT) {
      contributor.processSelectedItem(legacyItem, InputEvent.SHIFT_DOWN_MASK, "")
    }
  }

  override fun dispose() {
    usagePreviewDisposableList.forEach { Disposer.dispose(it) }
  }
}