// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.actions

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFeature
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeNewSearchToggleAction : ToggleAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean =
    Registry.`is`(SearchEverywhereFeature.registryKey)

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    Registry.get(SearchEverywhereFeature.registryKey).setValue(state)
  }
}