// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.target

import com.intellij.ide.actions.searcheverywhere.PersistentSearchEverywhereContributorFilter
import com.intellij.ide.actions.searcheverywhere.PreviewAction
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFiltersAction
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.scopes.SearchScopeData
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.frontend.tabs.utils.SeFilterEditorBase
import com.intellij.platform.searchEverywhere.frontend.tabs.utils.SeTypeVisibilityStateHolder
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.target.SeTargetsFilter
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeTargetsFilterEditor(
  private val project: Project?,
  tabId: String,
  private val scopesInfo: SearchScopesInfo?,
  typeVisibilityStates: List<SeTypeVisibilityStatePresentation>?,
  private val hasPreviewAction: Boolean,
  persistScopeIfAvailable: Boolean = false,
) : SeFilterEditorBase<SeTargetsFilter>( run {
  val selectedScopeId = SeScopePersistentStorage.create(project, tabId, scopesInfo, persistScopeIfAvailable)?.getScope()?.scopeId
                        ?: scopesInfo?.selectedScopeId
  SeTargetsFilter(selectedScopeId,
                  selectedScopeId != scopesInfo?.everywhereScopeId,
                  hiddenTypes(typeVisibilityStates))
}
) {
  private val updateFilterValueWithVisibilityStates = {
    filterValue = filterValue.cloneWith(hiddenTypes(visibilityStateHolder?.elements))
  }

  private val visibilityStateHolder: SeTypeVisibilityStateHolder? =
    typeVisibilityStates?.takeIf { it.isNotEmpty() }?.let {
      SeTypeVisibilityStateHolder(it, updateFilterValueWithVisibilityStates)
    }

  private val scopePersistentStorage: SeScopePersistentStorage? =
    SeScopePersistentStorage.create(project, tabId, scopesInfo, persistScopeIfAvailable)

  private val scopeFilterAction: AnAction? = scopesInfo?.let {
    SeScopeChooserActionProvider(scopesInfo,
                                 scopePersistentStorage?.getScope()?.scopeId ?: scopesInfo.selectedScopeId,
                                 onPersistScope = {
                                   scopePersistentStorage?.persist(it)
                                 },
                                 onSelectedScopeChanged = { scopeId, isAutoToggleEnabled ->
                                   filterValue = filterValue.cloneWith(scopeId, isAutoToggleEnabled)
                                 }).getAction()
  }

  override fun getHeaderActions(): List<AnAction> = listOfNotNull(getScopeFilterAction(),
                                                                  if (hasPreviewAction) PreviewAction() else null,
                                                                  getTypeFilterAction())

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

private class SeScopePersistentStorage(private val project: Project, private val tabId: String, private val scopeInfo: SearchScopesInfo) {
  private val persistedScopeKey: String = "SearchEverywhere.PersistedScope.$tabId"
  private val scopePersistentStorage: PropertiesComponent get() = PropertiesComponent.getInstance(project)

  fun persist(scopeId: String?) {
    val scopeName = scopeInfo.scopes.firstOrNull { it.scopeId == scopeId }?.name
    SeLog.log(SeLog.SCOPE) { "Persisting scope with name: $scopeName for tab: $tabId" }

    scopePersistentStorage.setValue(persistedScopeKey, scopeName)
  }

  fun getScope(): SearchScopeData? {
    @NlsSafe
    val scopeName: String? = scopePersistentStorage.getValue(persistedScopeKey)
    SeLog.log(SeLog.SCOPE) { "Retrieving persisted scope with name: $scopeName for tab: $tabId" }

    return scopeName?.let { scopeId ->
      scopeInfo.scopes.firstOrNull { it.name == scopeName }
    }
  }

  companion object {
    fun create(project: Project?, tabId: String, scopeInfo: SearchScopesInfo?, persistScopeIfAvailable: Boolean): SeScopePersistentStorage? {
      project ?: return null
      scopeInfo ?: return null
      if (!persistScopeIfAvailable) return null

      return SeScopePersistentStorage(project, tabId, scopeInfo)
    }
  }
}
