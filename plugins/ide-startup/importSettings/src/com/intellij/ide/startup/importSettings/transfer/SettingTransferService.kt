// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.ide.startup.importSettings.DefaultTransferSettingsConfiguration
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.TransferSettingsDataProvider
import com.intellij.ide.startup.importSettings.controllers.TransferSettingsListener
import com.intellij.ide.startup.importSettings.data.*
import com.intellij.ide.startup.importSettings.models.IdeVersion
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.models.SettingsPreferencesKind
import com.intellij.ide.startup.importSettings.providers.PluginInstallationState
import com.intellij.ide.startup.importSettings.providers.TransferSettingsPerformContext
import com.intellij.ide.startup.importSettings.providers.vscode.VSCodeTransferSettingsProvider
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.rd.util.withSyncIOBackgroundContext
import com.intellij.util.containers.nullize
import com.intellij.util.text.nullize
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rd.util.reactive.Property
import kotlinx.coroutines.*
import javax.swing.Icon
import kotlin.time.Duration.Companion.seconds

@Service
class SettingTransferService : ExternalService {

  companion object {

    fun getInstance(): SettingTransferService = service()

    private val logger = logger<SettingTransferService>()
  }

  private val config = DefaultTransferSettingsConfiguration(
    TransferSettingsDataProvider(
      VSCodeTransferSettingsProvider()
    ),
    shouldDisplayFailedVersions = false
  )

  data class ThirdPartyProductInfo(
    val product: IdeVersion,
    val settings: Deferred<Settings>
  ) {
    fun getSettingsQuickly(): Settings? =
      @Suppress("RAW_RUN_BLOCKING")
      runBlocking {
        logger.runAndLogException {
          withTimeout(5.seconds) {
            settings.await()
          }
        }
      }
  }

  @Volatile
  private var ideVersions: Deferred<Map<String, ThirdPartyProductInfo>>? = null
  private fun CoroutineScope.loadIdeVersionsAsync(): Deferred<Map<String, ThirdPartyProductInfo>> {
    ideVersions?.let { return it }
    logger.info("Refreshing the transfer settings data provider.")
    var versions = async {
      config.dataProvider.run {
        refresh()
        orderedIdeVersions
          .filterIsInstance<IdeVersion>()
          .map { version -> ThirdPartyProductInfo(version, async { loadIdeVersionSettingsAsync(version) }) }
          .map { info -> info.product.id to info }
          .toMap()
      }
    }
    ideVersions = versions
    return versions
  }

  private suspend fun CoroutineScope.loadIdeVersionSettingsAsync(version: IdeVersion): Settings =
    withSyncIOBackgroundContext {
      version.settingsCache
    }

