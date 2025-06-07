// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.controllers.TransferSettingsController
import com.intellij.ide.startup.importSettings.controllers.TransferSettingsListener
import com.intellij.ide.startup.importSettings.data.*
import com.intellij.ide.startup.importSettings.models.PluginFeature
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.models.SettingsPreferencesKind
import com.intellij.ide.startup.importSettings.providers.TransferSettingsPerformContext
import com.intellij.ide.startup.importSettings.transfer.backend.models.IdeVersion
import com.intellij.ide.startup.importSettings.transfer.backend.providers.PluginInstallationState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.containers.nullize
import com.intellij.util.text.nullize
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rd.util.reactive.Property
import kotlinx.coroutines.*
import javax.swing.Icon
import kotlin.time.Duration.Companion.seconds

class SettingTransferProductService(
  override val productId: TransferableIdeId,
  private val ideVersions: Deferred<Map<String, ThirdPartyProductInfo>>,
  private val importController: TransferSettingsController
) : ExternalProductService {

  companion object {
    private val logger = logger<SettingTransferProductService>()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun loadProductInfos(): Map<String, ThirdPartyProductInfo> {
    ideVersions.let {
      if (it.isCompleted) return it.getCompleted()
    }

    @Suppress("RAW_RUN_BLOCKING")
    return runBlocking {
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

  override val productTitle: String
    get() = when (productId) {
      TransferableIdeId.DummyIde -> ""
      TransferableIdeId.VSCode -> ""
      TransferableIdeId.Cursor -> ""
      TransferableIdeId.Windsurf -> ""
      TransferableIdeId.VisualStudio -> "Visual Studio"
      TransferableIdeId.VisualStudioForMac -> "Visual Studio for Mac"
    }

  override fun products(): List<Product> {
    return logger.runAndLogException {
      val versions = loadProductInfos().values
      versions.map { ExternalProductInfo.ofIdeVersion(it.product) }
    } ?: emptyList()
  }

  override fun getImportablePluginIds(itemId: String): List<String> {
    return logger.runAndLogException {
      val versions = loadProductInfos()
      val product = versions[itemId] ?: return emptyList()
      val settings = product.getSettingsQuickly() ?: error("Cannot load settings for $itemId quickly.")
      settings.plugins.values.filterIsInstance<PluginFeature>().map { it.pluginId }
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
        settings.plugins.values.filter { !it.isHidden }.let {
          if (it.isNotEmpty()) {
            add(TransferableSetting.plugins(it))
          }
        }
        settings.recentProjects.nullize()?.let(TransferableSetting::recentProjects)?.let(::add)
        addAll(ThirdPartyProductSettingItemProvider.generateSettingItems(product.product.transferableId, settings))
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

  override fun importSettings(productId: String, data: DataToApply): DialogImportData {
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

      importController.addListener(TransferSettingsWizardListener(importLifetime))
      importController.performImport(
        null,
        product,
        progressIndicator
      )
      return importData
    }
    catch(t: Throwable) {
      if (t is CancellationException) {
        throw t
      }

      logger.error(t)
      showImportErrorNotification(t)
      return dummyImportData()
    }
  }

  private fun applyPreferences(product: IdeVersion, toApply: DataToApply) {
    val selectedIds = toApply.importSettings.asSequence().map { it.id }.toSet()
    val settings = product.settingsCache
    val preferences = settings.preferences
    val pluginImportRequestedByUser = selectedIds.contains(TransferableSetting.PLUGINS_ID)
    preferences[SettingsPreferencesKind.Laf] = selectedIds.contains(TransferableSetting.UI_ID)
    preferences[SettingsPreferencesKind.SyntaxScheme] = selectedIds.contains(TransferableSetting.UI_ID)
    preferences[SettingsPreferencesKind.Keymap] = selectedIds.contains(TransferableSetting.KEYMAP_ID)
    preferences[SettingsPreferencesKind.Plugins] = pluginImportRequestedByUser || toApply.featuredPluginIds.isNotEmpty()
    preferences[SettingsPreferencesKind.RecentProjects] = selectedIds.contains(TransferableSetting.RECENT_PROJECTS_ID)

    if (!pluginImportRequestedByUser) {
      settings.plugins.clear()
    }

    val featuredPluginsToAdd = toApply.featuredPluginIds.asSequence().map { it to PluginFeature(null, it, it) }
    settings.plugins.putAll(featuredPluginsToAdd)

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
  SettingsService.getInstance().notification.set(object : NotificationData {
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
