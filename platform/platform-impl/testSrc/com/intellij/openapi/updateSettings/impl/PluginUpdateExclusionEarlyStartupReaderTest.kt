// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginUpdateExclusionService.Exclusion
import com.intellij.testFramework.PlatformTestUtil.useAppConfigDir
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@RegistryKey(key = "platform.enable.plugin.update.mode.feature", value = "true")
@TestApplication
internal class PluginUpdateExclusionEarlyStartupReaderTest {

  @Test
  fun `reads exclusions saved by the service`() = useAppConfigDir {
    val service = PluginUpdateExclusionService.getInstance()
    val expected = mapOf(
      PluginId.getId("manual.update.plugin") to Exclusion.FromAutoUpdate,
      PluginId.getId("ignored.update.plugin") to Exclusion.FromUpdate,
    )
    try {
      expected.forEach { [pluginId, exclusion] -> service.setExcluded(pluginId, exclusion) }

      timeoutRunBlocking {
        ApplicationManager.getApplication().stateStore.save(forceSavingAllSettings = true)
      }

      assertEquals(expected, PluginUpdateExclusionEarlyStartupReader.read())
    }
    finally {
      expected.keys.forEach { service.setExcluded(it, null) }
    }
  }

  @Test
  fun `returns empty when the file or component is missing`() = useAppConfigDir {
    val expected = emptyMap<PluginId, Exclusion>()
    assertEquals(expected, PluginUpdateExclusionEarlyStartupReader.read())

    writeXml("<application />")
    assertEquals(expected, PluginUpdateExclusionEarlyStartupReader.read())
  }

  @Test
  fun `propagates malformed xml`() = useAppConfigDir {
    writeXml("<application>")

    assertThrows(IOException::class.java) {
      PluginUpdateExclusionEarlyStartupReader.read()
    }
  }

  @Suppress("UseOptimizedEelFunctions")
  private fun writeXml(content: String) {
    val xmlPath = PathManager.getConfigDir().resolve(PathManager.OPTIONS_DIRECTORY).resolve(PLUGIN_UPDATE_EXCLUSIONS_FILE_NAME)
    Files.createDirectories(xmlPath.parent)
    Files.write(xmlPath, content.toByteArray(StandardCharsets.UTF_8))
  }
}