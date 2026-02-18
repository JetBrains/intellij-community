// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package com.intellij.platform.projectView.frontend.window

import com.intellij.platform.projectView.actions.ProjectViewOption
import com.intellij.platform.projectView.actions.ProjectViewOptionState
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.window.ProjectViewOptionSupport
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal class ProjectViewOptionSupportImpl(private val currentPane: AtomicReference<FrontendProjectViewPane?>) : ProjectViewOptionSupport {
  override fun getOptionState(option: ProjectViewOption): ProjectViewOptionState? = currentPane.load()?.getOptionSupport()?.getOptionState(option)

  override fun requestOptionValueUpdate(option: ProjectViewOption, newValue: Boolean) {
    currentPane.load()?.getOptionSupport()?.requestOptionValueUpdate(option, newValue)
  }
}
