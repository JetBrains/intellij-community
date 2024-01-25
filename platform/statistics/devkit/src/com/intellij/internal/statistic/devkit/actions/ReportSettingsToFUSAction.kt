// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions

import com.intellij.internal.statistic.eventLog.fus.FeatureUsageStateEventTracker
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.launch

private class ReportSettingsToFUSAction : DumbAwareAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    (ApplicationManager.getApplication() as ComponentManagerEx).getCoroutineScope().launch {
      for (tracker in FeatureUsageStateEventTracker.EP_NAME.extensionList) {
        tracker.reportNow()
      }
    }
  }
}
