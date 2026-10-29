// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.settings.v2.impl.providers.AppLayerService
import com.intellij.platform.settings.v2.impl.providers.FileLayerService
import com.intellij.platform.settings.v2.impl.providers.ProjectLayerService
import com.intellij.testFramework.VfsTestUtil
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Checks that a reload fires a contextual listener at a directory context when only an ancestor value changes. These
 * tests use no directory config. Only the application and the project configs change, and the listener at the source
 * root still fires, because the value that the key resolves there changed.
 *
 * It also checks the listeners of a value that the reload sweep drops (ADR 0065). These tests turn off the VFS
 * watcher of the file layer. So only the sweep drops the layer of a deleted directory config. A key-only listener
 * fires at the directory. A contextual listener fires only when the resolved value changes.
 */
@TestApplication
class SettingsReloadListenerTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val fromProject = SettingsKey("test.reloadListener.fromProject", default = "def")
    private val fromApp = SettingsKey("test.reloadListener.fromApp", default = "def")
    private val sweptForContextual = SettingsKey("test.reloadListener.sweptForContextual", default = "def")
    private val sweptForKeyOnly = SettingsKey("test.reloadListener.sweptForKeyOnly", default = "def")
    private val sweptToSameValue = SettingsKey("test.reloadListener.sweptToSameValue", default = "def")
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

  @Test
  fun `a reload that sweeps a directory layer fires a contextual listener of the removed value`(): Unit = runBlocking {
    withFileWatcherDisabled(project) {
      val dir = loadDirectoryConfig("sweptForContextual", sweptForContextual, "dir")
      val dirContext = readAction { SettingsContext.file(dir, project) }
      val fires = AtomicInteger()
      sweptForContextual.addListener(disposable, dirContext) { _, _ -> fires.incrementAndGet() }

      deleteDirectoryConfigAndReload(dir)

      assertEquals("def", sweptForContextual[dirContext])
      assertEquals(1, fires.get(), "the listener fires once when the sweep drops the value")
    }
  }

  @Test
  fun `a reload that sweeps a directory layer fires a key-only listener at the directory`(): Unit = runBlocking {
    withFileWatcherDisabled(project) {
      val dir = loadDirectoryConfig("sweptForKeyOnly", sweptForKeyOnly, "dir")
      val dirContext = readAction { SettingsContext.file(dir, project) }
      val changeContexts = CopyOnWriteArrayList<String>()
      sweptForKeyOnly.addListener(disposable) { _, context -> changeContexts.add(context.identity) }

      deleteDirectoryConfigAndReload(dir)

      assertEquals("def", sweptForKeyOnly[dirContext])
      assertEquals(listOf(dirContext.identity), changeContexts, "the listener fires once, at the context of the swept layer")
    }
  }

  @Test
  fun `a reload sweep that keeps the resolved value fires only the key-only listener`(): Unit = runBlocking {
    withFileWatcherDisabled(project) {
      writeConfig(projectConfigDir.resolve(localConfigName(ProjectLayerService.FILE_NAME)), sweptToSameValue, "same")
      val dir = loadDirectoryConfig("sweptToSameValue", sweptToSameValue, "same")
      val dirContext = readAction { SettingsContext.file(dir, project) }
      val contextualFires = AtomicInteger()
      sweptToSameValue.addListener(disposable, dirContext) { _, _ -> contextualFires.incrementAndGet() }
      val changeContexts = CopyOnWriteArrayList<String>()
      sweptToSameValue.addListener(disposable) { _, context -> changeContexts.add(context.identity) }

      deleteDirectoryConfigAndReload(dir)

      assertEquals("same", sweptToSameValue[dirContext])
      assertEquals(0, contextualFires.get(), "the project layer holds the same value, so the contextual listener does not fire")
      assertEquals(listOf(dirContext.identity), changeContexts, "the key-only listener fires once, at the context of the swept layer")
    }
  }

  /**
   * Creates the team config of the new directory [dirName] under the source root, with [value] for [key]. Then it
   * reloads the project scope, and checks that [key] resolves to [value] at the directory.
   *
   * @return the directory of the config
   */
  private suspend fun loadDirectoryConfig(dirName: String, key: SettingsKey<String>, value: String): VirtualFile {
    val dir = VfsTestUtil.createFile(sourceRoot, "$dirName/${FileLayerService.CONFIG_FILE}", configText(key, value)).parent
    SettingsVault.getInstance().reload(SettingsContext.project(project))
    assertEquals(value, key[readAction { SettingsContext.file(dir, project) }])
    return dir
  }

  /**
   * Deletes the team config of [dir] and reloads the project scope. With the VFS watcher off, only the reload sweep
   * drops the layer of the config.
   */
  private suspend fun deleteDirectoryConfigAndReload(dir: VirtualFile) {
    VfsTestUtil.deleteFile(readAction { dir.findChild(FileLayerService.CONFIG_FILE)!! })
    SettingsVault.getInstance().reload(SettingsContext.project(project))
  }

  private fun writeConfig(config: Path, key: SettingsKey<String>, value: String) {
    Files.createDirectories(config.parent)
    Files.writeString(config, configText(key, value))
  }

  private fun configText(key: SettingsKey<String>, value: String): String = """{"${key.identity}": "$value"}"""
}
