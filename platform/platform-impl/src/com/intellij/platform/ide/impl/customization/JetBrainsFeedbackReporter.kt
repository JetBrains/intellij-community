// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.customization

import com.intellij.feedback.AbstractInIdeEvaluationFeedbackProvider
import com.intellij.feedback.AbstractInIdeGeneralFeedbackProvider
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.customization.FeedbackReporter
import com.intellij.ui.LicensingFacade
import com.intellij.util.Url
import com.intellij.util.Urls
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class JetBrainsFeedbackReporter(private val productName: String,
                                private val useInIdeFeedback: Boolean) : FeedbackReporter {
  override val destinationDescription: String
    get() = "jetbrains.com"

  override fun feedbackFormUrl(description: String): Url {
    val appInfo = ApplicationInfo.getInstance()
    val build = appInfo.getBuild()

    return Urls.newFromEncoded("https://www.jetbrains.com/feedback/feedback.jsp")
      .addParameters(mapOf(
        "product" to productName,
        "build" to if (appInfo.isEAP) build.asStringWithoutProductCode() else build.asString(),
        "timezone" to System.getProperty("user.timezone", ""),
        "eval" to (LicensingFacade.getInstance()?.isEvaluationLicense == true).toString(),
      ))
  }

  override fun showFeedbackForm(project: Project?, requestedForEvaluation: Boolean): Boolean {
    if (useInIdeFeedback) {
      if (requestedForEvaluation) {
        val evaluationFeedbackProvider = AbstractInIdeEvaluationFeedbackProvider.getInstance()
        val evaluationFeedbackDialog = evaluationFeedbackProvider.getEvaluationFeedbackDialog(project, false)
        evaluationFeedbackDialog.show()
        return true
      }

      val generalFeedbackProvider = AbstractInIdeGeneralFeedbackProvider.getInstance()
      val generalFeedbackDialog = generalFeedbackProvider.getGeneralFeedbackDialog(project, false)
      generalFeedbackDialog.show()
      return true
    }

    return false
  }
}