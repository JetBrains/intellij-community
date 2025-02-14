// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.trialPromotion

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.trialPromotion.TrialStateService.TrialProgressData
import com.intellij.platform.trialPromotion.TrialStateService.TrialState
import com.intellij.platform.trialPromotion.vision.TrialTabVisionContent
import com.intellij.platform.trialPromotion.vision.TrialTabVisionContentProvider
import kotlinx.serialization.Serializable

internal abstract class TrialTabContent {
  abstract suspend fun isAvailable(): Boolean
  abstract suspend fun show(project: Project, dataContext: DataContext?, trialProgressData: TrialProgressData)

  companion object {
    suspend fun getContentMap(): Map<TrialPageKind, TrialTabContent>? {
      var provider: TrialTabVisionContentProvider? = TrialTabVisionContentProvider.getInstance()
      provider = provider?.takeIf { it.isAvailable() }
      return provider?.getContentMap()?.mapValues { (_, container) ->
        val page = container.entities.first()
        TrialTabVisionContent(page)
      }
    }
  }
}

// TODO: rename?
@Serializable
enum class TrialPageKind {
  TRIAL_STARTED,
  TRIAL_ACTIVE,
  TRIAL_ENDED,
  ;

  companion object {
    internal fun fromTrialState(state: TrialState): TrialPageKind = when (state) {
      TrialState.TRIAL_STARTED -> TRIAL_STARTED
      TrialState.ACTIVE, TrialState.ALERT, TrialState.EXPIRING -> TRIAL_ACTIVE
      TrialState.GRACE, TrialState.GRACE_ENDED -> TRIAL_ENDED
    }
  }
}
