// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.settings.v2.impl.providers.FileLayerService
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Pins how the file layer finds the directory configs: through the file name index, after the index is ready
 * (ADR 0163). A reload in dumb mode makes the application and the project contexts ready, and it keeps a directory
 * context dirty until smart mode returns. The lookup keeps out a config in an excluded directory. A project that
 * closes in dumb mode ends the lookup.
 *
 * [SettingsFileLocalityTest] holds the lookup of the configs of every locality, and [SettingsReloadSweepTest] holds the
 * lookup of nested directory configs.
 */
@TestApplication
class FileLayerIndexLookupTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()
    private val closingProjectPathFixture = tempPathFixture()

    private val key = SettingsKey("test.indexLookup.value", default = "def")

    /** Holds how long a test waits to see that something does not happen. */
    private val QUIET_PERIOD = 200.milliseconds
  }

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile
  private val vault get() = SettingsVault.getInstance()

  @Test
  fun `a project reload in dumb mode readies the project and loads the directory config in smart mode`(): Unit =
    assertDirectoryConfigWaitsForSmartMode("dumb-project-reload", SettingsContext.project(project))

  @Test
  fun `an application reload in dumb mode readies the application and the project and loads the directory config in smart mode`(): Unit =
    assertDirectoryConfigWaitsForSmartMode("dumb-app-reload", SettingsContext.app())

  @Test
  fun `a config in an excluded directory does not load`(): Unit = runBlocking {
    val includedDir = createConfigDirectory("lookup-included")
    val excludedDir = createConfigDirectory("lookup-excluded")
    val module = moduleFixture.get()
    PsiTestUtil.addExcludedRoot(module, excludedDir)
    try {
      val includedContext = readAction { SettingsContext.file(includedDir, project) }
      val excludedContext = readAction { SettingsContext.file(excludedDir, project) }

      val loadedContexts = FileLayerService.getInstance(project).requestData(SettingsContext.project(project)).map { it.context.identity }

      assertTrue(includedContext.identity in loadedContexts)
      assertFalse(excludedContext.identity in loadedContexts)
    }
    finally {
      PsiTestUtil.removeExcludedRoot(module, excludedDir)
    }
  }

  @Test
  fun `a project that closes in dumb mode ends the lookup of its configs`(): Unit = runBlocking {
    val closingProject = ProjectManagerEx.getInstanceEx().openProjectAsync(closingProjectPathFixture.get(), createTestOpenProjectOptions())!!
    try {
      IndexingTestUtil.waitUntilIndexesAreReady(closingProject)
      DumbModeTestUtils.startEternalDumbModeTask(closingProject).use {
        val lookup = async { FileLayerService.getInstance(closingProject).requestData(SettingsContext.project(closingProject)) }
        delay(QUIET_PERIOD)
        assertTrue(lookup.isActive)  // the lookup waits for smart mode

        closingProject.closeProjectAsync()

        assertEquals(emptyList<LayerData>(), withTimeout(10.seconds) { lookup.await() })
      }
    }
    finally {
      closingProject.closeProjectAsync()
    }
  }

  /**
   * Reloads [scopeContext] while the project is in dumb mode. It asserts that the application and the project contexts
   * get ready, and that the context of a new config directory [dirName] stays dirty until smart mode returns. The VFS
   * watcher is off, so only the reload loads the config.
   */
  private fun assertDirectoryConfigWaitsForSmartMode(dirName: String, scopeContext: SettingsContext): Unit = runBlocking {
    withFileWatcherDisabled(project) {
      val projectContext = SettingsContext.project(project)
      // The reload of the project open waits for the index too. A reload queues behind it, so the config below stays unloaded.
      vault.reload(projectContext)
      val dir = createConfigDirectory(dirName)
      val dirContext = readAction { SettingsContext.file(dir, project) }
      IndexingTestUtil.waitUntilIndexesAreReady(project)

      val reload = DumbModeTestUtils.startEternalDumbModeTask(project).use {
        val reload = launch { vault.reload(scopeContext) }
        withTimeout(10.seconds) { projectContext.awaitReady() }
        assertTrue(SettingsContext.app().isReady())
        delay(QUIET_PERIOD)
        assertTrue(reload.isActive)  // the file bucket waits for smart mode
        assertFalse(dirContext.isReady())
        assertEquals("def", key[dirContext])
        reload
      }

      withTimeout(30.seconds) { reload.join() }
      assertTrue(dirContext.isReady())
      assertEquals("dir value", key[dirContext])
    }
  }

  /** Creates the new directory [name] under the source root with a team config, and returns the directory. */
  private fun createConfigDirectory(name: String): VirtualFile =
    VfsTestUtil.createFile(sourceRoot, "$name/${FileLayerService.CONFIG_FILE}", """{ "${key.name}": "dir value" }""").parent
}
