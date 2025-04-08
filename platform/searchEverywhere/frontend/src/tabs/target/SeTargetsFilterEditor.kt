// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.target

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.platform.searchEverywhere.SeSearchScopesInfo
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.providers.files.SeTargetsFilter
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeTargetsFilterEditor(scopesInfo: SeSearchScopesInfo) : SeFilterEditor<SeTargetsFilter>(
  SeTargetsFilter(scopesInfo.selectedScopeId)
) {
  private val chooseScopeActionProvider = SeScopeChooserActionProvider(scopesInfo) {
    filterValue = SeTargetsFilter(it)
  }

  override fun getActions(): List<AnAction> = listOf(chooseScopeActionProvider.getAction())
}