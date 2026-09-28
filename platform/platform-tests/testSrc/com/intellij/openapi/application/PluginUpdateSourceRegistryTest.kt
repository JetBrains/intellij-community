// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.updateSettings.impl.FORCE_INTERNAL_USER_FOR_TESTS_IN_PLUGIN_UPDATE_SOURCES
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceService
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
internal class PluginUpdateSourceRegistryTest {

  @Test
  @RegistryKey(key = "platform.enable.plugin.update.source.feature", value = "true")
  @RegistryKey(key = "platform.limit.plugin.update.source.by.configured.one", value = "true")
  @RegistryKey(key = "platform.make.plugin.update.source.visible.in.ui", value = "true")
  @RegistryKey(key = "platform.disable.plugin.update.sources.ui.and.filtering.for.internal.users", value = "true")
  fun `plugin update source disabling registry affects only internal users`() {
    assertTrue(PluginUpdateSourceService.isPluginUpdateSourceShownInUI())
    assertTrue(PluginUpdateSourceService.isPluginUpdateFilteredAgainstPluginUpdateSource())

    TestModeFlags.runWithFlag(FORCE_INTERNAL_USER_FOR_TESTS_IN_PLUGIN_UPDATE_SOURCES, true) {
      assertFalse(PluginUpdateSourceService.isPluginUpdateSourceShownInUI())
      assertFalse(PluginUpdateSourceService.isPluginUpdateFilteredAgainstPluginUpdateSource())
    }
  }
}
