// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.platform.settings.v2.LayerLocality
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsValue
import com.intellij.platform.settings.v2.SettingsVault
import com.intellij.platform.settings.v2.impl.providers.JsonConfigFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** Checks the declaration of `UISettingsKeys`: the identities, the promotion and the storage of a [Float] value. */
@TestApplication
class UISettingsKeysTest {
  @BeforeEach
  @AfterEach
  fun dropStoredSettings(): Unit = timeoutRunBlocking(60.seconds) {
    dropStoredUISettings()
  }

  @Test
  fun `the promoter registers every key with its name as its identity`(): Unit = timeoutRunBlocking(60.seconds) {
    for (identity in UISettingsKeyIdentities.ALL) {
      assertEquals(identity, uiSettingsKey(identity).name, "the name of the registered key '$identity'")
    }
  }

  @Test
  fun `a float value is stored as its decimal text and reads back after a reload`(): Unit = timeoutRunBlocking(60.seconds) {
    writeUISettingsKey(UISettingsKeyIdentities.IDE_SCALE, 1.1f)
    val appContext = SettingsContext.app()
    SettingsVault.getInstance().flush(appContext)

    val stored = withContext(Dispatchers.IO) { JsonConfigFile.read(appConfigFile(LayerLocality.ENVIRONMENTAL)) }
    assertEquals(SettingsValue.DoubleValue(1.1), stored.values[UISettingsKeyIdentities.IDE_SCALE])

    SettingsVault.getInstance().reload(appContext)
    assertEquals(1.1f, uiSettingsKey(UISettingsKeyIdentities.IDE_SCALE)[appContext])
  }

  @Test
  fun `a hand-edited integer value of a float key reads as a float after a reload`(): Unit = timeoutRunBlocking(60.seconds) {
    val appContext = SettingsContext.app()
    withContext(Dispatchers.IO) {
      val config = appConfigFile(LayerLocality.ENVIRONMENTAL)
      val values = JsonConfigFile.read(config).values + (UISettingsKeyIdentities.IDE_SCALE to SettingsValue.IntValue(2))
      JsonConfigFile.write(config, SettingsValue.MapValue(values))
    }

    SettingsVault.getInstance().reload(appContext)
    assertEquals(2f, uiSettingsKey(UISettingsKeyIdentities.IDE_SCALE)[appContext])
  }
}
