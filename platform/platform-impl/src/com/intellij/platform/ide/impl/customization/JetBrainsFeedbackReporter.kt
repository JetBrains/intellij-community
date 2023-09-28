// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.customization

import com.intellij.ide.feedback.FeedbackForm
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.customization.FeedbackReporter
import com.intellij.ui.LicensingFacade
import com.intellij.util.Url
import com.intellij.util.Urls
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class JetBrainsFeedbackReporter(private val productName: String,
                                private val zenDeskFormData: ZenDeskFeedbackFormData?) : FeedbackReporter {
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
    if (Registry.`is`("ide.in.product.feedback") && zenDeskFormData != null) {
      FeedbackForm(project, zenDeskFormData, requestedForEvaluation).show()
      return true
    }
    return false
  }
}

/**
 * Provides information about ZenDesk form used to send feedback.
 */
interface ZenDeskFeedbackFormData {
  val formUrl: String
  val formId: Long
  val productId: String
  val fieldIds: ZenDeskFeedbackFormFieldIds
}

/**
 * IDs of elements in ZenDesk feedback form. 
 * They are used in JSON sent to zendesk.com, see [ZenDeskRequests][com.intellij.ide.feedback.ZenDeskRequests] for implementation details.
 */
interface ZenDeskFeedbackFormFieldIds {
  val product: Long
  val country: Long
  val rating: Long
  val build: Long
  val os: Long
  val timezone: Long
  val eval: Long
  val systemInfo: Long
  val needSupport: Long
  val topic: Long
}