// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.platform.searchEverywhere.SeFilterState
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
interface SeFilterEditor {
  val resultFlow: StateFlow<SeFilterState>

  fun getPresentation(): SeFilterPresentation
}

@ApiStatus.Internal
sealed interface SeFilterPresentation

@ApiStatus.Internal
interface SeFilterActionsPresentation : SeFilterPresentation {
  fun getActions(): List<AnAction>
}

@ApiStatus.Internal
interface SeFilterComponentPresentation : SeFilterPresentation {
  fun getComponent(): JComponent
}
