// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
import com.intellij.platform.settings.v2.impl.providers.AppLayerService
import com.intellij.platform.settings.v2.impl.providers.ProjectLayerService
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * Checks that a reload fires a contextual listener at a directory context when only an ancestor value changes. The
 * project has no directory config at all. Only the application and the project configs change, and the listener at
 * the source root still fires, because the value that the key resolves there changed.
 */
@TestApplication
class SettingsReloadListenerTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val fromProject = SettingsKey("test.reloadListener.fromProject", default = "def")
    private val fromApp = SettingsKey("test.reloadListener.fromApp", default = "def")
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile
  private val appConfigDir = Path.of(PathManager.getOptionsPath())
  private val projectConfigDir get() = Path.of(project.basePath!!, ProjectLayerService.CONFIG_DIR)

  /**
   * Deletes the application and the project configs, and reloads them to clear their vault layers. The whole test run
   * shares the application config, so the cleanup also runs after each test.
   */
  @BeforeEach
  @AfterEach
  fun clearConfigs(): Unit = runBlocking {
    clearLocalityConfigs(appConfigDir, AppLayerService.FILE_NAME)
    clearLocalityConfigs(projectConfigDir, ProjectLayerService.FILE_NAME)
    SettingsVault.getInstance().reload(SettingsContext.app())
    SettingsVault.getInstance().reload(SettingsContext.project(project))
  }

  @Test
  fun `a reload that changes only the project value fires a listener at the source root`(): Unit = runBlocking {
    val rootContext = readAction { SettingsContext.file(sourceRoot, project) }
    val fires = AtomicInteger()
    fromProject.addListener(disposable, rootContext) { _, _ -> fires.incrementAndGet() }

    writeConfig(projectConfigDir.resolve(localConfigName(ProjectLayerService.FILE_NAME)), fromProject, "project")
    SettingsVault.getInstance().reload(SettingsContext.project(project))

    assertEquals("project", fromProject[rootContext])
    assertEquals(1, fires.get(), "the listener at the source root fires once for the new project value")
  }

  @Test
  fun `a reload that changes only the application value fires a listener at the source root`(): Unit = runBlocking {
    val rootContext = readAction { SettingsContext.file(sourceRoot, project) }
    val fires = AtomicInteger()
    fromApp.addListener(disposable, rootContext) { _, _ -> fires.incrementAndGet() }

    // The application has no local locality (ADR 0149), so its value is in the team config.
    writeConfig(appConfigDir.resolve(AppLayerService.FILE_NAME), fromApp, "app")
    SettingsVault.getInstance().reload(SettingsContext.app())

    assertEquals("app", fromApp[rootContext])
    assertEquals(1, fires.get(), "the listener at the source root fires once for the new application value")
  }

  private fun writeConfig(config: Path, key: SettingsKey<String>, value: String) {
    Files.createDirectories(config.parent)
    Files.writeString(config, """{"${key.identity}": "$value"}""")
  }
}
