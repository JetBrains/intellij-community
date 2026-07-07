// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.platform.settings.v2.impl.JsonSettingsCodec
import com.intellij.platform.settings.v2.impl.providers.AppLayerService
import com.intellij.platform.settings.v2.impl.providers.ProjectLayerService
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks that the application layer and the project layer create a config file only on its first write, and never
 * when they start (ADR 0152). A test starts its own layer service on cleared configs, so the start runs with no file on disk. The
 * watcher of the project layer picks up a config file that appears after the start.
 */
@TestApplication
class SettingsConfigFileCreationTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    private val written = SettingsKey("test.configCreation.written", default = "def")
    private val external = SettingsKey("test.configCreation.external", default = "def")
    private val environmental = SettingsKey("test.configCreation.environmental", default = "def", locality = KeyLocality.ENVIRONMENTAL)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectFixture.get()
  private val optionsDir get() = Path.of(PathManager.getOptionsPath())
  private val projectConfigDir get() = Path.of(project.basePath!!, ProjectLayerService.CONFIG_DIR)

  @Test
  fun `the project layer creates no file when it starts`(): Unit = runBlocking {
    clearLocalityConfigs(projectConfigDir, ProjectLayerService.FILE_NAME)

    coroutineScope { Disposer.register(disposable, ProjectLayerService(project, this)) }

    assertNoConfigs(projectConfigDir, localityConfigNames(ProjectLayerService.FILE_NAME))
  }

  @Test
  fun `the first write at the application context creates only the team file`(): Unit = runBlocking {
    val app = SettingsContext.app()
    clearLocalityConfigs(optionsDir, AppLayerService.FILE_NAME)
    SettingsVault.getInstance().reload(app)
    coroutineScope { Disposer.register(disposable, AppLayerService(this)) }

    written[app] = "app"
    SettingsVault.getInstance().flush(app)

    val teamName = localityConfigName(AppLayerService.FILE_NAME, LayerLocality.TEAM)
    assertEquals(StringValue("app"), storedValue(optionsDir.resolve(teamName), written))
    assertNoConfigs(optionsDir, localityConfigNames(AppLayerService.FILE_NAME) - teamName)
  }

  @Test
  fun `the first write at the project context creates only the local file`(): Unit = runBlocking {
    val projectContext = SettingsContext.project(project)
    clearLocalityConfigs(projectConfigDir, ProjectLayerService.FILE_NAME)
    SettingsVault.getInstance().reload(projectContext)
    coroutineScope { Disposer.register(disposable, ProjectLayerService(project, this)) }

    written[projectContext] = "project"
    SettingsVault.getInstance().flush(projectContext)

    val localName = localConfigName(ProjectLayerService.FILE_NAME)
    assertEquals(StringValue("project"), storedValue(projectConfigDir.resolve(localName), written))
    // Pins the path of file-layers.spec.md. The other asserts derive it from ProjectLayerService.CONFIG_DIR.
    assertTrue(Files.exists(Path.of(project.basePath!!, ".jetbrains", "project.settings.local.json")))
    assertNoConfigs(projectConfigDir, localityConfigNames(ProjectLayerService.FILE_NAME) - localName)
  }

  @Test
  fun `the first write of an environmental key at the application context creates only the environmental file`(): Unit = runBlocking {
    val app = SettingsContext.app()
    clearLocalityConfigs(optionsDir, AppLayerService.FILE_NAME)
    SettingsVault.getInstance().reload(app)
    coroutineScope { Disposer.register(disposable, AppLayerService(this)) }

    environmental[app] = "app env"
    SettingsVault.getInstance().flush(app)

    val envName = localityConfigName(AppLayerService.FILE_NAME, LayerLocality.ENVIRONMENTAL)
    assertEquals(StringValue("app env"), storedValue(optionsDir.resolve(envName), environmental))
    assertNoConfigs(optionsDir, localityConfigNames(AppLayerService.FILE_NAME) - envName)
  }

  @Test
  fun `the first write of an environmental key at the project context creates only the environmental file`(): Unit = runBlocking {
    val projectContext = SettingsContext.project(project)
    clearLocalityConfigs(projectConfigDir, ProjectLayerService.FILE_NAME)
    SettingsVault.getInstance().reload(projectContext)
    coroutineScope { Disposer.register(disposable, ProjectLayerService(project, this)) }

    environmental[projectContext] = "project env"
    SettingsVault.getInstance().flush(projectContext)

    val envName = localityConfigName(ProjectLayerService.FILE_NAME, LayerLocality.ENVIRONMENTAL)
    assertEquals(StringValue("project env"), storedValue(projectConfigDir.resolve(envName), environmental))
    assertNoConfigs(projectConfigDir, localityConfigNames(ProjectLayerService.FILE_NAME) - envName)
  }

  @Test
  fun `a project config file that appears after the start is loaded`(): Unit = runBlocking {
    val projectContext = SettingsContext.project(project)
    // The file comes together with a new directory, so the watcher must react to the directory, not only to the file.
    deleteProjectConfigDir()
    val teamConfig = projectConfigDir.resolve(ProjectLayerService.FILE_NAME)
    try {
      Files.createDirectories(projectConfigDir)
      Files.writeString(teamConfig, """{"${external.identity}": "external"}""")
      refreshDirectory(projectConfigDir)

      awaitEquals("external") { external[projectContext] }
    }
    finally {
      deleteProjectConfigDir()
      SettingsVault.getInstance().reload(projectContext)
    }
  }

  private fun deleteProjectConfigDir() {
    projectConfigDir.toFile().deleteRecursively()
    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(projectConfigDir.parent)?.refresh(false, true)
  }

  private fun assertNoConfigs(dir: Path, names: List<String>) {
    for (name in names) {
      assertFalse(Files.exists(dir.resolve(name))) { name }
    }
  }

  private fun storedValue(config: Path, key: SettingsKey<*>): SettingsValue? =
    JsonSettingsCodec.decode(Files.readString(config)).values[key.identity]

  /** Refreshes [dir] in the VFS without a recursion, as the native file watcher does after a change inside it. */
  private fun refreshDirectory(dir: Path) {
    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(dir)!!.refresh(false, false)
  }
}
