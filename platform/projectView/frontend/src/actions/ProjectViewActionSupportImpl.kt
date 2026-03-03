// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.actions

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.platform.projectView.actions.FileNestingState
import com.intellij.platform.projectView.actions.NestingRuleState
import com.intellij.platform.projectView.actions.ProjectViewActionSupport
import com.intellij.platform.projectView.actions.ProjectViewOption
import com.intellij.platform.projectView.actions.ProjectViewOptionState
import com.intellij.platform.projectView.actions.ProjectViewSortKeyState
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import kotlinx.coroutines.flow.MutableStateFlow

internal class ProjectViewActionSupportImpl(private val currentPane: MutableStateFlow<FrontendProjectViewPane?>) :
    ProjectViewActionSupport {
  override fun getOptionState(option: ProjectViewOption): ProjectViewOptionState? = currentPane.value?.getOptionSupport()?.getOptionState(option)

  override fun getSortKeyState(): ProjectViewSortKeyState? = currentPane.value?.getOptionSupport()?.getSortKeyState()

  override fun getFileNestingState(): FileNestingState? = currentPane.value?.getOptionSupport()?.getFileNestingState()

  override fun requestOptionValueUpdate(option: ProjectViewOption, newValue: Boolean) {
    currentPane.value?.getOptionSupport()?.requestOptionValueUpdate(option, newValue)
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
