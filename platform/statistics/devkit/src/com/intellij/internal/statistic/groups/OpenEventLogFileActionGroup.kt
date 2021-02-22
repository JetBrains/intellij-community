// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.groups

import com.intellij.internal.statistic.StatisticsDevKitUtil.getLogProvidersInTestMode
import com.intellij.internal.statistic.actions.OpenEventLogFileAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

internal class OpenEventLogFileActionGroup : ActionGroup() {
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return getLogProvidersInTestMode()
      .map { logger ->
        val recorder = logger.recorderId
        OpenEventLogFileAction(recorder)
      }
      .toTypedArray()
  }

  override fun isPopup(): Boolean {
    return getLogProvidersInTestMode().size > 1
  }
}