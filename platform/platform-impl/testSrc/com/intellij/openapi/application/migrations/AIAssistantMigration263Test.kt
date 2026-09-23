// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.openapi.application.PluginMigrationOptions
import com.intellij.openapi.diagnostic.logger
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

private const val AI_ASSISTANT_PLUGIN_ID = "com.intellij.ml.llm"
private const val CURRENT_VERSION = "2026.3"

@TestApplication
internal class AIAssistantMigration263Test {
  /** The parent of the config directories of all versions, like `~/Library/Application Support/JetBrains`. */
  @TempDir
  lateinit var configRoot: Path

  private val newConfigDir: Path
    get() = configRoot.resolve("IntelliJIdea$CURRENT_VERSION")

  private val disabledPluginsFile: Path
    get() = newConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME)

  @BeforeEach
  fun createNewConfigDir() {
    newConfigDir.createDirectories()
  }

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

  /** The old config directory is a sibling of the new one, and its name carries the version, as in the real layout. */
  private fun migrateFrom(previousVersion: String?) {
    val oldConfigDir = configRoot.resolve("IntelliJIdea${previousVersion ?: "Unknown"}").createDirectories()
    val options = PluginMigrationOptions(
      previousVersion = previousVersion,
      currentProductVersion = CURRENT_VERSION,
      newConfigDir = newConfigDir,
      oldConfigDir = oldConfigDir,
      pluginsToMigrate = mutableListOf(),
      pluginsToDownload = mutableListOf(),
      log = logger<AIAssistantMigration263Test>(),
    )
    AIAssistantMigration263().migratePlugins(options)
  }
}
