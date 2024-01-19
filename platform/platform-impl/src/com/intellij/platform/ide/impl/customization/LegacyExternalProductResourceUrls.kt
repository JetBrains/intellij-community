// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.customization

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.updateSettings.impl.UpdateRequestParameters
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.platform.ide.customization.FeedbackReporter
import com.intellij.ui.LicensingFacade
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.io.URLUtil
import java.util.regex.Pattern

class LegacyExternalProductResourceUrls : ExternalProductResourceUrls {
  override val updateMetadataUrl: Url?
    get() {
      val customUrl = System.getProperty("idea.updates.url")
      if (customUrl != null) {
        return Urls.newFromEncoded(customUrl)
      }
      val baseUrl = ApplicationInfoEx.getInstanceEx().updateUrls?.checkingUrl ?: return null
      return UpdateRequestParameters.amendUpdateRequest(Urls.newFromEncoded(baseUrl))
    }

  override fun computePatchUrl(from: BuildNumber, to: BuildNumber): Url? {
    val customUrl = computeCustomPatchDownloadUrl(from, to)
    if (customUrl != null) {
      return customUrl
    }
    val baseUrl = ApplicationInfoEx.getInstanceEx().updateUrls?.patchesUrl ?: return null
    return Urls.newFromEncoded(baseUrl).resolve(computePatchFileName(from, to))
  }

  override val bugReportUrl: ((String) -> Url)?
    get() {
      val youtrackUrl = ApplicationInfoEx.getInstanceEx().youtrackUrl ?: return null
      return { description -> Urls.newFromEncoded(youtrackUrl.replace("\$DESCR", URLUtil.encodeURIComponent(description))) }
    }

  override val technicalSupportUrl: ((description: String) -> Url)?
    get() {
      val urlTemplate = ApplicationInfoEx.getInstanceEx().supportUrl ?: return null
      return { _ ->
        val url = urlTemplate
          .replace("\$BUILD", ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode())
          .replace("\$OS", currentOsNameForIntelliJSupport())
          .replace("\$TIMEZONE", System.getProperty("user.timezone"))
        Urls.newFromEncoded(url) 
      }
    }

  override val feedbackReporter: FeedbackReporter?
    get() {
      val urlTemplate = ApplicationInfoEx.getInstanceEx().feedbackUrl ?: return null
      return object : FeedbackReporter {
        override val destinationDescription: String
          get() {
            val uriPattern = Pattern.compile("[^:/?#]+://(?:www\\.)?([^/?#]*).*", Pattern.DOTALL)
            val matcher = uriPattern.matcher(urlTemplate)
            return if (matcher.matches()) matcher.group(1) else ApplicationInfo.getInstance().companyName
          }

        override fun feedbackFormUrl(description: String): Url {
          val appInfo = ApplicationInfoEx.getInstanceEx()
          val build = appInfo.getBuild()
          val url = urlTemplate
            .replace("\$BUILD", URLUtil.encodeURIComponent(if (appInfo.isEAP) build.asStringWithoutProductCode() else build.asString()))
            .replace("\$TIMEZONE", URLUtil.encodeURIComponent(System.getProperty("user.timezone", "")))
            .replace("\$VERSION", URLUtil.encodeURIComponent(appInfo.getFullVersion()))
            .replace("\$EVAL", URLUtil.encodeURIComponent((LicensingFacade.getInstance()?.isEvaluationLicense == true).toString()))
            .replace("\$DESCR", URLUtil.encodeURIComponent(description))
          return Urls.newFromEncoded(url)
        }
      }
    }

  override val downloadPageUrl: Url?
    get() = ApplicationInfoEx.getInstanceEx().downloadUrl?.let { Urls.newFromEncoded(it) }

  override val youTubeChannelUrl: Url?
    get() = ApplicationInfoEx.getInstanceEx().jetBrainsTvUrl?.let { Urls.newFromEncoded(it) }

  override val keyboardShortcutsPdfUrl: Url?
    get() {
      val appInfo = ApplicationInfoEx.getInstanceEx()
      val url = if (SystemInfo.isMac) appInfo.getMacKeymapUrl() else appInfo.getWinKeymapUrl()
      return url?.let { Urls.newFromEncoded(url) }
    }

  override val whatIsNewPageUrl: Url?
    get() = ApplicationInfoEx.getInstanceEx().whatsNewUrl?.let { Urls.newFromEncoded(it) }

  override val gettingStartedPageUrl: Url?
    get() = ApplicationInfoEx.getInstanceEx().documentationUrl?.let { Urls.newFromEncoded(it) }

  override val helpPageUrl: ((topicId: String) -> Url)?
    get() {
      val baseHelpUrl = ApplicationInfoEx.getInstanceEx().webHelpUrl ?: return null 
      return { topicId ->
        Urls.newFromEncoded(baseHelpUrl).resolve("${ApplicationInfo.getInstance().shortVersion}/").addParameters(mapOf(
          topicId to ""
        ))
      }
    }
}