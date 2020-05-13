// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.internal.statistic.eventLog.fus.FeatureUsageStateEventTracker
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ReportSettingsToFUSAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    for (tracker in FeatureUsageStateEventTracker.EP_NAME.extensions) {
      tracker.reportNow()
    }
  }
}
