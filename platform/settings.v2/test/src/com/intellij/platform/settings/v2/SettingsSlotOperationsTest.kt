// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.application.PathManager
import com.intellij.platform.settings.v2.SettingsSlotOperationsTest.Companion.DIR
import com.intellij.platform.settings.v2.impl.providers.AppLayerService
import com.intellij.platform.settings.v2.impl.providers.ProjectLayerService
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Pins the slot operations (copy, drop, rename) of a [SettingsMapKey] at the application and the project layers. The
 * vault applies an operation to every layer at the context that holds the slot. Each test seeds and loads a committed
 * config, runs the operation, and compares the resulting file to a committed snapshot. [SettingsMapKey.renameSlot] is
 * a copy and a drop in one batch (ADR 0132), so it reaches the disk in one write.
 */
@TestApplication
class SettingsSlotOperationsTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    private val current = SettingsKey("test.slotops.current", default = "dev")
    private val data = SettingsMapKey("test.slotops.data", discriminatorKey = current)

    private val style = SettingsKey("test.slotops.style", default = "none", storageKey = data)

    private const val DIR = "slotOps"
  }

  private val project get() = projectFixture.get()
  // The input is seeded into the config that a plain write takes: the team config of the application, which has no
  // local locality (ADR 0149), and the local config of the project. A slot op rewrites every layer that holds the
  // slot, so the output is there too.
  private val appConfig = Path.of(PathManager.getOptionsPath(), AppLayerService.FILE_NAME)
  private val projectConfig get() = Path.of(project.basePath!!, ProjectLayerService.CONFIG_DIR, localConfigName(ProjectLayerService.FILE_NAME))

  /**
   * Deletes the application and the project configs, and reloads them to clear their vault layers. The tests of this
   * class share these configs and layers, so each test starts from an empty state, not from the slots of another test.
   */
  @BeforeEach
  fun clearConfigs(): Unit = runBlocking {
    clearLocalityConfigs(Path.of(PathManager.getOptionsPath()), AppLayerService.FILE_NAME)
    clearLocalityConfigs(Path.of(project.basePath!!, ProjectLayerService.CONFIG_DIR), ProjectLayerService.FILE_NAME)
    reset(SettingsContext.app())
    reset(SettingsContext.project(project))
  }

  private suspend fun reset(context: SettingsContext) {
    load(context)
    assertEquals("none", style[context.access { this[current] = "dev" }])  // reload installs synchronously
  }

  @Test
  fun `app - copy slot appends a duplicate under the new discriminator`(): Unit = runBlocking {
    val context = SettingsContext.app()
    verify(context, appConfig, "copy.json") { data.copySlot(context, "dev", "stage") }
  }

  @Test
  fun `app - drop slot removes it`(): Unit = runBlocking {
    val context = SettingsContext.app()
    verify(context, appConfig, "drop.json") { data.dropSlot(context, "prod") }
  }

  @Test
  fun `app - copy slot overwrites an existing target slot and keeps the source`(): Unit = runBlocking {
    val context = SettingsContext.app()
    verify(context, appConfig, "copy-overwrite.json") { data.copySlot(context, "dev", "prod") }
  }

  @Test
  fun `app - dropping the last slot removes the storage entry`(): Unit = runBlocking {
    val context = SettingsContext.app()
    verify(context, appConfig, "drop-last.json") {
      data.dropSlot(context, "prod")
      data.dropSlot(context, "dev")
    }
  }

  @Test
  fun `app - rename slot moves it in one write`(): Unit = runBlocking {
    val context = SettingsContext.app()
    verify(context, appConfig, "rename.json") { data.renameSlot(context, "dev", "dev2") }
  }

  @Test
  fun `project - copy slot appends a duplicate under the new discriminator`(): Unit = runBlocking {
    val context = SettingsContext.project(project)
    verify(context, projectConfig, "copy.json") { data.copySlot(context, "dev", "stage") }
  }

  @Test
  fun `project - drop slot removes it`(): Unit = runBlocking {
    val context = SettingsContext.project(project)
    verify(context, projectConfig, "drop.json") { data.dropSlot(context, "prod") }
  }

  @Test
  fun `project - rename slot moves it in one write`(): Unit = runBlocking {
    val context = SettingsContext.project(project)
    verify(context, projectConfig, "rename.json") { data.renameSlot(context, "dev", "dev2") }
  }

  /**
   * Seeds `input.json` of [DIR] into [configPath], and loads it into the vault. Then runs [op], and waits until the
   * file matches the snapshot [expected].
   *
   * @param context the context whose layer reads [configPath]
   * @param expected the name of the snapshot file in [DIR]
   */
  private suspend fun verify(context: SettingsContext, configPath: Path, expected: String, op: suspend () -> Unit) {
    Files.createDirectories(configPath.parent)
    Files.writeString(configPath, Files.readString(Path.of(testResourcePath("$DIR/input.json"))))
    load(context)
    assertEquals("dev-style", style[context.access { this[current] = "dev" }])  // reload installs synchronously

    op()

    val expectedText = Files.readString(Path.of(testResourcePath("$DIR/$expected"))).trim()
    withTimeout(10.seconds) {
      while (!Files.exists(configPath) || Files.readString(configPath).trim() != expectedText) delay(10.milliseconds)
    }
    UsefulTestCase.assertSameLinesWithFile(testResourcePath("$DIR/$expected"), Files.readString(configPath))
  }

  /** Loads the seeded config into the layer of [context]. The vault asks every provider again. */
  private suspend fun load(context: SettingsContext) {
    SettingsVault.getInstance().reload(context)
  }
}
