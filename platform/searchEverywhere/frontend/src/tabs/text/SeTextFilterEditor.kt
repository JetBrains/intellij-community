// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.text

import com.intellij.find.FindManager
import com.intellij.find.FindSettings
import com.intellij.find.impl.JComboboxAction
import com.intellij.find.impl.TextSearchRightActionAction.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.Project
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.SeTextSearchOptions
import com.intellij.platform.searchEverywhere.frontend.tabs.target.SeScopeChooserActionProvider
import com.intellij.platform.searchEverywhere.frontend.tabs.utils.SeFilterEditorBase
import com.intellij.platform.searchEverywhere.providers.SeTextFilter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTextFilterEditor(
  private val project: Project?,
  private val scopesInfo: SearchScopesInfo?,
  initialTextSearchOptions: SeTextSearchOptions?,
  private val disposable: Disposable,
  registerShortcut: (AnAction) -> Unit,
) : SeFilterEditorBase<SeTextFilter>(
  SeTextFilter(selectedScopeId = scopesInfo?.selectedScopeId,
               selectedType = null,
               isCaseSensitive = initialTextSearchOptions?.isCaseSensitive ?: false,
               isWholeWordsOnly = initialTextSearchOptions?.isWholeWordsOnly ?: false,
               isRegex = initialTextSearchOptions?.isRegex ?: false)
) {
  private val scopeFilterAction: AnAction? = scopesInfo?.let {
    SeScopeChooserActionProvider(scopesInfo) {
      filterValue = filterValue.cloneWithScope(it)
    }.getAction()
  }
  private val typesFilterAction: JComboboxAction? = project?.let {
    JComboboxAction(project, disposable) { filterValue = filterValue.cloneWithType(it) }.also {
      disposable.whenDisposed { it.saveMask() }
    }
  }
  private val caseSensitiveAction = CaseSensitiveAction(AtomicBooleanProperty(initialTextSearchOptions?.isCaseSensitive ?: false).apply {
    afterChange { filterValue = filterValue.cloneWithCase(it) }
  }, registerShortcut) { }
  private val wordAction = WordAction(AtomicBooleanProperty(initialTextSearchOptions?.isWholeWordsOnly ?: false).apply {
    afterChange { filterValue = filterValue.cloneWithWords(it) }
  }, registerShortcut) { }
  private val regexpAction = RegexpAction(AtomicBooleanProperty(initialTextSearchOptions?.isRegex ?: false).apply {
    afterChange { filterValue = filterValue.cloneWithRegex(it) }
  }, registerShortcut) { }

  override fun getHeaderActions(): List<AnAction> = listOfNotNull(scopeFilterAction, typesFilterAction)

  override fun getSearchFieldActions(): List<AnAction> = listOf(caseSensitiveAction, wordAction, regexpAction)

  fun changeType(type: String?) {
    typesFilterAction?.let {
      filterValue = filterValue.cloneWithType(type)
      FindManager.getInstance(project).findInProjectModel.fileFilter = type
      FindSettings.getInstance().fileMask = type
    }
  }

  fun selectCaseSensitiveAction(selected: Boolean) {
    filterValue = filterValue.cloneWithCase(selected)
    caseSensitiveAction.setSelected(createActionEvent(), selected)
  }

  fun selectWordAction(selected: Boolean) {
    filterValue = filterValue.cloneWithWords(selected)
    wordAction.setSelected(createActionEvent(), selected)
  }

  fun selectRegexpAction(selected: Boolean) {
    filterValue = filterValue.cloneWithRegex(selected)
    regexpAction.setSelected(createActionEvent(), selected)
  }

  private fun createActionEvent(): AnActionEvent {
    return AnActionEvent.createEvent(
      DataContext.EMPTY_CONTEXT,
      Presentation(),
      "SeTextQueryFilterEditor",
      ActionUiKind.NONE,
      null
    )
  }
}