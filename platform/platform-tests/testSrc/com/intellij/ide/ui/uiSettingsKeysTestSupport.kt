// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.application.PathManager
import com.intellij.platform.settings.v2.LayerLocality
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsKey
import com.intellij.platform.settings.v2.SettingsValue
import com.intellij.platform.settings.v2.SettingsVault
import com.intellij.platform.settings.v2.impl.SettingsKeyRegistry
import com.intellij.platform.settings.v2.impl.providers.JsonConfigFile
import com.intellij.platform.settings.v2.setValue
import com.intellij.testFramework.common.waitUntil
import com.intellij.platform.settings.v2.impl.providers.localityFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Holds the name of the team config of the application layer. It mirrors `AppLayerService.FILE_NAME` in
 * `community/platform/settings.v2/shared/src/com/intellij/platform/settings/v2/impl/providers/AppLayerService.kt`.
 */
private const val APP_CONFIG_FILE_NAME = "app.settings.json"

/**
 * Holds the identities of the keys of [UISettingsKeys]. That object is internal to its module, so a test finds a key by
 * its identity in the key registry, see [uiSettingsKey].
 */
internal object UISettingsKeyIdentities {
  const val IDE_ANTIALIASING: String = "ui.ideAntialiasing"
  const val EDITOR_ANTIALIASING: String = "ui.editorAntialiasing"
  const val CUSTOM_FONT_FACE: String = "ui.customFont.face"
  const val CUSTOM_FONT_SIZE: String = "ui.customFont.size"
  const val CUSTOM_FONT_ENABLED: String = "ui.customFont.enabled"
  const val IDE_SCALE: String = "ui.ideScale"
  const val PRESENTATION_MODE_IDE_SCALE: String = "ui.presentationModeIdeScale"
  const val SINGLE_STRIPE: String = "toolWindows.singleStripe"

  /** Lists every identity, in the order of the declarations of [UISettingsKeys]. */
  val ALL: List<String> = listOf(IDE_ANTIALIASING, EDITOR_ANTIALIASING, CUSTOM_FONT_FACE, CUSTOM_FONT_SIZE,
                                 CUSTOM_FONT_ENABLED, IDE_SCALE, PRESENTATION_MODE_IDE_SCALE, SINGLE_STRIPE)
}

/**
 * Returns the key of the UI settings of [identity], as its promoter registers it.
 *
 * @throws IllegalStateException when the registry holds no key of [identity]
 */
internal suspend fun uiSettingsKey(identity: String): SettingsKey<*> {
  val registry = SettingsKeyRegistry.getInstance()
  registry.awaitReady()
  return checkNotNull(registry[identity]) { "The registry holds no key '$identity'" }
}

/** Writes [value] into the key of the UI settings of [identity] at the application context. */
internal suspend fun <T> writeUISettingsKey(identity: String, value: T) {
  @Suppress("UNCHECKED_CAST")
  (uiSettingsKey(identity) as SettingsKey<T>).setValue(value)
}

/** Returns the stored values of the key of the UI settings of [identity] at the application context. */
internal suspend fun storedUISettingsValues(identity: String): List<Any?> =
  uiSettingsKey(identity).values(SettingsContext.app())

/**
 * Waits until the key of the UI settings of [identity] stores [value] and nothing else. A setter of [UISettings] writes
 * it later.
 */
internal suspend fun waitUntilUISettingsKeyStores(identity: String, value: Any?) {
  waitUntil("the key '$identity' stores $value") { storedUISettingsValues(identity) == listOf(value) }
}

/** Returns the config file of the application layer for [locality]. The application layer has no local file. */
internal fun appConfigFile(locality: LayerLocality): Path =
  Path.of(PathManager.getOptionsPath(), localityFileName(APP_CONFIG_FILE_NAME, locality))

/**
 * Drops every stored value of [UISettingsKeys] and reloads the application context. The environmental keys live in
 * the environmental config of the application, and the shareable key lives in its team config. The other entries of
 * the configs stay, such as the migration ledger, so no migration runs again after the reload.
 */
internal suspend fun dropStoredUISettings() {
  val vault = SettingsVault.getInstance()
  val appContext = SettingsContext.app()
  vault.flush(appContext)
  val identities = UISettingsKeyIdentities.ALL.toSet()
  withContext(Dispatchers.IO) {
    for (locality in listOf(LayerLocality.TEAM, LayerLocality.ENVIRONMENTAL)) {
      val config = appConfigFile(locality)
      JsonConfigFile.write(config, SettingsValue.MapValue(JsonConfigFile.read(config).values - identities))
    }
  }
  vault.reload(appContext)
}
