// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.configurationStore.saveSettings
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.startup.StartupManager
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Checks that the settings save of the IDE flushes the pending saves of settings v2. The test calls [saveSettings],
 * the entry point of the autosave and of the save before a commit.
 *
 * The debounced save must not reach the provider before the check, or a save with no flush also passes. So the test
 * writes the pending value through [TestLayerProvider.valueSavedBy], which holds the debounced save until a flush.
 */
@TestApplication
class SettingsIdeSaveTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val written = SettingsKey("test.ideSave.written", default = "def")

    /**
     * Holds the class name of `ProjectSettingsLoadActivity` in
     * `backend/baseline/src/com/intellij/platform/settings/v2/impl/ProjectSettingsLoadActivity.kt`. The class is
     * internal to a module that the test module cannot see.
     */
    private const val PROJECT_SETTINGS_LOAD_ACTIVITY = "com.intellij.platform.settings.v2.impl.ProjectSettingsLoadActivity"
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()
  private val project get() = projectFixture.get()

  @BeforeEach
  fun maskProviders(): Unit = timeoutRunBlocking {
    awaitProjectSettingsLoad()
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
  }

  @Test
  fun `the application save writes a pending change at the application context`(): Unit = timeoutRunBlocking {
    val app = SettingsContext.app()
    app.awaitReady()

    val saved = provider.valueSavedBy(app, written, "app") { saveSettings(ApplicationManager.getApplication()) }

    assertEquals(StringValue("app"), saved)
  }

  @Test
  fun `the project save writes a pending change at the project context`(): Unit = timeoutRunBlocking {
    val projectContext = SettingsContext.project(project)
    projectContext.awaitReady()

    val saved = provider.valueSavedBy(projectContext, written, "project") { saveSettings(project) }

    assertEquals(StringValue("project"), saved)
  }

  @Test
  fun `the project save writes a pending change at a directory of the project`(): Unit = timeoutRunBlocking {
    val dir = VfsTestUtil.createFile(sourceRootFixture.get().virtualFile, "ide-save/anchor.txt").parent
    val dirContext = readAction { SettingsContext.file(dir, project) }
    dirContext.awaitReady()

    val saved = provider.valueSavedBy(dirContext, written, "directory") { saveSettings(project) }

    assertEquals(StringValue("directory"), saved)
  }

  /**
   * Waits for the load activity of settings v2. The activity creates the project service that the store calls in a
   * save. The platform launches the activity at the open of the project and does not wait for it. A finished activity
   * is not in the map.
   *
   * The wait matches the activity by its class name. So it first checks that the extension point holds that class.
   * Otherwise, a renamed activity makes the wait pass with no wait.
   */
  private suspend fun awaitProjectSettingsLoad() {
    val activityRegistered = StartupActivity.POST_STARTUP_ACTIVITY.filterableLazySequence()
      .any { it.implementationClassName == PROJECT_SETTINGS_LOAD_ACTIVITY }
    assertTrue(activityRegistered) {
      "The post-startup activity extension point holds no $PROJECT_SETTINGS_LOAD_ACTIVITY. " +
      "After a rename of the activity, update PROJECT_SETTINGS_LOAD_ACTIVITY."
    }
    (StartupManager.getInstance(project) as StartupManagerImpl).getRunningProjectActivities()
      .filterKeys { it.name == PROJECT_SETTINGS_LOAD_ACTIVITY }
      .values.forEach { it.join() }
  }
}
