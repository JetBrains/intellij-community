// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.target

import com.intellij.ide.actions.searcheverywhere.PersistentSearchEverywhereContributorFilter
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFiltersAction
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.platform.searchEverywhere.SeSearchScopesInfo
import com.intellij.platform.searchEverywhere.frontend.SeFilterActionsPresentation
import com.intellij.platform.searchEverywhere.frontend.SeFilterPresentation
import com.intellij.platform.searchEverywhere.frontend.tabs.utils.SeFilterEditorBase
import com.intellij.platform.searchEverywhere.frontend.tabs.utils.SeTypeVisibilityStateHolder
import com.intellij.platform.searchEverywhere.providers.target.SeTargetsFilter
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeTargetsFilterEditor(private val scopesInfo: SeSearchScopesInfo?,
                            typeVisibilityStates: List<SeTypeVisibilityStatePresentation>?) : SeFilterEditorBase<SeTargetsFilter>(
  SeTargetsFilter(scopesInfo?.selectedScopeId, hiddenTypes(typeVisibilityStates))
) {
  private val updateFilterValueWithVisibilityStates = {
    filterValue = filterValue.cloneWith(hiddenTypes(visibilityStateHolder?.elements))
  }

  private val visibilityStateHolder: SeTypeVisibilityStateHolder? =
    typeVisibilityStates?.takeIf { it.isNotEmpty() }?.let { it ->
      SeTypeVisibilityStateHolder(it, updateFilterValueWithVisibilityStates)
    }

  private val scopeFilterAction: AnAction? = scopesInfo?.let {
    SeScopeChooserActionProvider(scopesInfo) {
      filterValue = filterValue.cloneWith(it)
    }.getAction()
  }

  override fun getPresentation(): SeFilterPresentation {
    return object : SeFilterActionsPresentation {
      override fun getActions(): List<AnAction> = listOfNotNull(getScopeFilterAction(), getTypeFilterAction())
    }
  }

  private fun getScopeFilterAction(): AnAction? {
    return scopeFilterAction
  }

  private fun getTypeFilterAction(): AnAction? {
    if (visibilityStateHolder == null) return null

    val persistentFilter = PersistentSearchEverywhereContributorFilter(visibilityStateHolder.elements,
                                                                       visibilityStateHolder,
                                                                       { it.name },
                                                                       { it.iconId?.icon() })
    return SearchEverywhereFiltersAction(persistentFilter, updateFilterValueWithVisibilityStates)
  }

  companion object {
    private fun hiddenTypes(all: List<SeTypeVisibilityStatePresentation>?) = all?.filter { !it.isEnabled }?.map { it.name }
  }
}
