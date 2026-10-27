// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.settings.v2.impl.providers.AppLayerService
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
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
 * Pins a realistic multidimensional setting through the plain key API: a `style` per profile and language. One test
 * **builds** the application config and compares the file on disk to a committed snapshot. The config holds the
 * rosters, the current selections and the 2×2 matrix of slots, and [access] sets the slots that are not selected.
 * The other test **reads** that committed config back. It resolves `style` by a change of the current discriminator
 * values and by slots pinned with [access].
 */
@TestApplication
class SettingsSlottedWriteTest {
  companion object {
    private val profiles = SettingsKey("test.profiles", default = emptyList(), serializer = StringSerializer.listOf())
    private val languages = SettingsKey("test.languages", default = emptyList(), serializer = StringSerializer.listOf())
    private val profileCurrent = SettingsKey("test.profile.current", default = "default")
    private val langCurrent = SettingsKey("test.lang.current", default = "default")
    private val profileData = SettingsMapKey("test.profile.data", discriminatorKey = profileCurrent)
    private val langData = SettingsMapKey("test.lang.data", discriminatorKey = langCurrent, storageKey = profileData)
    private val style = SettingsKey("test.style", default = "none", storageKey = langData)

    private const val SNAPSHOT_DIR = "slottedWrite"
    private const val CONFIG = "profiles.app.settings.json"

    private fun profileOf(profile: String, lang: String) = { b: SettingsAccessContext.Builder ->
      b[profileCurrent] = profile
      b[langCurrent] = lang
    }
  }

  // The application has no local locality (ADR 0149), so a plain write lands in the team app config.
  private val appConfig = Path.of(PathManager.getOptionsPath(), AppLayerService.FILE_NAME)

  /**
   * Deletes the application config, and reloads it to clear the application layer of the vault. The whole test run
   * shares this config and layer, so each test starts from an empty state.
   */
  @BeforeEach
  fun dropAppConfig(): Unit = runBlocking {
    clearLocalityConfigs(Path.of(PathManager.getOptionsPath()), AppLayerService.FILE_NAME)
    SettingsVault.getInstance().reload(SettingsContext.app())
    val appCtx = SettingsContext.app()
    // reload installs synchronously, so the cleared (deleted) config is visible at once
    assertEquals(emptyList<String>(), profiles[appCtx])
    assertEquals("none", style[appCtx.access(profileOf("dev", "java"))])
  }

  @Test
  fun `builds the per-profile per-language config and matches the snapshot`(): Unit = runBlocking {
    val appCtx = SettingsContext.app()

    profiles[appCtx] = listOf("prod", "dev")
    languages[appCtx] = listOf("kotlin", "java")
    profileCurrent[appCtx] = "prod"
    langCurrent[appCtx] = "kotlin"

    // accessFor pins the slot that the current discriminators select, here prod / kotlin.
    assertCurrent("prod", "kotlin")
    style[appCtx.accessFor(style)] = "prod-kotlin"

    // Other cells are addressed by pinning their slots via access, without touching the current selection.
    style[appCtx.access(profileOf("prod", "java"))] = "prod-java"
    style[appCtx.access(profileOf("dev", "kotlin"))] = "dev-kotlin"

    // Changing the current discriminators makes the next accessFor pin a different slot.
    profileCurrent[appCtx] = "dev"
    langCurrent[appCtx] = "java"
    assertCurrent("dev", "java")
    style[appCtx.accessFor(style)] = "dev-java"

    // Writes publish to the vault immediately but persist to disk only after the debounce, so wait until the
    // built file settles to the expected snapshot before comparing it.
    val expected = Files.readString(Path.of(testResourcePath("$SNAPSHOT_DIR/$CONFIG"))).trim()
    withTimeout(10.seconds) {
      while (!Files.exists(appConfig) || Files.readString(appConfig).trim() != expected) delay(10.milliseconds)
    }
    UsefulTestCase.assertSameLinesWithFile(testResourcePath("$SNAPSHOT_DIR/$CONFIG"), Files.readString(appConfig))
  }

  @Test
  fun `reads the per-profile per-language config by current value and by pinned slot`(): Unit = runBlocking {
    seedConfig()
    val appCtx = SettingsContext.app()

    // The seeded current selection is dev / java.
    assertEquals("dev-java", style[appCtx])

    // Changing a current discriminator re-selects the slot the same key resolves to.
    profileCurrent[appCtx] = "prod"
    assertEquals("prod-java", style[appCtx])
    langCurrent[appCtx] = "kotlin"
    assertEquals("prod-kotlin", style[appCtx])

    // Pinning slots via access resolves any cell regardless of the current selection.
    assertEquals("dev-kotlin", style[appCtx.access(profileOf("dev", "kotlin"))])
    assertEquals("dev-java", style[appCtx.access(profileOf("dev", "java"))])
    assertEquals("prod-java", style[appCtx.access(profileOf("prod", "java"))])
  }

  /** Seeds the application config from the committed snapshot and loads it into the vault. */
  private suspend fun seedConfig() {
    Files.createDirectories(appConfig.parent)
    Files.writeString(appConfig, Files.readString(Path.of(testResourcePath("$SNAPSHOT_DIR/$CONFIG"))))
    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(appConfig)
    SettingsVault.getInstance().reload(SettingsContext.app())
  }

  /** Asserts both current discriminators have landed, so a following accessFor pins the right slot. */
  private fun assertCurrent(profile: String, lang: String) {
    val appCtx = SettingsContext.app()
    assertEquals(profile, profileCurrent[appCtx])
    assertEquals(lang, langCurrent[appCtx])
  }
}
