// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.openapi.application.PluginMigrationOptions
import com.intellij.openapi.diagnostic.logger
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

private const val AI_ASSISTANT_PLUGIN_ID = "com.intellij.ml.llm"

@TestApplication
internal class AIAssistantMigration263Test {
  @TempDir
  lateinit var configDir: Path

  private val disabledPluginsFile: Path
    get() = configDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME)

  @Test
  fun `disables AI Assistant when settings come from 2026_2`() {
    migrateFrom("2026.2")
    assertThat(disabledPluginsFile.readLines()).containsExactly(AI_ASSISTANT_PLUGIN_ID)
  }

  @Test
  fun `keeps the plugins the old version disabled`() {
    disabledPluginsFile.writeLines(listOf("org.example.first", "org.example.second"))
    migrateFrom("2025.3")
    assertThat(disabledPluginsFile.readLines()).containsExactlyInAnyOrder("org.example.first", "org.example.second", AI_ASSISTANT_PLUGIN_ID)
  }

  @Test
  fun `does not duplicate an entry the old version already had`() {
    disabledPluginsFile.writeLines(listOf(AI_ASSISTANT_PLUGIN_ID))
    migrateFrom("2026.2")
    assertThat(disabledPluginsFile.readLines()).containsExactly(AI_ASSISTANT_PLUGIN_ID)
  }

  @Test
  fun `leaves settings from 2026_3 alone`() {
    migrateFrom("2026.3")
    assertThat(disabledPluginsFile).doesNotExist()
  }

  @Test
  fun `leaves settings from a later version alone`() {
    migrateFrom("2027.1")
    assertThat(disabledPluginsFile).doesNotExist()
  }

  @Test
  fun `leaves settings alone when the previous version is unknown`() {
    migrateFrom(null)
    assertThat(disabledPluginsFile).doesNotExist()
  }

  private fun migrateFrom(previousVersion: String?) {
    val options = PluginMigrationOptions(
      previousVersion = previousVersion,
      currentProductVersion = "263.1",
      newConfigDir = configDir,
      oldConfigDir = configDir.resolve("old"),
      pluginsToMigrate = mutableListOf(),
      pluginsToDownload = mutableListOf(),
      log = logger<AIAssistantMigration263Test>(),
    )
    AIAssistantMigration263().migratePlugins(options)
  }
}
