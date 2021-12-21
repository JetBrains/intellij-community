// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.action.AttachExternalProjectAction
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectNotificationAware
import com.intellij.openapi.externalSystem.importing.ExternalSystemSetupProjectTest
import com.intellij.openapi.externalSystem.importing.ExternalSystemSetupProjectTestCase
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.action.ImportProjectFromScriptAction
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder.Companion.buildscript
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.jetbrains.plugins.gradle.util.waitForProjectReload
import org.jetbrains.plugins.gradle.util.whenResolveTaskStarted
import org.junit.runners.Parameterized
import java.util.concurrent.atomic.AtomicInteger

class GradleSetupProjectTest : ExternalSystemSetupProjectTest, GradleImportingTestCase() {

  private lateinit var testDisposable: Disposable
  private lateinit var expectedImportActionsCounter: AtomicInteger
  private lateinit var actualImportActionsCounter: AtomicInteger

  override fun setUp() {
    super.setUp()

    testDisposable = Disposer.newDisposable()
    expectedImportActionsCounter = AtomicInteger(0)
    actualImportActionsCounter = AtomicInteger(0)

    whenResolveTaskStarted({ actualImportActionsCounter.incrementAndGet() }, testDisposable)
    AutoImportProjectTracker.enableAutoReloadInTests(testDisposable)
  }

  override fun tearDown() {
    Disposer.dispose(testDisposable)

    super.tearDown()
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

  override fun assertDefaultProjectState(project: Project) {
    val notificationAware = AutoImportProjectNotificationAware.getInstance(myProject)
    invokeAndWaitIfNeeded {
      assertEmpty(notificationAware.getProjectsWithNotification())
    }
    assertEquals(expectedImportActionsCounter.get(), actualImportActionsCounter.get())
  }

  override fun waitForImport(action: () -> Project): Project {
    expectedImportActionsCounter.incrementAndGet()
    return waitForProjectReload(action)
  }

  override fun attachProject(project: Project, projectFile: VirtualFile): Project {
    AttachExternalProjectAction().perform(project, selectedFile = projectFile)
    return project
  }

  override fun attachProjectFromScript(project: Project, projectFile: VirtualFile): Project {
    ImportProjectFromScriptAction().perform(project, selectedFile = projectFile)
    return project
  }

  override fun cleanupProjectTestResources(project: Project) {
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

    fun removeGradleJvmSdk(project: Project) {
      ApplicationManager.getApplication().invokeAndWait {
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