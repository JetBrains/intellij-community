// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.addBoolIfDiffers
import com.intellij.internal.statistic.beans.newBooleanMetric
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.toLowerCase
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.vcs.commit.NonModalCommitCustomization.Companion.isNonModalCustomizationApplied

private const val COUNTER_GROUP = "vcs"
private val appSettings get() = VcsApplicationSettings.getInstance()

internal object NonModalCommitUsagesCollector {
  fun getMetrics(): Set<MetricEvent> {
    val defaultSettings = VcsApplicationSettings()

    return mutableSetOf<MetricEvent>().apply {
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.COMMIT_FROM_LOCAL_CHANGES }, "non.modal.commit")
      if (isNonModalCustomizationApplied()) add(newBooleanMetric("non.modal.commit.new.installation", true))
      NonModalCommitPromoter.getPromotionState()?.let { add(newMetric("non.modal.commit.promotion", it)) }
    }
  }

  fun logStateChanged(project: Project?) {
    val data = FeatureUsageData().addEnabled(appSettings.COMMIT_FROM_LOCAL_CHANGES)
    FUCounterUsageLogger.getInstance().logEvent(project, COUNTER_GROUP, "non.modal.commit.state.changed", data)
  }

  fun logPromotionEvent(project: Project, state: NonModalCommitPromotionState) =
    FUCounterUsageLogger.getInstance().logEvent(project, COUNTER_GROUP, state.toEventId())
}

private fun NonModalCommitPromotionState.toEventId(): String = "non.modal.commit.promotion.${toLowerCase(name)}" // NON-NLS