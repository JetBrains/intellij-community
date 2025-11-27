// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend.searchEverywhere

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfo
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfoProvider
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.frontend.tabs.SeDefaultTabBase
import com.intellij.platform.searchEverywhere.frontend.tabs.target.SeTargetsFilterEditor
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.suspendLazy
import com.intellij.vcs.git.SeGitProviderIdUtils
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeGitTab(delegate: SeTabDelegate) : SeDefaultTabBase(delegate) {
  override val name: String get() = GitBundle.message("search.everywhere.group.name")
  override val id: String get() = ID
  override val priority: Int get() = 750
  private val filterEditor: SuspendLazyProperty<SeFilterEditor> = suspendLazy { SeTargetsFilterEditor(null, delegate.getTypeVisibilityStates(), false) }

  override suspend fun getFilterEditor(): SeFilterEditor {
    return filterEditor.getValue()
  }

  override suspend fun getEmptyResultInfo(context: DataContext): SeEmptyResultInfo {
    return SeEmptyResultInfoProvider(getFilterEditor(),
                                     delegate.getProvidersIds(),
                                     delegate.canBeShownInFindResults()).getEmptyResultInfo(delegate.project, context)
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return false
  }

  companion object {
    @ApiStatus.Internal
    const val ID: String = SeGitProviderIdUtils.GIT_OBJECTS_ID
  }
}
