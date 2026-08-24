// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TemporaryDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories

/**
 * Regression tests for PY-89275: a partially-created project must always be disposed when its
 * initialization is interrupted, otherwise it leaks through `ProjectIdsStorage.idsToProject`
 * (unregistration only happens in `ProjectImpl.dispose()`), which is what
 * `_LastInSuiteTest.testProjectLeak` flags.
 *
 * Two failure paths are covered:
 *
 * 1. Cancellation arrives at `templateAsync.await()` in `ProjectManagerImpl.prepareNewProject` —
 *    after `ProjectImpl` is constructed and registered in `ProjectIdsStorage`, but before
 *    `initProject` is entered. The project must be disposed via the caller-side `try/catch` in
 *    `prepareNewProject`.
 *
 * 2. A failure occurs *inside* `instantiateProject` after `ProjectImpl(...)` registered the project —
 *    e.g. `beforeInit(project)` throws, or a sibling coroutine on a shared scope cancels the parent
 *    while the inner `span("project instantiation")` / `span("options.beforeInit")` `withContext`
 *    is on its way out. The throw bubbles out of `instantiateProject` before its caller can assign
 *    the result and enter the caller-side try/catch, so the project must be disposed by
 *    `instantiateProject`'s own try/catch.
 *
 * Asserting [Project.isDisposed] is sufficient: if the project is disposed, `ProjectImpl.dispose()`
 * has called `unregisterProjectId`, removing it from `ProjectIdsStorage`.
 */
class ProjectCancellationLeakTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule: ApplicationRule = ApplicationRule()
  }

  @Service(Service.Level.PROJECT)
  private class HangingSaver : SettingsSavingComponent {
    override suspend fun save() {
      saveEntered.countDown()
      awaitCancellation()
    }
  }

  @Test
  fun `project is disposed when open is cancelled at templateAsync await`() {
    val projectFile = TemporaryDirectory.generateTemporaryPath("template-hang-leak-test")
    projectFile.createDirectories()

    // Force-instantiate the SettingsSavingComponent on the default project. Going through `service()` calls
    // `ComponentStoreWithExtraComponents.initComponent`, which drops the cached list of saving components so
    // that the next `saveSettings` invocation picks it up.
    val defaultProject = ProjectManagerEx.getInstanceEx().defaultProject
    defaultProject.service<HangingSaver>()
    saveEntered = CountDownLatch(1)

    var captured: Project? = null
    val options = OpenProjectTask {
      forceOpenInNewFrame = true
      runConversionBeforeOpen = false
      runConfigurators = false
      showWelcomeScreen = false
      isNewProject = true
      useDefaultProjectAsTemplate = true  // forces templateAsync to be non-null
      projectName = "template-hang-leak-test"
      projectRootDir = projectFile
      beforeInitTasks += { project -> captured = project }
    }

    @Suppress("RAW_SCOPE_CREATION")
    val openScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    try {
      val openJob = openScope.async {
        ProjectManagerEx.getInstanceEx().openProjectAsync(projectFile, options)
      }

      // Wait until `HangingSaver.save()` is pending — instantiateProject will have already returned by then,
      // and prepareNewProject is (or is about to be) suspended at templateAsync.await().
      assertThat(saveEntered.await(30, TimeUnit.SECONDS))
        .describedAs("HangingSaver.save() must be invoked during template save")
        .isTrue
      // Give the coroutine a chance to reach templateAsync.await(). This is a small window but not a race:
      // even if the open job reaches initProject first, initProject will observe the same scope cancellation
      // and also route through the caller-side try/catch that this test is validating.
      Thread.sleep(50)
      openJob.cancel(CancellationException("test-induced cancellation"))
      runBlocking { runCatching { openJob.await() } }
    }
    finally {
      openScope.cancel()
    }

    val leaked = captured
    assertThat(leaked)
      .describedAs("beforeInit must have captured the ProjectImpl reference")
      .isNotNull
    assertThat(leaked!!.isDisposed)
      .describedAs(
        "Project must be disposed after cancelled open (otherwise it leaks through ProjectIdsStorage.idsToProject, " +
        "since unregisterProjectId is only called by ProjectImpl.dispose())"
      )
      .isTrue
  }

  /**
   * Validates the inner `try/catch` in `ProjectManagerImpl.instantiateProject`. Throwing from
   * `beforeInit` exits the surrounding `span("options.beforeInit")` after the project is already
   * registered in `ProjectIdsStorage`, so `instantiateProject` must dispose the partially-created
   * project before propagating the exception. Without the inner catch, the throw skips the
   * caller-side `try/catch` in `prepareNewProject` (the caller never gets the project reference)
   * and the project leaks via `ProjectIdsStorage.idsToProject`.
   */
  @Test
  fun `project is disposed when beforeInit throws inside instantiateProject`() {
    val projectFile = TemporaryDirectory.generateTemporaryPath("beforeInit-throw-leak-test")
    projectFile.createDirectories()

    var captured: Project? = null
    val options = OpenProjectTask {
      forceOpenInNewFrame = true
      runConversionBeforeOpen = false
      runConfigurators = false
      showWelcomeScreen = false
      isNewProject = true
      useDefaultProjectAsTemplate = false
      projectName = "beforeInit-throw-leak-test"
      projectRootDir = projectFile
      beforeInitTasks += { project ->
        captured = project
        throw RuntimeException("test-induced beforeInit failure")
      }
    }

    runBlocking {
      runCatching { ProjectManagerEx.getInstanceEx().openProjectAsync(projectFile, options) }
    }

    val leaked = captured
    assertThat(leaked)
      .describedAs("beforeInit must have captured the ProjectImpl reference before throwing")
      .isNotNull
    assertThat(leaked!!.isDisposed)
      .describedAs(
        "Project must be disposed when a failure occurs inside instantiateProject (otherwise it " +
        "leaks via ProjectIdsStorage.idsToProject, since unregisterProjectId is only called by " +
        "ProjectImpl.dispose())"
      )
      .isTrue
  }
}

private lateinit var saveEntered: CountDownLatch
