// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.platform.projectView.frontend.actions

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.platform.projectView.actions.NestingRuleState
import com.intellij.platform.projectView.actions.ProjectViewActionState
import com.intellij.platform.projectView.actions.ProjectViewActionSupport
import com.intellij.platform.projectView.actions.ProjectViewOption
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

internal class ProjectViewActionSupportImpl(
  private val currentPane: MutableStateFlow<FrontendProjectViewPane?>
) : ProjectViewActionSupport {
  override fun getActionState(): ProjectViewActionState? = currentPane.value?.getOptionSupport()?.getActionState()

  override fun getActionStateFlow(): Flow<ProjectViewActionState?> = currentPane.flatMapLatest {
    it?.getOptionSupport()?.getActionStateFlow() ?: emptyFlow()
  }

  override fun requestOptionValueChange(option: ProjectViewOption, newValue: Boolean) {
    currentPane.value?.getOptionSupport()?.requestOptionValueChange(option, newValue)
  }

  override fun requestSortKeyChange(sortKey: NodeSortKey) {
    currentPane.value?.getOptionSupport()?.requestSortKeyChange(sortKey)
  }

  override fun requestFileNestingChange(
      fileNestingOn: Boolean,
      activeRules: List<NestingRuleState>,
  ) {
    currentPane.value?.getOptionSupport()?.requestFileNestingChange(fileNestingOn, activeRules)
  }
}
