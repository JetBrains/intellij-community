// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.frontend.window

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.platform.projectView.actions.ProjectViewOption
import com.intellij.platform.projectView.actions.ProjectViewOptionState
import com.intellij.platform.projectView.actions.ProjectViewSortKeyState
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.window.ProjectViewOptionSupport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal class ProjectViewOptionSupportImpl(private val currentPane: MutableStateFlow<FrontendProjectViewPane?>) : ProjectViewOptionSupport {
  override fun getOptionState(option: ProjectViewOption): ProjectViewOptionState? = currentPane.value?.getOptionSupport()?.getOptionState(option)

  override fun getSortKeyState(): ProjectViewSortKeyState? = currentPane.value?.getOptionSupport()?.getSortKeyState()

  override fun requestOptionValueUpdate(option: ProjectViewOption, newValue: Boolean) {
    currentPane.value?.getOptionSupport()?.requestOptionValueUpdate(option, newValue)
  }

  override fun requestSortKeyChange(sortKey: NodeSortKey) {
    currentPane.value?.getOptionSupport()?.requestSortKeyChange(sortKey)
  }
}
