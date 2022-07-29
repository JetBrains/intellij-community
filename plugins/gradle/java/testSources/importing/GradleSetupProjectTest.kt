// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.action.AttachExternalProjectAction
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectNotificationAware
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.importing.ExternalSystemSetupProjectTest
import com.intellij.openapi.externalSystem.importing.ExternalSystemSetupProjectTestCase
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
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
import org.junit.runners.Parameterized
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

class GradleSetupProjectTest : ExternalSystemSetupProjectTest, GradleImportingTestCase() {
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
    try {
      Disposer.dispose(testDisposable)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  override fun getSystemId(): ProjectSystemId = SYSTEM_ID

  override fun generateProject(id: String): ExternalSystemSetupProjectTestCase.ProjectInfo {
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
    return ExternalSystemSetupProjectTestCase.ProjectInfo(projectFile,
                                                          "$name-project", "$name-project.main", "$name-project.test",
                                                          "$name-project.module", "$name-project.module.main", "$name-project.module.test",
                                                          "$name-project.$name-module", "$name-project.$name-module.main",
                                                          "$name-project.$name-module.test",
                                                          "$name-composite", "$name-composite.main", "$name-composite.test")
  }

  override fun assertDefaultProjectSettings(project: Project) {
    val externalProjectPath = project.basePath!!
    val settings = ExternalSystemApiUtil.getSettings(project, SYSTEM_ID) as GradleSettings
    val projectSettings = settings.getLinkedProjectSettings(externalProjectPath)!!
    assertEquals(projectSettings.externalProjectPath, externalProjectPath)
    assertEquals(projectSettings.isUseQualifiedModuleNames, true)
    assertEquals(settings.storeProjectFilesExternally, true)
  }

  override suspend fun assertDefaultProjectState(project: Project) {
    val notificationAware = AutoImportProjectNotificationAware.getInstance(myProject)
    withContext(Dispatchers.EDT) {
      assertEmpty(notificationAware.getProjectsWithNotification())
    }
    assertEquals(expectedImportActionsCounter.get(), actualImportActionsCounter.get())
  }

  override suspend fun waitForImport(action: suspend () -> Project): Project {
    expectedImportActionsCounter.incrementAndGet()
    val promise = getProjectDataLoadPromise()
    val result = action()
    withTimeout(1.minutes) {
      promise.asDeferred().join()
    }
    return result
  }

  override suspend fun attachProject(project: Project, projectFile: VirtualFile): Project {
    performAction(AttachExternalProjectAction(), project, selectedFile = projectFile)
    return project
  }

  override suspend fun attachProjectFromScript(project: Project, projectFile: VirtualFile): Project {
    performAction(ImportProjectFromScriptAction(), project, selectedFile = projectFile)
    return project
  }

  override suspend fun cleanupProjectTestResources(project: Project) {
    super.cleanupProjectTestResources(project)
    removeGradleJvmSdk(project)
  }

  companion object {
    /**
     * It's sufficient to run the test against one gradle version
     */
    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests(): Collection<Array<out String>> = arrayListOf(arrayOf(BASE_GRADLE_VERSION))

    suspend fun removeGradleJvmSdk(project: Project) {
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
  }
}