// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.trialState

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.ui.Messages
import com.intellij.ui.LicensingFacade
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.max

internal object TrialStateUtils {

  fun getExpiresInDays(): Int? {
    val date = LicensingFacade.getInstance()?.expirationDate ?: return null
    return ChronoUnit.DAYS.between(Instant.now(), date.toInstant()).toInt()
  }

  fun getTrialLengthDays(): Int {
    val license = LicensingFacade.getInstance() ?: return 0
    val expirationDate = license.licenseExpirationDate ?: return 0
    val metadata = license.metadata

    if (metadata == null || metadata.length < 20) {
      return 0
    }

    val generationDate = try {
      SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).parse(metadata.substring(2, 10))
    }
    catch (_: ParseException) {
      return 0
    }

    val result = max(ChronoUnit.DAYS.between(generationDate.toInstant(), expirationDate.toInstant()), 0)
    return result.toInt()
  }

  fun showTrialEndedDialog() {
    val answer = Messages.showYesNoDialog(IdeBundle.message("trial.state.trial.ended.dialog.text"),
                                          IdeBundle.message("trial.state.trial.ended.dialog.title"),
                                          IdeBundle.message("trial.state.trial.ended.dialog.add.license"),
                                          IdeBundle.message("trial.state.trial.ended.dialog.restart"),
                                          Messages.getInformationIcon())
    if (answer == Messages.YES) {
      showRegister()
    }
    else {
      ApplicationManagerEx.getApplicationEx().restart(true)
    }
  }

  fun openTrailStateTab() {
    // todo implement
    println("openTrailStateTab not implemented")
  }

  fun showRegister() {
    val actionManager = ActionManager.getInstance()
    val registerAction = actionManager.getAction("Register") ?: return
    ActionUtil.performActionDumbAwareWithCallbacks(registerAction,
                                                   AnActionEvent.createEvent(SimpleDataContext.builder().build(), null, "",
                                                                             ActionUiKind.NONE, null))
  }
}
