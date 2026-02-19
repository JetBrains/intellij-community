// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.actions

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFeature
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareToggleAction
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeNewSearchToggleAction : DumbAwareToggleAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = SearchEverywhereFeature.isSplit

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    SearchEverywhereFeature.isSplit = state
    SearchEverywhereUsageTriggerCollector.SPLIT_SWITCHED.log(state)
  }
}