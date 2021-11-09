// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.addBoolIfDiffers
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector.Companion.NON_MODAL_COMMIT_PROMOTION_ACCEPTED
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector.Companion.NON_MODAL_COMMIT_PROMOTION_REJECTED
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector.Companion.NON_MODAL_COMMIT_PROMOTION_SHOWN
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector.Companion.NON_MODAL_COMMIT_STATE_CHANGED
import com.intellij.openapi.vcs.statistics.VcsApplicationOptionsUsagesCollector

private val appSettings get() = VcsApplicationSettings.getInstance()

internal object NonModalCommitUsagesCollector {
  fun getMetrics(): Set<MetricEvent> {
    val defaultSettings = VcsApplicationSettings()

    return mutableSetOf<MetricEvent>().apply {
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.COMMIT_FROM_LOCAL_CHANGES },
                       VcsApplicationOptionsUsagesCollector.NON_MODEL_COMMIT)
      if (NonModalCommitCustomization.isNonModalCustomizationApplied()) add(
        VcsApplicationOptionsUsagesCollector.NON_MODEL_COMMIT_NEW_INSTALLATION.metric(true))
      NonModalCommitPromoter.getPromotionState()?.let { add(VcsApplicationOptionsUsagesCollector.NON_MODEL_COMMIT_PROMOTION.metric(it)) }
    }
  }

  fun logStateChanged(project: Project?) = NON_MODAL_COMMIT_STATE_CHANGED.log(project, appSettings.COMMIT_FROM_LOCAL_CHANGES)

  fun logPromotionEvent(project: Project, state: NonModalCommitPromotionState) = state.toEventId().log(project)
}

private fun NonModalCommitPromotionState.toEventId(): EventId {
  return when (this) {
    NonModalCommitPromotionState.SHOWN -> NON_MODAL_COMMIT_PROMOTION_SHOWN
    NonModalCommitPromotionState.ACCEPTED -> NON_MODAL_COMMIT_PROMOTION_ACCEPTED
    NonModalCommitPromotionState.REJECTED -> NON_MODAL_COMMIT_PROMOTION_REJECTED
  }
}