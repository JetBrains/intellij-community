// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.customization

import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.platform.ide.customization.FeedbackReporter
import com.intellij.ui.LicensingFacade
import com.intellij.util.Url
import com.intellij.util.Urls

internal class JetBrainsFeedbackReporter(private val productName: String) : FeedbackReporter {
  override val destinationDescription: String
    get() = "jetbrains.com"

  override fun feedbackFormUrl(description: String): Url {
    val appInfo = ApplicationInfoEx.getInstanceEx()
    val build = appInfo.getBuild()

    return Urls.newFromEncoded("https://www.jetbrains.com/feedback/feedback.jsp")
      .addParameters(mapOf(
        "product" to productName,
        "build" to if (appInfo.isEAP()) build.asStringWithoutProductCode() else build.asString(),
        "timezone" to System.getProperty("user.timezone", ""),
        "eval" to (LicensingFacade.getInstance()?.isEvaluationLicense == true).toString(),
      ))
  }
}
