// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.customization

import com.intellij.ide.Region
import com.intellij.ide.RegionSettings
import com.intellij.l10n.LocalizationStateService
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.updateSettings.impl.PatchInfo
import com.intellij.openapi.updateSettings.impl.UpdateRequestParametersProvider
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.platform.ide.customization.FeedbackReporter
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus

/**
 * A base class for implementations of [ExternalProductResourceUrls] describing IDEs developed by JetBrains.
 */
abstract class BaseJetBrainsExternalProductResourceUrls : ExternalProductResourceUrls {
  abstract val basePatchDownloadUrl: Url

  /**
   * Returns ID of a YouTrack project which will be used by the "Submit a Bug Report" action.
   */
  abstract val youtrackProjectId: String

  /**
   * Returns the product name in the form shown at the "intellij-support.jetbrains.com" site and "jetbrains.com/feedback/feedback.jsp" page.
   */
  abstract val shortProductNameUsedInForms: String?

  /**
   * Returns URL of the product page on the "jetbrains.com" site.
   * It's used to compute URLs of the following resources:
   * * [productPageUrl]/download to get the address of the download page;
   * * [productPageUrl]/whatsnew to get the address of "What's New" page.
   */
  abstract val productPageUrl: Url

  /**
   * Returns base URL of context help pages.
   * The current IDE version number and ID of the requested topic are added to it to get the actual URL:
   * [baseWebHelpUrl]`/<version>/?<topicId>`.
   */
  abstract val baseWebHelpUrl: Url?

  /**
   * Returns base URL for JetBrains website. Takes into account the user-selected region and locale.
   * When localized pages are not present on the website, it takes care of redirecting to their English equivalents,
   * so the IDE doesn't need to worry about that
   */
  val baseWebSiteUrl: Url
    get() {

      //China mainland has its own website, on which the default locale is zh; other regions use jetbrains.com with default en
      val defaultLocales = mapOf(Region.CHINA to "zh")

      //All language URLs currently supported by the website
      val langToUrl = mapOf(
        "en" to "en-us",
        "zh" to "zh-cn",
        "ja" to "ja-jp",
        "ko" to "ko-kr",
        "es" to "es-es",
        "pt" to "pt-br",
        "fr" to "fr-fr",
        "de" to "de-de"
      )

      val currentRegion = RegionSettings.getRegion()
      val selectedLocale = LocalizationStateService.getInstance()?.selectedLocale

      return Urls.newFromEncoded(
        buildString {
          append("https://www.jetbrains.com")
          if (currentRegion == Region.CHINA)
            append(".cn")
          if (selectedLocale != defaultLocales.getOrDefault(currentRegion, "en")) {
            val locale = langToUrl[selectedLocale]
            /**
             * In case the locale is not supported by the website, direct to its default language.
             * For jetbrains.com it will be English, and for jetbrains.com.cn — Chinese.
             **/
            if (locale != null)
              append("/$locale")
          }
        })
    }

  /**
   * Returns ID of the form used to contact support at the "intellij-support.jetbrains.com" site.
   */
  open val intellijSupportFormId: Int
    get() = 66731

  /**
   * Use an in-product form for "Help | Submit Feedback...".
   */
  open val useInIdeGeneralFeedback: Boolean
    get() = false

  /**
   * Use an in-product form for evaluation feedback.
   */
  open val useInIdeEvaluationFeedback: Boolean
    get() = false

  override val updateMetadataUrl: Url
    get() = System.getProperty("idea.updates.url", "https://www.jetbrains.com/updates/updates.xml")
      .let { Urls.newFromEncoded(it) }
      .let { UpdateRequestParametersProvider.passUpdateParameters(it) }

  final override fun computePatchUrl(from: BuildNumber, to: BuildNumber): Url =
    computeCustomPatchDownloadUrl(from, to)
    ?: basePatchDownloadUrl.resolve(computePatchFileName(from, to))

  override val bugReportUrl: ((String) -> Url)?
    get() = { description ->
      Urls.newFromEncoded("https://youtrack.jetbrains.com/newissue").addParameters(mapOf(
        "project" to youtrackProjectId,
        "clearDraft" to "true",
        "description" to description,
        "c" to "Affected versions: ${ApplicationInfo.getInstance().fullVersion} (${ApplicationInfo.getInstance().getBuild().withoutProductCode()})"
      ))
    }

  override val technicalSupportUrl: ((description: String) -> Url)?
    get() = { _ ->
      Urls.newFromEncoded("https://intellij-support.jetbrains.com/hc/en-us/requests/new").addParameters(
        mapOf(
          "ticket_form_id" to intellijSupportFormId.toString(),
          "product" to shortProductNameUsedInForms,
          "build" to ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode(),
          "os" to currentOsNameForIntelliJSupport(),
          "timezone" to System.getProperty("user.timezone")
        ))
    }

  override val feedbackReporter: FeedbackReporter?
    get() = shortProductNameUsedInForms?.let { productName ->
      JetBrainsFeedbackReporter(productName, useInIdeGeneralFeedback, useInIdeEvaluationFeedback)
    }

  override val downloadPageUrl: Url?
    get() = productPageUrl.resolve("download")

  override val whatIsNewPageUrl: Url?
    get() = productPageUrl.resolve("whatsnew")

  override val helpPageUrl: ((topicId: String) -> Url)?
    get() = baseWebHelpUrl?.let { baseUrl ->
      { topicId ->
        baseUrl.resolve("${ApplicationInfo.getInstance().shortVersion}/").addParameters(mapOf(
          topicId to ""
        ))
      }
    }
}

/**
 * Supported values for [intellij-support.jetbrains.com](https://intellij-support.jetbrains.com):
 * * Windows 10+ - `win-10[-64]`
 * * Windows 8 - `win-8[-64]`
 * * Windows 7- - `win-7[-64]`
 * * macOS - `mac`
 * * Linux - `linux`
 * * Other - `other-os`
 */
@ApiStatus.Internal
fun currentOsNameForIntelliJSupport(): String = when (OS.CURRENT) {
  OS.Windows -> {
    "win-" +
    (if (OS.CURRENT.isAtLeast(10, 0)) "-10" else "-8") +
    (if (CpuArch.CURRENT.width == 64) "-64" else "")
  }
  OS.macOS -> "mac"
  OS.Linux -> "linux"
  else -> "other-os"
}

internal fun computePatchFileName(from: BuildNumber, to: BuildNumber): String {
  val product = ApplicationInfo.getInstance().build.productCode
  val runtime = if (CpuArch.isArm64()) "-aarch64" else ""
  return "${product}-${from.withoutProductCode().asString()}-${to.withoutProductCode().asString()}-patch${runtime}-${PatchInfo.OS_SUFFIX}.jar"
}

internal fun computeCustomPatchDownloadUrl(from: BuildNumber, to: BuildNumber): Url? {
  val customPatchesUrl = System.getProperty("idea.patches.url") ?: return null
  return Urls.newFromEncoded(customPatchesUrl).resolve(computePatchFileName(from, to))
}
