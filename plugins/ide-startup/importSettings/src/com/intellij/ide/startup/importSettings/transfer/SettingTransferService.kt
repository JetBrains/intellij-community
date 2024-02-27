// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.startup.importSettings.DefaultTransferSettingsConfiguration
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.controllers.TransferSettingsListener
import com.intellij.ide.startup.importSettings.data.*
import com.intellij.ide.startup.importSettings.models.IdeVersion
import com.intellij.ide.startup.importSettings.models.PluginFeature
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.models.SettingsPreferencesKind
import com.intellij.ide.startup.importSettings.providers.TransferSettingsPerformContext
import com.intellij.ide.startup.importSettings.providers.vscode.VSCodeTransferSettingsProvider
import com.intellij.ide.startup.importSettings.transfer.backend.TransferSettingsDataProvider
import com.intellij.ide.startup.importSettings.transfer.backend.providers.PluginInstallationState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.containers.nullize
import com.intellij.util.text.nullize
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rd.util.reactive.Property
import kotlinx.coroutines.*
import java.time.Duration
import javax.swing.Icon
import kotlin.time.Duration.Companion.seconds

@Service
class SettingTransferService(private val outerScope: CoroutineScope) : ExternalService {

  companion object {

    fun getInstance(): SettingTransferService = service()

    private val logger = logger<SettingTransferService>()
  }

  private val config = DefaultTransferSettingsConfiguration(
    TransferSettingsDataProvider(
      VSCodeTransferSettingsProvider(outerScope)
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
  private fun loadIdeVersionsAsync(scope: CoroutineScope): Deferred<Map<String, ThirdPartyProductInfo>> {
    ideVersions?.let { return it }
    logger.info("Refreshing the transfer settings data provider.")
    val versions = scope.async(Dispatchers.IO) {
      config.dataProvider.run {
        refresh()
        orderedIdeVersions
          .filterIsInstance<IdeVersion>()
          .map { version ->
            ThirdPartyProductInfo(version, scope.async(Dispatchers.IO) { loadIdeVersionSettings(version) })
          }.associateBy { info -> info.product.id }
      }
    }
    ideVersions = versions
    return versions
  }

  private fun loadIdeVersionSettings(version: IdeVersion): Settings =
    version.settingsCache

  override fun warmUp(scope: CoroutineScope) {
    loadIdeVersionsAsync(scope)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun loadProductInfos(): Map<String, ThirdPartyProductInfo> {
    ideVersions?.let { if (it.isCompleted) return it.getCompleted() }

    @Suppress("RAW_RUN_BLOCKING")
    return runBlocking {
      val ideVersions = loadIdeVersionsAsync(outerScope)

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

  override suspend fun hasDataToImport(): Boolean {
    val startNs = System.nanoTime()
    try {
      return config.dataProvider.hasDataToImport()
    }
    finally {
      val endNs = System.nanoTime()
      logger.info("Checking for data to import took ${Duration.ofNanos(endNs - startNs).toMillis()} ms.")
    }
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
    return logger.runAndLogException<Icon?> {
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
    val settings = product.settingsCache
    val preferences = settings.preferences
    preferences[SettingsPreferencesKind.Laf] = selectedIds.contains(TransferableSetting.UI_ID)
    preferences[SettingsPreferencesKind.SyntaxScheme] = selectedIds.contains(TransferableSetting.UI_ID)
    preferences[SettingsPreferencesKind.Keymap] = selectedIds.contains(TransferableSetting.KEYMAP_ID)
    preferences[SettingsPreferencesKind.Plugins] = selectedIds.contains(TransferableSetting.PLUGINS_ID)
    preferences[SettingsPreferencesKind.RecentProjects] = selectedIds.contains(TransferableSetting.RECENT_PROJECTS_ID)

    val featuresToRemove = settings.plugins.asSequence().filter { (_, feature) ->
      when (feature) {
        is PluginFeature -> PluginManagerCore.isPluginInstalled(PluginId.getId(feature.pluginId))
        else -> true
      }
    }.map { it.key }
    settings.plugins.keys.removeAll(featuresToRemove.toSet())
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
