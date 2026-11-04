// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.BrowserUtil
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.svg.getSvgDocumentSize
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

private val LOG = logger<MigrationOfferContent>()

private const val MAX_CONTENT_SIZE = 512 * 1024

/**
 * The result of a platform update check, split by the UI that shows it.
 * At most one of the two values is not `null`.
 */
@ApiStatus.Internal
class PlatformUpdateDecision(
  /** The usual update: the widget, the settings menu, the notification, and [PlatformUpdateDialog]. */
  val announced: PlatformUpdates.Loaded?,
  /** The migration to another product: only the migration dialog shows it. */
  val migration: PlatformUpdates.Loaded?,
)

/** A background check gives no update while "Remind Me Later" is active. */
@ApiStatus.Internal
fun decidePlatformUpdate(updates: PlatformUpdates, userInitiated: Boolean, remindLaterActive: Boolean): PlatformUpdateDecision {
  val loaded = when (updates) {
    is PlatformUpdates.Loaded -> updates
    PlatformUpdates.Empty, is PlatformUpdates.ConnectionError -> null
  }
  val isShown = userInitiated || !remindLaterActive
  val announced = loaded?.takeIf { isShown && it.newBuild.distribution == null }
  val migration = loaded?.takeIf { isShown && it.newBuild.distribution != null }
  return PlatformUpdateDecision(announced, migration)
}

/** The SVG data of the light and the dark variant of one image. */
@ApiStatus.Internal
class MigrationImageData(val light: ByteArray, val dark: ByteArray)

/** Everything that the migration dialog shows. The dialog loads nothing. */
internal class MigrationOfferContent(
  val update: PlatformUpdates.Loaded,
  val distribution: DistributionInfo,
  val offer: MigrationOfferTexts,
  val sourceLogo: MigrationImageData,
  val targetLogo: MigrationImageData,
  val background: MigrationImageData?,
)

internal sealed interface MigrationOfferResult {
  class Loaded(val content: MigrationOfferContent) : MigrationOfferResult

  /** [error] is the download failure. It is `null` when the content is not valid. */
  class Failed(val error: IOException?) : MigrationOfferResult
}

/**
 * Loads the content manifest of the migration and all its images.
 * The dialog shows only when everything loads and is valid.
 * A failure gives [MigrationOfferResult.Failed], not an exception, so the update check always completes.
 */
@RequiresBackgroundThread
internal fun loadMigrationOffer(update: PlatformUpdates.Loaded, indicator: ProgressIndicator?): MigrationOfferResult {
  return try {
    doLoadMigrationOffer(update, indicator)
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    LOG.warn("the migration offer is not shown: unexpected error", e)
    MigrationOfferResult.Failed(error = null)
  }
}

private fun doLoadMigrationOffer(update: PlatformUpdates.Loaded, indicator: ProgressIndicator?): MigrationOfferResult {
  val distribution = update.newBuild.distribution ?: return failed("no <distribution> in ${update.newBuild}", error = null)
  val manifestUrl = distribution.manifestUrl
  if (!isAllowedContentUrl(manifestUrl)) {
    return failed("the manifest URL is not allowed: ${manifestUrl}", error = null)
  }

  val manifest = try {
    val text = download(manifestUrl, indicator).toString(Charsets.UTF_8)
    when (val result = parseMigrationManifest(text, LocalizationUtil.getLocale())) {
      is MigrationManifestParseResult.Valid -> result
      is MigrationManifestParseResult.Invalid -> return failed("invalid manifest ${manifestUrl}: ${result.reason}", error = null)
    }
  }
  catch (e: IOException) {
    return failed("cannot load ${manifestUrl}: ${e.message}", e)
  }
  catch (e: InvalidContentException) {
    return failed("invalid manifest ${manifestUrl}: ${e.message}", error = null)
  }

  val images = manifest.images
  return try {
    MigrationOfferResult.Loaded(MigrationOfferContent(
      update = update,
      distribution = distribution,
      offer = manifest.offer,
      sourceLogo = loadImage(images.sourceLogo, indicator),
      targetLogo = loadImage(images.targetLogo, indicator),
      background = images.background?.let { loadImage(it, indicator) },
    ))
  }
  catch (e: IOException) {
    failed("cannot load an image of ${manifestUrl}: ${e.message}", e)
  }
  catch (e: InvalidContentException) {
    failed("invalid image of ${manifestUrl}: ${e.message}", error = null)
  }
}

/** The action of the primary button of the migration dialog. It opens the download page in the browser. */
@RequiresEdt
internal fun startMigrationUpdate(project: Project?, content: MigrationOfferContent) {
  val update = content.update
  BrowserUtil.browse(downloadUrl(update.newBuild, update.updatedChannel), project)
}

private var isMigrationOfferShown = false

/**
 * Shows the migration dialog. It does nothing while the dialog is already open.
 * A failure is only logged, so that the update check shows its other results.
 */
@RequiresEdt
internal fun showMigrationOffer(project: Project?, content: MigrationOfferContent) {
  if (isMigrationOfferShown) {
    return
  }
  isMigrationOfferShown = true
  try {
    MigrationOfferDialog(project, content).show()
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    LOG.warn("cannot show the migration offer", e)
  }
  finally {
    isMigrationOfferShown = false
  }
}

private fun failed(reason: String, error: IOException?): MigrationOfferResult.Failed {
  LOG.info("the migration offer is not shown: ${reason}")
  return MigrationOfferResult.Failed(error)
}

private fun loadImage(image: MigrationImage, indicator: ProgressIndicator?): MigrationImageData =
  MigrationImageData(light = loadSvg(image.light, indicator), dark = loadSvg(image.dark, indicator))

private fun loadSvg(url: String, indicator: ProgressIndicator?): ByteArray {
  val data = download(url, indicator)
  try {
    getSvgDocumentSize(data)
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    throw InvalidContentException("${url} is not an SVG image: ${e.message}")
  }
  return data
}

private fun download(url: String, indicator: ProgressIndicator?): ByteArray {
  indicator?.checkCanceled()
  val data = HttpRequests.request(url)
    .productNameAsUserAgent()
    .connect { it.inputStream.readNBytes(MAX_CONTENT_SIZE + 1) }
  if (data.size > MAX_CONTENT_SIZE) {
    throw InvalidContentException("${url} is larger than ${MAX_CONTENT_SIZE} bytes")
  }
  return data
}

private class InvalidContentException(message: String) : Exception(message)