  override suspend fun warmUp() {
    coroutineScope {
      loadIdeVersionsAsync()
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun loadProductInfos(): Map<String, ThirdPartyProductInfo> {
    ideVersions?.let { if (it.isCompleted) return it.getCompleted() }

    @Suppress("RAW_RUN_BLOCKING")
    return runBlocking {
      val ideVersions = loadIdeVersionsAsync()

      logger.warn("Started waiting for transfer provider initialization.")
      try {
        withTimeout(10.seconds) {
          ideVersions.await()
        }
      }
      finally {
        logger.warn("Finished waiting for transfer provider initialization.")
      }
    }
  }

  override suspend fun hasDataToImport() =
    coroutineScope {
      loadIdeVersionsAsync().await().values.any()
    }

  override fun products(): List<Product> {
    return logger.runAndLogException {
      val versions = loadProductInfos().values
      versions.map { ExternalProductInfo.ofIdeVersion(it.product) }
    } ?: emptyList()
  }

  override fun getSettings(itemId: String): List<BaseSetting> {
    return logger.runAndLogException {
      val versions = loadProductInfos()
      val product = versions[itemId] ?: return emptyList()
      val settings = product.getSettingsQuickly() ?: error("Cannot load settings for $itemId quickly.")
      buildList {
        settings.laf?.let(TransferableSetting::uiTheme)?.let(::add)
        settings.keymap?.let(TransferableSetting::keymap)?.let(::add)
        settings.plugins.values.let {
          if (it.isNotEmpty()) {
            add(TransferableSetting.plugins(it))
          }
        }
        settings.recentProjects.nullize()?.let(TransferableSetting::recentProjects)?.let(::add)
      }
    } ?: emptyList()
  }

  override fun getProductIcon(itemId: String,
                              size: IconProductSize): Icon? {
    return logger.runAndLogException {
      val info = loadProductInfos()[itemId] ?: return null
      return info.product.transferableId.icon(size)
    }
  }

  override fun importSettings(productId: String, data: List<DataForSave>): DialogImportData {
    try {
      val info = loadProductInfos()[productId] ?: error(ImportSettingsBundle.message("transfer.error.product-not-found", productId))
      val product = info.product
      applyPreferences(product, data)
      val importData = TransferSettingsProgress(product)
      val progressIndicator = importData.createProgressIndicatorAdapter()
      val importLifetime = LifetimeDefinition()
      SettingsService.getInstance().importCancelled.advise(importLifetime.lifetime) {
        progressIndicator.cancel()
      }

      config.controller.addListener(TransferSettingsWizardListener(importLifetime))
      config.controller.performImport(
        null,
        product,
        progressIndicator
      )
      return importData
    }
    catch(t: Throwable) {
      if (t is CancellationException || t is ProcessCanceledException) {
        throw t
      }

      logger.error(t)
      showImportErrorNotification(t)
      return dummyImportData()
    }
  }

  private fun applyPreferences(product: IdeVersion, toApply: List<DataForSave>) {
    val selectedIds = toApply.asSequence().map { it.id }.toSet()
    val preferences = product.settingsCache.preferences
    preferences[SettingsPreferencesKind.Laf] = selectedIds.contains(TransferableSetting.UI_ID)
    preferences[SettingsPreferencesKind.SyntaxScheme] = selectedIds.contains(TransferableSetting.UI_ID)
    preferences[SettingsPreferencesKind.Keymap] = selectedIds.contains(TransferableSetting.KEYMAP_ID)
    preferences[SettingsPreferencesKind.Plugins] = selectedIds.contains(TransferableSetting.PLUGINS_ID)
    preferences[SettingsPreferencesKind.RecentProjects] = selectedIds.contains(TransferableSetting.RECENT_PROJECTS_ID)
  }
}

internal class TransferSettingsWizardListener(private val ld: LifetimeDefinition) : TransferSettingsListener {

  override fun importStarted(ideVersion: IdeVersion, settings: Settings) {
    thisLogger().info("Settings import from ${ideVersion.name} has been started.")
  }

  override fun importFailed(ideVersion: IdeVersion, settings: Settings, throwable: Throwable) {
    ld.terminate()
    thisLogger().error("Setting import error from ${ideVersion.name}.", throwable)
    showImportErrorNotification(throwable)
  }

  override fun importPerformed(ideVersion: IdeVersion, settings: Settings, context: TransferSettingsPerformContext) {
    ld.terminate()
    thisLogger().info("Setting import error from ${ideVersion.name} has been finished, plugin state: ${context.pluginInstallationState}.")
    SettingsService.getInstance().doClose.fire(Unit)
    if (context.pluginInstallationState == PluginInstallationState.RestartRequired) {
      ApplicationManagerEx.getApplicationEx().restart(/* exitConfirmed = */ true)
    }
  }
}

private fun showImportErrorNotification(error: Throwable) {
  val message = ImportSettingsBundle.message(
    "transfer.error.unknown",
    (error.localizedMessage ?: error.message)
      .nullize(nullizeSpaces = true) ?: ImportSettingsBundle.message("transfer.error.no-error-message")
  )
  SettingsService.getInstance().error.fire(object : NotificationData {
    override val status = NotificationData.NotificationStatus.ERROR
    override val message = message
    override val customActionList = emptyList<NotificationData.Action>()
  })
}

private fun dummyImportData() = object : DialogImportData {
  override val message = ""
  override val progress = object : ImportProgress {
    override val progressMessage = Property("")
    override val progress = OptProperty<Int>()
  }
}
