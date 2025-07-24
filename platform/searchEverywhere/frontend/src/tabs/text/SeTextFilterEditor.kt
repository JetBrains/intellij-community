// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.text

import com.intellij.find.FindManager
import com.intellij.find.FindSettings
import com.intellij.find.impl.JComboboxAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.Project
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.frontend.tabs.target.SeScopeChooserActionProvider
import com.intellij.platform.searchEverywhere.frontend.tabs.utils.SeFilterEditorBase
import com.intellij.platform.searchEverywhere.providers.SeTextFilter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTextFilterEditor(
  private val project: Project?,
  private val scopesInfo: SearchScopesInfo?,
  private val disposable: Disposable
) : SeFilterEditorBase<SeTextFilter>(
  SeTextFilter(scopesInfo?.selectedScopeId, null)
) {
  private val scopeFilterAction: AnAction? = scopesInfo?.let {
    SeScopeChooserActionProvider(scopesInfo) {
      filterValue = filterValue.cloneWithScope(it)
    }.getAction()
  }

  private val typesFilterAction: JComboboxAction? = project?.let {
    JComboboxAction(project) { filterValue = filterValue.cloneWithType(it) }.also {
      disposable.whenDisposed { it.saveMask() }
    }
  }

  fun changeType(type: String?) {
    typesFilterAction?.let {
      filterValue = filterValue.cloneWithType(type)
      FindManager.getInstance(project).findInProjectModel.fileFilter = type
      FindSettings.getInstance().fileMask = type
    }
  }

  private fun getScopeFilterAction(): AnAction? {
    return scopeFilterAction
  }

  private fun getTypeFilterAction(): AnAction? {
    return typesFilterAction
  }

  override fun getActions(): List<AnAction> = listOfNotNull(getScopeFilterAction(), getTypeFilterAction())
}