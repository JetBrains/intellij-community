// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.groups

import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil.getLogProvidersInTestMode
import com.intellij.internal.statistic.devkit.actions.UpdateEventsSchemeAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

internal class UpdateEventsSchemeActionGroup : ActionGroup() {
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return getLogProvidersInTestMode()
      .map { logger ->
        UpdateEventsSchemeAction(logger.recorderId)
      }
      .toTypedArray()
  }

  override fun isPopup(): Boolean {
    return getLogProvidersInTestMode().size > 1
  }
}