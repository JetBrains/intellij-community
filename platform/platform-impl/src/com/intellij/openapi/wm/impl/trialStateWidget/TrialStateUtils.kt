// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.trialStateWidget

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.LicensingFacade
import java.time.Instant
import java.time.temporal.ChronoUnit

internal object TrialStateUtils {

  private const val TRIAL_STATE_TRIAL_STARTED_MS_KEY = "trial.state.trial.started.ms"

  /**
   * If new trial available was shown contains when it was shown, 0 otherwise
   */
  var trialStartTime: Instant?
    get() {
      val value = PropertiesComponent.getInstance().getLong(TRIAL_STATE_TRIAL_STARTED_MS_KEY, 0)
      return if (value == 0L) null else Instant.ofEpochSecond(value)
    }
    set(value) {
      if (value == null || value.epochSecond == 0L) {
        PropertiesComponent.getInstance().unsetValue(TRIAL_STATE_TRIAL_STARTED_MS_KEY)
      }
      else {
        PropertiesComponent.getInstance().setValue(TRIAL_STATE_TRIAL_STARTED_MS_KEY, value.epochSecond.toString())
      }
    }


  fun getExpiresInDays(): Int? {
    val date = LicensingFacade.getInstance()?.expirationDate ?: return null
    return ChronoUnit.DAYS.between(Instant.now(), date.toInstant()).toInt()
  }

  fun getTrialLengthDays(): Int {
    // todo implement
    return 31
  }

  fun openTrailStateTab() {
    // todo implement
    println("openTrailStateTab not implemented")
  }
}
