// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.providers

// @spec ../../../../../../../../../spec/file-layers.spec.md

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.platform.settings.v2.LayerData
import com.intellij.platform.settings.v2.LayerLocality
import com.intellij.platform.settings.v2.LayerProvider
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.impl.AppSettingsContext
import com.intellij.platform.settings.v2.impl.JsonSettingsCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

/**
 * Backs the base application layer: the defaults that ship with the IDE as the [RESOURCE_PATH] resource of the
 * platform (ADR 0147). The layer only reads the resource. It takes no write and watches nothing.
 */
@Service(Service.Level.APP)
internal class AppDefaultsLayerService(scope: CoroutineScope) : BaseLayerService(scope) {
  override val providerClass: Class<out LayerProvider> = AppDefaultsLayerProvider::class.java

  override val localities: List<LayerLocality> = listOf(LayerLocality.TEAM)

  @Volatile
  private var testResourceLoader: ClassLoader? = null

  private val resourceLoader: ClassLoader get() = testResourceLoader ?: AppDefaultsLayerService::class.java.classLoader

  /** Reads the defaults from [loader] instead of the platform until [disposable] is disposed. */
  @TestOnly
  fun setResourceLoaderForTests(loader: ClassLoader, disposable: Disposable) {
    check(testResourceLoader == null) { "Another test already overrides the defaults resource" }
    testResourceLoader = loader
    Disposer.register(disposable) { testResourceLoader = null }
  }

  override suspend fun read(storageContext: SettingsContext, locality: LayerLocality): MapValue {
    val bytes = withContext(Dispatchers.IO) { resourceLoader.getResourceAsStream(RESOURCE_PATH)?.use { it.readAllBytes() } }
    return if (bytes == null) MapValue(emptyMap()) else JsonSettingsCodec.decode(bytes.decodeToString())
  }

  override suspend fun write(storageContext: SettingsContext, locality: LayerLocality, data: MapValue) {
    error("The application defaults layer takes no write")
  }

  override fun availableLocalities(context: SettingsContext): Set<LayerLocality> = emptySet()

  override suspend fun requestData(scopeContext: SettingsContext): List<LayerData> =
    if (scopeContext.realContext is AppSettingsContext) load(AppSettingsContext) else emptyList()

  companion object {
    /** Holds the path of the defaults resource, relative to the root of the class path. */
    const val RESOURCE_PATH: String = "settings/app.settings.default.json"

    fun getInstance(): AppDefaultsLayerService = service()
  }
}
