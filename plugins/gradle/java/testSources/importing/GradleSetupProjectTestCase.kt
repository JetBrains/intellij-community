// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.action.AttachExternalProjectAction
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectNotificationAware
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.importProjectAsync
import com.intellij.openapi.externalSystem.util.performAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.runAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.concurrency.asDeferred
import org.jetbrains.plugins.gradle.action.ImportProjectFromScriptAction
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.testFramework.util.buildscript
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.jetbrains.plugins.gradle.util.getProjectDataLoadPromise
import org.jetbrains.plugins.gradle.util.whenResolveTaskStarted
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runners.Parameterized
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes
import com.intellij.testFramework.useProjectAsync as useProjectAsyncImpl

abstract class GradleSetupProjectTestCase : GradleImportingTestCase() {

  private lateinit var testDisposable: Disposable
  private lateinit var expectedImportActionsCounter: AtomicInteger
  private lateinit var actualImportActionsCounter: AtomicInteger

  override fun runInDispatchThread() = false

  override fun setUp() {
    super.setUp()

    testDisposable = Disposer.newDisposable()
    expectedImportActionsCounter = AtomicInteger(0)
    actualImportActionsCounter = AtomicInteger(0)

    whenResolveTaskStarted({ actualImportActionsCounter.incrementAndGet() }, testDisposable)
    AutoImportProjectTracker.enableAutoReloadInTests(testDisposable)
  }

  override fun tearDown() {
    runAll(
      { Disposer.dispose(testDisposable) },
      { super.tearDown() }
    )
  }

  fun generateProject(id: String): ProjectInfo {
    val name = "${System.currentTimeMillis()}-$id"
    createProjectSubFile("$name-composite/settings.gradle", "rootProject.name = '$name-composite'")
    createProjectSubFile("$name-project/settings.gradle", """
      rootProject.name = '$name-project'
      include 'module'
      includeBuild '../$name-composite'
      includeFlat '$name-module'
    """.trimIndent())
    val buildScript = buildscript { withJavaPlugin() }
    createProjectSubFile("$name-composite/build.gradle", buildScript)
    createProjectSubFile("$name-module/build.gradle", buildScript)
    createProjectSubFile("$name-project/module/build.gradle", buildScript)
    val projectFile = createProjectSubFile("$name-project/build.gradle", buildScript)
    return ProjectInfo(projectFile,
                       "$name-project", "$name-project.main", "$name-project.test",
                       "$name-project.module", "$name-project.module.main", "$name-project.module.test",
                       "$name-project.$name-module", "$name-project.$name-module.main",
                       "$name-project.$name-module.test",
                       "$name-composite", "$name-composite.main", "$name-composite.test")
  }

  fun assertProjectState(project: Project, vararg projectsInfo: ProjectInfo) {
    assertProjectStructure(project, *projectsInfo)
    for (projectInfo in projectsInfo) {
      assertProjectSettings(project)
    }
    assertNotificationIsVisible(project, false)
    assertAutoReloadState()
  }

  private fun assertProjectStructure(project: Project, vararg projectsInfo: ProjectInfo) {
    assertModules(project, *projectsInfo.flatMap { it.modules }.toTypedArray())
  }

  private fun assertProjectSettings(project: Project) {
    val externalProjectPath = project.basePath!!
    val settings = ExternalSystemApiUtil.getSettings(project, SYSTEM_ID) as GradleSettings
    val projectSettings = settings.getLinkedProjectSettings(externalProjectPath)!!
    assertEquals(projectSettings.externalProjectPath, externalProjectPath)
    assertEquals(projectSettings.isUseQualifiedModuleNames, true)
    assertEquals(settings.storeProjectFilesExternally, true)
  }

  @Suppress("SameParameterValue")
  private fun assertNotificationIsVisible(project: Project, isNotificationVisible: Boolean) {
    val notificationAware = AutoImportProjectNotificationAware.getInstance(project)
    assertEquals(isNotificationVisible, notificationAware.isNotificationVisible()) {
      notificationAware.getProjectsWithNotification().toString()
    }
  }

  private fun assertAutoReloadState() {
    assertEquals(expectedImportActionsCounter.get(), actualImportActionsCounter.get())
  }

  suspend fun waitForImport(action: suspend () -> Project): Project {
    expectedImportActionsCounter.incrementAndGet()
    val promise = getProjectDataLoadPromise()
    val result = action()
    withTimeout(1.minutes) {
      promise.asDeferred().join()
    }
    return result
  }

  suspend fun importProjectAsync(projectFile: VirtualFile): Project {
    return importProjectAsync(projectFile, SYSTEM_ID)
  }

  suspend fun attachProjectAsync(project: Project, projectFile: VirtualFile): Project {
    performAction(
      action = AttachExternalProjectAction(),
      project = project,
      systemId = SYSTEM_ID,
      selectedFile = projectFile
    )
    return project
  }

  suspend fun attachProjectFromScriptAsync(project: Project, projectFile: VirtualFile): Project {
    performAction(
      action = ImportProjectFromScriptAction(),
      project = project,
      systemId = SYSTEM_ID,
      selectedFile = projectFile
    )
    return project
  }

  suspend fun <R> Project.useProjectAsync(save: Boolean = false, action: suspend (Project) -> R): R {
    return useProjectAsyncImpl(save) {
      try {
        action(it)
      }
      finally {
        cleanupProjectTestResources(this)
      }
    }
  }

  private suspend fun cleanupProjectTestResources(project: Project) {
    withContext(Dispatchers.EDT) {
      runWriteAction {
        val projectJdkTable = ProjectJdkTable.getInstance()
        val settings = GradleSettings.getInstance(project)
        for (projectSettings in settings.linkedProjectsSettings) {
          val gradleJvm = projectSettings.gradleJvm
          val sdk = ExternalSystemJdkUtil.getJdk(project, gradleJvm)
          if (sdk != null) projectJdkTable.removeJdk(sdk)
        }
      }
    }
  }

  data class ProjectInfo(val projectFile: VirtualFile, val modules: List<String>) {
    constructor(projectFile: VirtualFile, vararg modules: String) : this(projectFile, modules.toList())
  }

  companion object {
    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests(): Collection<Array<out String>> = arrayListOf(arrayOf(BASE_GRADLE_VERSION))
  }
}