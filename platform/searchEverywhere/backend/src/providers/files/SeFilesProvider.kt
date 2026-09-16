// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.files

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.SeExtendedInfoProvider
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsPreviewProvider
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeSearchScopesProvider
import com.intellij.platform.searchEverywhere.SeTypeVisibilityStateProvider
import com.intellij.platform.searchEverywhere.providers.target.SeTargetItemsProvider
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import org.jetbrains.annotations.Nls

internal class SeFilesProvider private constructor(
  private val targetProvider: SeTargetItemsProvider<FileTypeRef>,
) : SeItemsProvider,
    SeSearchScopesProvider,
    SeTypeVisibilityStateProvider,
    SeItemsPreviewProvider,
    SeExtendedInfoProvider {
  override val id: String get() = SeProviderIdUtils.FILES_ID
  override val displayName: @Nls String get() = IdeBundle.message("search.everywhere.group.name.files")

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector): Unit =
    targetProvider.collectItems(params, collector)

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean =
    targetProvider.itemSelected(item, this, modifiers, searchText)

  override suspend fun performExtendedAction(item: SeItem): Boolean =
    targetProvider.performExtendedAction(item, this)

  override suspend fun getSearchScopesInfo(): SearchScopesInfo? = targetProvider.getSearchScopesInfo()

  override suspend fun getTypeVisibilityStates(index: Int): List<SeTypeVisibilityStatePresentation> =
    targetProvider.getTypeVisibilityStates(index)

  override suspend fun getPreviewInfo(item: SeItem, project: Project): SePreviewInfo? =
    targetProvider.getPreviewInfo(item)

  override suspend fun canBeShownInFindResults(): Boolean = true

  override fun dispose() {
    // The target provider holds the disposables that a preview fetch opened.
    Disposer.dispose(targetProvider)
  }

  companion object {
    /**
     * `setFilterItems` is a whitelist of the types to show. An empty set rejects every file, and an
     * unset filter accepts every file.
     */
    private fun createModel(project: Project, hiddenTypes: Set<FileTypeRef>): FilteringGotoByModel<FileTypeRef> {
      val model = GotoFileModel(project)
      model.setFilterItems(FileSearchEverywhereContributor.getAllFileTypes().filterNot { it in hiddenTypes })
      return model
    }

    suspend fun create(project: Project, dataContext: DataContext): SeFilesProvider {
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
        isFileProvider = true,
      )

      return SeFilesProvider(targetProvider)
    }
  }
}
