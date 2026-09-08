// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.files

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.SeExtendedInfoBuilder
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeSearchScopesProvider
import com.intellij.platform.searchEverywhere.SeTypeVisibilityStateProvider
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.target.SeTargetItemsProvider
import com.intellij.platform.searchEverywhere.providers.target.SeTargetPresentableItem
import com.intellij.platform.searchEverywhere.providers.target.SeTargetRawItem
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import com.intellij.platform.searchEverywhere.providers.target.presentation.SeTargetPresentationProvider
import com.intellij.psi.PsiDirectory
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import org.jetbrains.annotations.Nls

internal class SeFilesProvider private constructor(private val targetProvider: SeTargetItemsProvider) : SeItemsProvider,
                                                                                                        SeSearchScopesProvider,
                                                                                                        SeTypeVisibilityStateProvider {
  override val id: String get() = SeProviderIdUtils.FILES_ID
  override val displayName: @Nls String get() = IdeBundle.message("search.everywhere.group.name.files")

  override suspend fun collectItems(
    params: SeParams,
    collector: SeItemsProvider.Collector,
  ) = coroutineScope {
    val inputQuery = SeTargetItemsProvider.normalizeQuery(params.inputQuery)
    val inputQueryHasNoExtension = !inputQuery.contains('.')

    targetProvider.getItemsFlow(params, presentationProvider = { fetchPresentation(it, inputQuery, inputQueryHasNoExtension) })
      .buffer(capacity = 0, onBufferOverflow = BufferOverflow.SUSPEND)
      .takeWhile {
        collector.put(it)
      }
      .collect()
  }

  private suspend fun fetchPresentation(
    item: SeTargetRawItem,
    inputQuery: String,
    inputQueryHasNoExtension: Boolean,
  ): SeTargetPresentableItem {
    val weight = item.rawWeight ?: 0
    val presentation = SeTargetPresentationProvider.computePresentation(item.rawItem)
                       ?: TargetPresentation.builder("").presentation()

    return SeTargetPresentableItem(
      rawItem = item.rawItem,
      matchers = item.matchers,
      weight = weight,
      presentation = presentation,
      extendedInfo = SeExtendedInfoBuilder().build(), // TODO: provide the extended info
      isMultiSelectionSupported = true, // AbstractGotoSEContributor supports it for every goto model
      isExactMatch = SeTargetItemsProvider.isExactMatch(
        // The legacy verdict of the item. SeAsyncContributorWrapper derives it the same way.
        isExactMatchFromItem = DefaultChooseByNameItemProvider.isInExactMatchDegreeRange(weight),
        presentableText = presentation.presentableText,
        inputQuery = inputQuery,
        isFile = id == SeProviderIdUtils.FILES_ID,
        inputQueryHasNoExtension = inputQueryHasNoExtension,
        isDirectory = PSIPresentationBgRendererWrapper.toPsi(item.rawItem) is PsiDirectory,
      ),
    )
  }

  override suspend fun itemSelected(
    item: SeItem,
    modifiers: Int,
    searchText: String,
  ): Boolean {
    SeLog.log(SeLog.USER_ACTION) { "SeFilesProvider.itemSelected" }
    return true
  }

  override suspend fun getSearchScopesInfo(): SearchScopesInfo? = targetProvider.getSearchScopesInfo()

  override suspend fun getTypeVisibilityStates(index: Int): List<SeTypeVisibilityStatePresentation> =
    targetProvider.getTypeVisibilityStates(index)

  override suspend fun canBeShownInFindResults(): Boolean = true

  override fun dispose() {

  }

  companion object {
    private fun createModel(project: Project, hiddenTypes: Set<FileTypeRef>): FilteringGotoByModel<FileTypeRef> {
      val model = GotoFileModel(project)
      model.setFilterItems(FileSearchEverywhereContributor.getAllFileTypes().filterNot { it in hiddenTypes })
      return model
    }

    suspend fun create(project: Project,
                       dataContext: DataContext): SeFilesProvider {
      val targetProvider = SeTargetItemsProvider.create(
        project = project,
        dataContext = dataContext,
        operationDisposable = null,
        label = "SeFiles",
        gotoModelProvider = { project, _, hiddenTypes ->
          createModel(project, hiddenTypes)
        },
        typeFilterProvider = {
          listOf(FileSearchEverywhereContributor.createFileTypeFilter(it))
        },
      )

      return SeFilesProvider(targetProvider)
    }
  }
}
