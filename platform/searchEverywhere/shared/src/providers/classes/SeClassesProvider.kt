// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.classes

import com.intellij.ide.actions.GotoClassPresentationUpdater
import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.ide.util.gotoByName.GotoClassModel2
import com.intellij.ide.util.gotoByName.LanguageRef
import com.intellij.ide.util.gotoByName.LanguageRef.Companion.forAllLanguages
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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/** The coroutine based counterpart of [SeClassesLegacyBasedProvider]. */
@ApiStatus.Internal
class SeClassesProvider private constructor(
  private val targetProvider: SeTargetItemsProvider<LanguageRef>,
) : SeItemsProvider,
    SeSearchScopesProvider,
    SeTypeVisibilityStateProvider,
    SeItemsPreviewProvider,
    SeExtendedInfoProvider {
  override val id: String get() = SeProviderIdUtils.CLASSES_ID
  override val displayName: @Nls String get() = GotoClassPresentationUpdater.getTabTitlePluralized()

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
     * `setFilterItems` is a whitelist of the languages to show, so the hidden ones come out of the full
     * list. `ClassSearchEverywhereContributor.createModel` passes `filter.selectedElements`, which is
     * the same set.
     */
    private fun createModel(project: Project, hiddenLanguages: Set<LanguageRef>): FilteringGotoByModel<LanguageRef> {
      val model = GotoClassModel2(project)
      model.setFilterItems(forAllLanguages().filterNot { it in hiddenLanguages })
      return model
    }

    suspend fun create(project: Project, dataContext: DataContext): SeClassesProvider {
      val targetProvider = SeTargetItemsProvider.create(
        project = project,
        dataContext = dataContext,
        operationDisposable = null,
        label = "SeClasses",
        gotoModelProvider = { project, _, hiddenLanguages ->
          createModel(project, hiddenLanguages)
        },
        typeFilterProvider = {
          listOf(ClassSearchEverywhereContributor.createLanguageFilter(it))
        },
      )

      return SeClassesProvider(targetProvider)
    }
  }
}
