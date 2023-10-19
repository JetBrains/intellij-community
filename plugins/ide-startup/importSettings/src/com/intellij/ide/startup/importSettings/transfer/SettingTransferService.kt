// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.ide.customize.transferSettings.DefaultTransferSettingsConfiguration
import com.intellij.ide.customize.transferSettings.TransferSettingsDataProvider
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.models.SettingsPreferencesKind
import com.intellij.ide.customize.transferSettings.providers.vscode.VSCodeTransferSettingsProvider
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.data.BaseSetting
import com.intellij.ide.startup.importSettings.data.DataForSave
import com.intellij.ide.startup.importSettings.data.DialogImportData
import com.intellij.ide.startup.importSettings.data.ExternalService
import com.intellij.ide.startup.importSettings.data.IconProductSize
import com.intellij.ide.startup.importSettings.data.ImportProgress
import com.intellij.ide.startup.importSettings.data.Product
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.containers.nullize
import com.intellij.util.text.nullize
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rd.util.reactive.Property
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import javax.swing.Icon

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
  private lateinit var ideVersions: Deferred<Map<String, IdeVersion>>
  override suspend fun warmUp() {
    coroutineScope {
      ideVersions = async {
        config.dataProvider.run {
          refresh()
          orderedIdeVersions
            .filterIsInstance<IdeVersion>()
            .map { version -> version.id to version }
            .toMap()
        }
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun loadIdeVersions(): Map<String, IdeVersion> {
    if (ideVersions.isCompleted) return ideVersions.getCompleted()

    @Suppress("RAW_RUN_BLOCKING")
    return runBlocking {
      logger.warn("Started waiting for transfer provider initialization.")
      try {
        ideVersions.await()
      }
      finally {
        logger.warn("Finished waiting for transfer provider initialization.")
      }
    }
  }

  override fun products(): List<Product> {
    return logger.runAndLogException {
      val versions = loadIdeVersions().values
      versions.map(ExternalProductInfo::ofIdeVersion)
    } ?: emptyList()
  }

  override fun getSettings(itemId: String): List<BaseSetting> {
    return logger.runAndLogException {
      val versions = loadIdeVersions()
      val version = versions[itemId] ?: return emptyList()
      val settings = version.settingsCache // TODO: Preload in background
      buildList {
        settings.laf?.let(TransferableSetting::uiTheme)?.let(::add)
        settings.keymap?.let(TransferableSetting::keymap)?.let(::add)
        settings.plugins.nullize()?.let { TransferableSetting.plugins() }?.let(::add)
        settings.recentProjects.nullize()?.let { TransferableSetting.recentProjects() }?.let(::add)
      }
    } ?: emptyList()
  }

  override fun getProductIcon(itemId: String,
                              size: IconProductSize): Icon? {
    return logger.runAndLogException {
      val version = loadIdeVersions()[itemId] ?: return null
      return version.transferableId.icon
    }
  }

  override fun importSettings(productId: String, data: List<DataForSave>): DialogImportData {
    try {
      val ideVersion = loadIdeVersions()[productId] ?: error(ImportSettingsBundle.message("transfer.error.product-not-found", productId))
      applyPreferences(ideVersion, data)
      val withPlugins = ideVersion.settingsCache.preferences.plugins
      val importData = TransferSettingsProgress(ideVersion)
      config.controller.performImport(
        null,
        ideVersion,
        withPlugins,
        importData.createProgressIndicatorAdapter()
      )
      return importData
    }
    catch(t: Throwable) {
      if (t is CancellationException || t is ProcessCanceledException) {
        throw t
      }

      logger.error(t)
      return importErrorData(t) // TODO: Report error via the parent service
    }
  }
}

private fun applyPreferences(ideVersion: IdeVersion, toApply: List<DataForSave>) {
  val selectedIds = toApply.asSequence().map { it.id }.toSet()
  val preferences = ideVersion.settingsCache.preferences
  preferences[SettingsPreferencesKind.Laf] = selectedIds.contains(TransferableSetting.UI_ID)
  preferences[SettingsPreferencesKind.SyntaxScheme] = selectedIds.contains(TransferableSetting.UI_ID)
  preferences[SettingsPreferencesKind.Keymap] = selectedIds.contains(TransferableSetting.KEYMAP_ID)
  preferences[SettingsPreferencesKind.Plugins] = selectedIds.contains(TransferableSetting.PLUGINS_ID)
  preferences[SettingsPreferencesKind.RecentProjects] = selectedIds.contains(TransferableSetting.RECENT_PROJECTS_ID)
}

private fun importErrorData(error: Throwable) = object : DialogImportData {
  override val message = ImportSettingsBundle.message(
    "transfer.error.unknown",
    (error.localizedMessage ?: error.message)
      .nullize(nullizeSpaces = true) ?: ImportSettingsBundle.message("transfer.error.no-error-message")
  )
  override val progress = object : ImportProgress {
    override val progressMessage = Property("")
    override val progress = OptProperty<Int>()
  }
}
