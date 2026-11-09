// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.l10n.LocalizationUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.MigrationImageData
import com.intellij.openapi.application.MigrationState
import com.intellij.openapi.application.MigrationStatus
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.svg.getSvgDocumentSize
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.IOException
import javax.swing.plaf.basic.BasicHTML

private val LOG = logger<MigrationOfferContent>()

private const val MAX_CONTENT_SIZE = 512 * 1024

/**
 * The result of a platform update check, split by the UI that shows it.
 * At most one of the two updates is not `null`.
 */
@ApiStatus.Internal
class PlatformUpdateDecision(
  /** The usual update: the widget, the settings menu, the notification, and [PlatformUpdateDialog]. */
  val announced: PlatformUpdates.Loaded?,
  /** The migration to another product: only the migration dialog shows it. */
  val migration: PlatformUpdates.Loaded?,
  /** A manual check shows "Restart to Update" for the installed migration distribution, and not "no updates". */
  val migrationReady: Boolean,
)

/**
 * A background check gives no update while "Remind Me Later" is active.
 * A migration build gives no offer when [migrationStatus] is not `null`, because the distribution is already installed.
 */
@ApiStatus.Internal
fun decidePlatformUpdate(
  updates: PlatformUpdates,
  userInitiated: Boolean,
  remindLaterActive: Boolean,
  migrationStatus: MigrationStatus?,
): PlatformUpdateDecision {
  val loaded = when (updates) {
    is PlatformUpdates.Loaded -> updates
    PlatformUpdates.Empty, is PlatformUpdates.ConnectionError -> null
  }
  val isShown = userInitiated || !remindLaterActive
  val announced = loaded?.takeIf { isShown && it.newBuild.distribution == null }
  val migration = loaded?.takeIf { isShown && it.newBuild.distribution != null && migrationStatus == null }
  val migrationReady = userInitiated && migrationStatus == MigrationStatus.INSTALLED && announced == null &&
                       updates !is PlatformUpdates.ConnectionError
  return PlatformUpdateDecision(announced, migration, migrationReady)
}

/** Everything that the migration dialog shows. The dialog loads nothing. */
internal class MigrationOfferContent(
  val update: PlatformUpdates.Loaded,
  val distribution: DistributionInfo,
  val offer: MigrationOfferTexts,
  val ready: MigrationReadyTexts?,
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
      ready = manifest.ready,
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

private var isMigrationInstallRunning = false

/**
 * The action of the primary button of the migration dialog.
 * It installs the distribution in the background, then shows "Restart to Update".
 * It does nothing while the install runs, or when a valid migration state exists.
 */
@RequiresEdt
internal fun startMigrationUpdate(project: Project?, content: MigrationOfferContent) {
  val state = migrationStateStore().get()
  if (state != null) {
    LOG.info("the migration distribution is already installed: ${state.app}, ${state.status.id}")
    return
  }
  if (isMigrationInstallRunning) {
    LOG.info("the migration install is already running")
    return
  }

  isMigrationInstallRunning = true
  val distribution = content.distribution
  val update = content.update
  @Suppress("UsagesOfObsoleteApi")
  object : Task.Backgroundable(project, IdeBundle.message("updates.migration.progress.title"), true) {
    override fun run(indicator: ProgressIndicator) {
      val app = installMigrationDistribution(distribution, indicator)
      LOG.info("the migration distribution is installed: ${app}")
      try {
        migrationStateStore().set(MigrationState(MigrationStatus.INSTALLED, app, distribution.product))
      }
      catch (e: IOException) {
        LOG.warn("cannot write the migration state", e)
      }
    }

    override fun onSuccess() {
      showMigrationReady(project, content.ready)
    }

    override fun onThrowable(error: Throwable) {
      val message = if (error is MigrationInstallException) {
        LOG.warn("the migration install failed: ${error.message}")
        error.userMessage
      }
      else {
        LOG.warn("the migration install failed", error)
        IdeBundle.message("updates.migration.error.install")
      }
      UpdateChecker.getNotificationGroupForIdeUpdateResults()
        .createNotification(IdeBundle.message("updates.migration.error.title"), message, NotificationType.ERROR)
        .addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("update.downloading.patch.open")) {
          BrowserUtil.browse(downloadUrl(update.newBuild, update.updatedChannel), project)
        })
        .setDisplayId("ide.migration.install.failed")
        .notify(project)
    }

    override fun onFinished() {
      isMigrationInstallRunning = false
    }
  }.queue()
}

/**
 * Shows "Restart to Update" for the installed migration distribution in the widget.
 * The notification shows only when the widget is not shown, or when [forceNotification] is true.
 * Without [ready] texts, the notification uses the generic texts.
 */
@RequiresEdt
internal fun showMigrationReady(project: Project?, ready: MigrationReadyTexts?, forceNotification: Boolean = false) {
  setMigrationRestartState()
  if (!forceNotification && IdeUpdateWidgetState.isWidgetShown()) {
    return
  }

  val title = ready?.title?.let { StringUtil.escapeXmlEntities(it) } ?: IdeBundle.message("update.ready.message")
  UpdateChecker.getNotificationGroupForIdeUpdateResults()
    .createNotification(title, "", NotificationType.INFORMATION)
    .addAction(NotificationAction.createSimpleExpiring(migrationReadyActionText(ready), MIGRATION_RESTART_ACTION))
    .setDisplayId("ide.migration.suggest.restart")
    .notify(project)
}

/** The text of the notification action. A text with the `<html>` prefix gives the generic text, and `&` and `_` are not mnemonics. */
@ApiStatus.Internal
fun migrationReadyActionText(ready: MigrationReadyTexts?): @Nls String {
  val action = ready?.action?.takeUnless { BasicHTML.isHTMLString(it) } ?: return IdeBundle.message("update.toolbar.widget.restart.text")
  return StringUtil.escapeMnemonics(action)
}

/**
 * At startup, a valid installed state gives "Restart to Update" in the widget, with no update check and no notification.
 */
internal fun restoreMigrationRestartState() {
  val app = ApplicationManager.getApplication()
  if (app.isHeadlessEnvironment || app.isCommandLine) {
    return
  }
  if (migrationStateStore().get()?.status == MigrationStatus.INSTALLED) {
    setMigrationRestartState()
  }
}

/** Whether the settings menu shows "Restart to Update": the state is installed, and the widget is not shown. */
internal fun isMigrationRestartInMenu(): Boolean =
  !IdeUpdateWidgetState.isWidgetShown() && migrationStateStore().get()?.status == MigrationStatus.INSTALLED

private fun setMigrationRestartState() {
  val widget = IdeUpdateWidgetState.getInstance()
  // the restart state needs an update first
  widget.onUpdateFound(true)
  widget.onRestartReady(MIGRATION_RESTART_ACTION)
}

/** "Restart to Update" of the migration. The widget does not count it as the restart of a patch. */
internal val MIGRATION_RESTART_ACTION: Runnable = Runnable { restartToMigration() }

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

internal fun download(url: String, indicator: ProgressIndicator?): ByteArray {
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
