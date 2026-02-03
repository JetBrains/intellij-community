// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.ide.startup.importSettings.DefaultTransferSettingsConfiguration
import com.intellij.ide.startup.importSettings.TransferSettingsConfiguration
import com.intellij.ide.startup.importSettings.data.ExternalProductService
import com.intellij.ide.startup.importSettings.data.ExternalService
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.transfer.backend.TransferSettingsDataProvider
import com.intellij.ide.startup.importSettings.transfer.backend.models.IdeVersion
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

@Service
class SettingTransferService(private val outerScope: CoroutineScope) : ExternalService {

  companion object {

    fun getInstance(): SettingTransferService = service()
  }

  private val config: TransferSettingsConfiguration
  init {
    val providers = buildList {
      ThirdPartyProductSettingsTransfer.EP_NAME.forEachExtensionSafe { extension ->
        addAll(extension.getProviders())
      }
    }

    config = DefaultTransferSettingsConfiguration(
      TransferSettingsDataProvider(providers),
      shouldDisplayFailedVersions = false
    )
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

  override val productServices: List<ExternalProductService> by lazy {
    config.dataProvider.providers.map { provider ->
      val id = provider.transferableIdeId
      val ideVersions = outerScope.async {
        val versions = loadIdeVersionsAsync(outerScope).await()
        versions.filterValues { it.product.transferableId == id }
      }
      SettingTransferProductService(id, ideVersions, config.controller)
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
}

data class ThirdPartyProductInfo(
  val product: IdeVersion,
  val settings: Deferred<Settings>
) {
  fun getSettingsQuickly(): Settings? =
    @Suppress("RAW_RUN_BLOCKING")
    (runBlocking {
    logger.runAndLogException {
      withTimeout(5.seconds) {
        settings.await()
      }
    }
  })
}

private val logger = logger<SettingTransferService>()
