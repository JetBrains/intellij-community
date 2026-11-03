// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.openapi.wm.ex.isIndexingActivitiesSuppressed
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.writeText

/**
 * A recent project without a workspace model cache reads its module files during the load, and the root change asks for the capabilities.
 * The first answer is cached for the life of the project, so the service must mark the project before the load asks.
 */
@TestApplication
internal class RecentProjectsServiceTest {
  @Test
  fun `a directory-based recent project has indexing suppressed`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val projectDir = createDirectoryProject(tempDir.resolve("recent"))

    assertThat(onRecentProject(projectDir, action = { isIndexingActivitiesSuppressed(it) })).isTrue()
  }

  /** The recent-project path of an `.ipr` project is the `.ipr` file, and the base path of the project is its parent directory. */
  @Test
  fun `an ipr-based recent project has indexing suppressed`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val projectDir = createModule(tempDir.resolve("recent"))
    val iprFile = projectDir.resolve("recent.ipr")
    iprFile.writeText(modulesXml())

    assertThat(onRecentProject(iprFile, action = { isIndexingActivitiesSuppressed(it) })).isTrue()
  }

  /** The service must reuse an open project, because a second instance for the same location collides on the project storages. */
  @Test
  fun `an open ipr-based project is reused`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val iprFile = createModule(tempDir.resolve("recent")).resolve("recent.ipr")
    iprFile.writeText(modulesXml())
    TrustedProjects.setProjectTrusted(iprFile, isTrusted = true)
    val options = createTestOpenProjectOptions(runPostStartUpActivities = false)

    checkNotNull(ProjectManagerEx.getInstanceEx().openProjectAsync(iprFile, options)).useProjectAsync { openProject ->
      assertThat(onRecentProject(iprFile, action = { it })).isSameAs(openProject)
    }
  }

  /** The service loads one project at a time. A request that waits for the load of another project shows its own load meanwhile. */
  @Test
  fun `a request that waits for another load shows its load`(@TempDir tempDir: Path, @TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking {
      val loadingDir = createDirectoryProject(tempDir.resolve("loading"))
      val waitingDir = createDirectoryProject(tempDir.resolve("waiting"))
      val initStarted = CompletableDeferred<Unit>()
      val initReleased = CompletableDeferred<Unit>()
      holdInit(projectDir = loadingDir, started = initStarted, released = initReleased, disposable = disposable)
      val service = RecentProjectsService.getInstance()
      try {
        val loadingOperation = requestOperation(loadingDir)
        initStarted.await()
        val waitingOperation = requestOperation(waitingDir)

        awaitLoad(waitingDir, isShown = true)

        initReleased.complete(Unit)
        loadingOperation.await()
        waitingOperation.await()
        awaitLoad(waitingDir, isShown = false)
      }
      finally {
        service.disposeHeldProjects()
      }
    }

  /** Disposing the held projects cancels a load in progress, and the load stops showing. */
  @Test
  fun `a load that the disposal cancels stops showing`(@TempDir tempDir: Path, @TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking {
      val projectDir = createDirectoryProject(tempDir.resolve("loading"))
      val initStarted = CompletableDeferred<Unit>()
      holdInit(projectDir = projectDir, started = initStarted, released = CompletableDeferred(), disposable = disposable)
      val service = RecentProjectsService.getInstance()
      try {
        requestOperation(projectDir)
        initStarted.await()
        awaitLoad(projectDir, isShown = true)
      }
      finally {
        service.disposeHeldProjects()
      }

      awaitLoad(projectDir, isShown = false)
    }

  // Runs [action] on the project that the service gives the operations for [projectPath].
  private suspend fun <T> onRecentProject(projectPath: Path, action: suspend (Project) -> T): T {
    TrustedProjects.setProjectTrusted(projectPath, isTrusted = true)
    val service = RecentProjectsService.getInstance()
    val result = CompletableDeferred<T>()
    try {
      service.runOnRecentProject(itemOf(projectPath), operation = { project -> result.complete(action(project)) })
      return result.await()
    }
    finally {
      service.disposeHeldProjects()
    }
  }

  // Asks the service for an operation on the trusted project [projectPath]. Completes with the project that the operation gets.
  private fun requestOperation(projectPath: Path): Deferred<Project> {
    TrustedProjects.setProjectTrusted(projectPath, isTrusted = true)
    val operationProject = CompletableDeferred<Project>()
    RecentProjectsService.getInstance().runOnRecentProject(itemOf(projectPath), operation = { operationProject.complete(it) })
    return operationProject
  }

  // The tracker reports from its own coroutine, so the test waits for the report that it expects.
  private suspend fun awaitLoad(projectPath: Path, isShown: Boolean) {
    val item = itemOf(projectPath)
    val loadId = ProgressId.Load(checkNotNull(item.getRecentProjectId()))
    RecentProjectProgressTracker.getInstance().projectsProgress.first { progress ->
      progress.getProgressOf(item).any { it.id == loadId } == isShown
    }
  }

  // Holds the load of the directory-based project [projectDir] in its init activities until [released] completes. The load holds the load
  // mutex of the service meanwhile. The other init activities do not run until [disposable] ends.
  private fun holdInit(projectDir: Path, started: CompletableDeferred<Unit>, released: Deferred<Unit>, disposable: Disposable) {
    // A directory-based project takes the name of its directory.
    val projectName = projectDir.fileName.toString()
    val activity = object : InitProjectActivity {
      override suspend fun run(project: Project) {
        if (project.name == projectName) {
          started.complete(Unit)
          released.await()
        }
      }
    }
    val initProjectActivities = ExtensionPointName<InitProjectActivity>("com.intellij.initProjectActivity")
    ExtensionTestUtil.maskExtensions(initProjectActivities, listOf(activity), disposable, fireEvents = false)
  }

  // The item the welcome screen shows for the project: the recent projects list stores the path in the system-independent form.
  private fun itemOf(projectPath: Path): RecentProjectItem = RecentProjectItem(
    projectPath = projectPath.invariantSeparatorsPathString,
    projectName = projectPath.fileName.toString(),
    displayName = projectPath.fileName.toString(),
    projectGroup = null,
    activationTimestamp = null,
  )

  private fun createDirectoryProject(projectDir: Path): Path {
    createModule(projectDir).resolve(Project.DIRECTORY_STORE_FOLDER).createDirectories().resolve("modules.xml").writeText(modulesXml())
    return projectDir
  }

  // A module with a source root, so the load has roots to read.
  private fun createModule(projectDir: Path): Path {
    projectDir.resolve("src").createDirectories()
    projectDir.resolve("recent.iml").writeText($$"""
      <?xml version="1.0" encoding="UTF-8"?>
      <module type="EMPTY_MODULE" version="4">
        <component name="NewModuleRootManager">
          <content url="file://$MODULE_DIR$">
            <sourceFolder url="file://$MODULE_DIR$/src" isTestSource="false" />
          </content>
          <orderEntry type="sourceFolder" forTests="false" />
        </component>
      </module>
    """.trimIndent())
    return projectDir
  }

  private fun modulesXml(): String = $$"""
    <?xml version="1.0" encoding="UTF-8"?>
    <project version="4">
      <component name="ProjectModuleManager">
        <modules>
          <module fileurl="file://$PROJECT_DIR$/recent.iml" filepath="$PROJECT_DIR$/recent.iml" />
        </modules>
      </component>
    </project>
  """.trimIndent()
}
