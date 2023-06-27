// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.addBoolIfDiffers
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector.Companion.NON_MODAL_COMMIT_STATE_CHANGED
import com.intellij.openapi.vcs.statistics.VcsApplicationOptionsUsagesCollector

private val appSettings get() = VcsApplicationSettings.getInstance()

internal object NonModalCommitUsagesCollector {
  fun getMetrics(): Set<MetricEvent> {
    val defaultSettings = VcsApplicationSettings()

    return mutableSetOf<MetricEvent>().apply {
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.COMMIT_FROM_LOCAL_CHANGES },
                       VcsApplicationOptionsUsagesCollector.NON_MODAL_COMMIT)
    }
  }

  fun logStateChanged(project: Project?) = NON_MODAL_COMMIT_STATE_CHANGED.log(project, appSettings.COMMIT_FROM_LOCAL_CHANGES)
}
