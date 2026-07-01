// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.providers

// @spec ../../../../../../../../../spec/file-layers.spec.md

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.settings.v2.LayerData
import com.intellij.platform.settings.v2.LayerLocality
import com.intellij.platform.settings.v2.LayerProvider
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.impl.AppSettingsContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Backs the writable application layer, stored in `app.settings.json` and its locality files in the config `options`
 * directory (next to `jdk.table.xml`), applied to the application context. The layer has the team and the
 * environmental locality, but no local one (ADR 0149). It watches its files from the start, also a file that is still
 * missing. The vault persists its writes through [writeData] after a debounce (ADR 0061). A file is created on its
 * first write (ADR 0152).
 */
@Service(Service.Level.APP)
internal class AppLayerService(scope: CoroutineScope) : BaseLayerService(scope) {
  override val providerClass: Class<out LayerProvider> = AppLayerProvider::class.java

  private val appContext = AppSettingsContext

  override val localities: List<LayerLocality> = listOf(LayerLocality.TEAM, LayerLocality.ENVIRONMENTAL)

  init {
    scope.launch {
      for (locality in localities) {
        watchConfigFile(this@AppLayerService, configFile(locality)) { refresh(appContext) }
      }
    }
  }

  override suspend fun read(storageContext: SettingsContext, locality: LayerLocality): MapValue =
    withContext(Dispatchers.IO) { JsonConfigFile.read(configFile(locality)) }

  override suspend fun write(storageContext: SettingsContext, locality: LayerLocality, data: MapValue) {
    withContext(Dispatchers.IO) { JsonConfigFile.write(configFile(locality), data) }
  }

  override fun availableLocalities(context: SettingsContext): Set<LayerLocality> =
    if (context.realContext !is AppSettingsContext) emptySet() else localities.toSet()

  override suspend fun requestData(scopeContext: SettingsContext): List<LayerData> =
    if (scopeContext.realContext is AppSettingsContext) load(appContext) else emptyList()

  private fun configFile(locality: LayerLocality): Path = Path.of(PathManager.getOptionsPath(), localityFileName(FILE_NAME, locality))

  companion object {
    const val FILE_NAME: String = "app.settings.json"

    fun getInstance(): AppLayerService = service()
  }
}
