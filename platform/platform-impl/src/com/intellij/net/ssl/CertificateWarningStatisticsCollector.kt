// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.net.ssl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object CertificateWarningStatisticsCollector: CounterUsagesCollector() {
  private val certificateWarningGroup = EventLogGroup("certificate.warning.info", 2)

  private val certificateAccepted = certificateWarningGroup.registerEvent("certificate_accepted")
  private val certificateRejected = certificateWarningGroup.registerEvent("certificate_rejected")
  
  private val usingShowButton = EventFields.Boolean("using_show_button")
  private val detailsShown = certificateWarningGroup.registerVarargEvent("details_shown", usingShowButton)
  
  override fun getGroup(): EventLogGroup {
    return certificateWarningGroup
  }
  
  fun certificateAccepted() {
    certificateAccepted.log()
  }
  
  fun certificateRejected() {
    certificateRejected.log()
  }
  
  fun detailsShown(usingShowButton: Boolean) {
    detailsShown.log(this.usingShowButton.with(usingShowButton))
  }
}