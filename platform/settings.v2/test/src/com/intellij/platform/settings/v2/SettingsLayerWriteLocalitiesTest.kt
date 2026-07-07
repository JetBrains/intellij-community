// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.platform.settings.v2.impl.JsonSettingsCodec
import com.intellij.platform.settings.v2.impl.providers.AppDefaultsLayerProvider
import com.intellij.platform.settings.v2.impl.providers.AppDefaultsLayerService
import com.intellij.platform.settings.v2.impl.providers.AppLayerService
import com.intellij.platform.settings.v2.impl.providers.ProjectLayerService
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks through the real providers which layer takes a write at a context. A layer takes a write only where
 * [LayerProvider.availableLocalities] offers a locality (ADR 0129). The read-only application defaults offer none,
 * and the file layer offers none at the application and the project context. The application defaults come from a
 * resource of the platform (ADR 0147), and the platform ships that resource well-formed. The application layer
 * creates no file when it starts (ADR 0152). It has no local locality, so a plain write there lands in the team file
 * (ADR 0149).
 */
@TestApplication
class SettingsLayerWriteLocalitiesTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    private val shipped = SettingsKey("test.layerWrite.shipped", default = "def", layerFilter = { it is AppDefaultsLayerProvider })
    private val scoped = SettingsKey("test.layerWrite.scoped", default = "def")
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectFixture.get()

  @Test
  fun `the read-only defaults layer resolves its resource and takes no write`(@TempDir resourceRoot: Path): Unit = runBlocking {
    val app = SettingsContext.app()
    val resource = resourceRoot.resolve(AppDefaultsLayerService.RESOURCE_PATH)
    Files.createDirectories(resource.parent)
    Files.writeString(resource, """{"${shipped.identity}": "shipped"}""")
    val loader = URLClassLoader(arrayOf(resourceRoot.toUri().toURL()), null)
    val override = Disposer.newDisposable()
    try {
      AppDefaultsLayerService.getInstance().setResourceLoaderForTests(loader, override)
      SettingsVault.getInstance().reload(app)

      assertEquals("shipped", shipped[app])
      val failure = assertThrows<IllegalStateException> { runBlocking { shipped[app] = "written" } }
      assertTrue(failure.message!!.contains("'${shipped.name}'") && failure.message!!.contains("'${app.identity}'")) { failure.message!! }
      assertEquals("shipped", shipped[app])
    }
    finally {
      Disposer.dispose(override)
      SettingsVault.getInstance().reload(app)
      loader.close()
    }
  }

  @Test
  fun `the platform ships a well-formed defaults resource`(): Unit = runBlocking {
    val text = AppDefaultsLayerService::class.java.classLoader.getResourceAsStream(AppDefaultsLayerService.RESOURCE_PATH)
      ?.use { it.readAllBytes().decodeToString() }
    assertNotNull(text) { "no ${AppDefaultsLayerService.RESOURCE_PATH} resource" }
    JsonSettingsCodec.decode(text!!)
    assertEquals(1, AppDefaultsLayerService.getInstance().requestData(SettingsContext.app()).size)
  }

  @Test
  fun `the application layer creates no file when it starts`(): Unit = runBlocking {
    val optionsDir = Path.of(PathManager.getOptionsPath())
    clearLocalityConfigs(optionsDir, AppLayerService.FILE_NAME)

    coroutineScope { Disposer.register(disposable, AppLayerService(this)) }

    for (name in localityConfigNames(AppLayerService.FILE_NAME)) {
      assertFalse(Files.exists(optionsDir.resolve(name))) { name }
    }
  }

  @Test
  fun `a plain write at the application context lands in the team file and creates no local file`(): Unit = runBlocking {
    val app = SettingsContext.app()
    val optionsDir = Path.of(PathManager.getOptionsPath())
    val localConfig = optionsDir.resolve(localConfigName(AppLayerService.FILE_NAME))
    Files.deleteIfExists(localConfig)

    scoped[app] = "team"
    SettingsVault.getInstance().flush(app)

    assertEquals(StringValue("team"), storedValue(optionsDir.resolve(AppLayerService.FILE_NAME)))
    assertFalse(Files.exists(localConfig))
  }

  @Test
  fun `the file layer takes no write at the application and the project context`(): Unit = runBlocking {
    val app = SettingsContext.app()
    val projectContext = SettingsContext.project(project)

    scoped[app] = "app"
    scoped[projectContext] = "project"
    SettingsVault.getInstance().flush(app)
    SettingsVault.getInstance().flush(projectContext)

    val appConfig = Path.of(PathManager.getOptionsPath(), AppLayerService.FILE_NAME)
    val projectConfig = Path.of(project.basePath!!, ProjectLayerService.CONFIG_DIR, localConfigName(ProjectLayerService.FILE_NAME))
    assertEquals(StringValue("app"), storedValue(appConfig))
    assertEquals(StringValue("project"), storedValue(projectConfig))
  }

  private fun storedValue(config: Path): SettingsValue? = JsonSettingsCodec.decode(Files.readString(config)).values[scoped.identity]
}
