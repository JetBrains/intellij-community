// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.platform.settings.v2.impl.JsonSettingsCodec
import com.intellij.platform.settings.v2.impl.providers.JsonFileLayerProvider
import com.intellij.platform.settings.v2.impl.providers.ProjectLayerService
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pins the persistence of a write at a file context when no file layer exists, which is the light mode of JetBrains
 * Light (ADR 0164). The write climbs to the project layer. The value must survive the persist of the project layer
 * and reach the local project config on the disk.
 *
 * The test masks the layer providers to the light-mode set: every registered provider but `JsonFileLayerProvider`.
 */
@TestApplication
class SettingsClimbedWritePersistenceTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val key = SettingsKey("test.climbed.value", default = "def")
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile
  private val projectConfigDir get() = Path.of(project.basePath!!, ProjectLayerService.CONFIG_DIR)

  @Test
  fun `a file-context write that climbs to the project layer survives the persist and reaches the project config`(): Unit = runBlocking {
    val lightModeProviders = LayerProvider.EP_NAME.extensionList.filter { it !is JsonFileLayerProvider }
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, lightModeProviders, disposable)
    clearLocalityConfigs(projectConfigDir, ProjectLayerService.FILE_NAME)
    val file = VfsTestUtil.createFile(sourceRoot, "climbed/file.txt")
    val fileContext = readAction { SettingsContext.file(file, project) }

    key[fileContext] = "climbed"
    assertEquals("climbed", key[fileContext], "the write is readable at once")

    delay(PERSIST_SETTLE)
    assertEquals("climbed", key[fileContext], "the value survives the persist of the project layer")

    val localConfig = projectConfigDir.resolve(localConfigName(ProjectLayerService.FILE_NAME))
    awaitEquals(StringValue("climbed")) { storedValue(localConfig) }
  }

  /** Returns the value of [key] in [config], or `null` when the file or the key is absent. */
  private fun storedValue(config: Path): SettingsValue? =
    if (Files.exists(config)) JsonSettingsCodec.decode(Files.readString(config)).values[key.identity] else null
}

/** Longer than the debounce of the vault saver, so the persist of the written unit has run. */
private val PERSIST_SETTLE = 500.milliseconds
