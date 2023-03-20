// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate")

package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectNotificationAware
import com.intellij.openapi.externalSystem.autolink.UnlinkedProjectStartupActivity
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.utils.module.assertModules
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.testFramework.utils.vfs.getDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.service.project.open.linkAndRefreshGradleProject
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.settings.TestRunner
import org.jetbrains.plugins.gradle.testFramework.util.ModuleInfo
import org.jetbrains.plugins.gradle.testFramework.util.ProjectInfo
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.junit.jupiter.api.Assertions
import kotlin.io.path.name


abstract class GradleTestCase : GradleBaseTestCase() {

  suspend fun <R> awaitProjectReload(wait: Boolean = true, action: suspend () -> R): R {
    if (!wait) {
      return action()
    }
    return gradleReload.withAllowedReload {
      org.jetbrains.plugins.gradle.util.awaitProjectReload {
        action()
      }
    }
  }

  suspend fun openProject(relativePath: String, wait: Boolean = true): Project {
    val projectRoot = testRoot.getDirectory(relativePath)
    return closeOpenedProjectsIfFailAsync {
      awaitProjectReload(wait = wait) {
        openProjectAsync(projectRoot, UnlinkedProjectStartupActivity())
      }
    }
  }

  suspend fun linkProject(project: Project, relativePath: String) {
    val projectRoot = testRoot.getDirectory(relativePath)
    awaitProjectReload {
      linkAndRefreshGradleProject(projectRoot.path, project)
    }
  }

  suspend fun reloadProject(
    project: Project,
    relativePath: String,
    configure: ImportSpecBuilder.() -> Unit
  ) {
    awaitProjectReload {
      ExternalSystemUtil.refreshProject(
        testRoot.getDirectory(relativePath).path,
        ImportSpecBuilder(project, SYSTEM_ID)
          .apply(configure)
      )
    }
  }

  suspend fun initProject(projectInfo: ProjectInfo) =
    initProject(testRoot, projectInfo)

  private suspend fun initProject(root: VirtualFile, projectInfo: ProjectInfo) {
    for (compositeInfo in projectInfo.composites) {
      initProject(root, compositeInfo)
    }
    for (moduleInfo in projectInfo.modules) {
      val moduleRoot = writeAction {
        root.findOrCreateDirectory(moduleInfo.relativePath)
      }
      moduleInfo.filesConfiguration.createFiles(moduleRoot)
    }
  }

  suspend fun deleteProject(projectInfo: ProjectInfo) =
    deleteProject(testRoot, projectInfo)

  private suspend fun deleteProject(root: VirtualFile, projectInfo: ProjectInfo) {
    for (compositeInfo in projectInfo.composites) {
      deleteProject(root, compositeInfo)
    }
    withContext(Dispatchers.EDT) {
      writeAction {
        for (moduleInfo in projectInfo.modules) {
          root.deleteRecursively(moduleInfo.relativePath)
        }
      }
    }
  }

  open fun assertProjectState(project: Project, vararg projectsInfo: ProjectInfo) {
    assertReloadState()
    assertNotificationIsVisible(project, false)
    assertProjectStructure(project, *projectsInfo)
    for (projectInfo in projectsInfo) {
      assertDefaultProjectSettings(project, projectInfo)
      assertBuildFiles(projectInfo)
    }
  }

  fun assertReloadState() {
    gradleReload.assertReloadState()
  }

  fun assertProjectStructure(project: Project, vararg projectsInfo: ProjectInfo) {
    val settings = ExternalSystemApiUtil.getSettings(project, SYSTEM_ID)
    Assertions.assertEquals(projectsInfo.size, settings.linkedProjectsSettings.size)
    val modulesInfo = projectsInfo.flatMap { getModulesInfo(it) }
    assertModules(
      project,
      *modulesInfo.map { it.ideName }.toTypedArray(),
      *modulesInfo.flatMap { it.modulesPerSourceSet }.toTypedArray()
    )
  }

  private fun getModulesInfo(projectInfo: ProjectInfo): List<ModuleInfo> {
    return projectInfo.modules + projectInfo.composites.flatMap { getModulesInfo(it) }
  }

  fun assertDefaultProjectSettings(project: Project, projectInfo: ProjectInfo) {
    val externalProjectPath = testRoot.getDirectory(projectInfo.relativePath).path
    val settings = GradleSettings.getInstance(project)
    val projectSettings = settings.getLinkedProjectSettings(externalProjectPath)
    val rootModule = project.modules.first { it.name == projectInfo.name }

    Assertions.assertTrue(ExternalSystemApiUtil.isExternalSystemAwareModule(SYSTEM_ID, rootModule))
    Assertions.assertTrue(ExternalStorageConfigurationManager.getInstance(project).isEnabled)
    requireNotNull(projectSettings) { "Cannot find project settings for $externalProjectPath" }
    Assertions.assertEquals(projectSettings.externalProjectPath, externalProjectPath)
    Assertions.assertTrue(settings.storeProjectFilesExternally)
    Assertions.assertTrue(projectSettings.isResolveModulePerSourceSet)
    Assertions.assertTrue(projectSettings.isResolveExternalAnnotations)
    Assertions.assertTrue(projectSettings.delegatedBuild)
    Assertions.assertEquals(TestRunner.GRADLE, projectSettings.testRunner)
    Assertions.assertTrue(projectSettings.isUseQualifiedModuleNames)
  }

  fun assertBuildFiles(projectInfo: ProjectInfo) {
    for (compositeInfo in projectInfo.composites) {
      assertBuildFiles(compositeInfo)
    }
    for (moduleInfo in projectInfo.modules) {
      val moduleRoot = testRoot.getDirectory(moduleInfo.relativePath)
      moduleInfo.filesConfiguration.assertContentsAreEqual(moduleRoot)
    }
  }

  fun assertNotificationIsVisible(project: Project, isNotificationVisible: Boolean) {
    val notificationAware = AutoImportProjectNotificationAware.getInstance(project)
    Assertions.assertEquals(isNotificationVisible, notificationAware.isNotificationVisible()) {
      notificationAware.getProjectsWithNotification().toString()
    }
  }

  fun projectInfo(
    relativePath: String,
    useKotlinDsl: Boolean = true,
    configure: ProjectInfo.Builder.() -> Unit = {}
  ) = ProjectInfo.create(
    relativePath.toNioPath().name,
    relativePath,
    useKotlinDsl,
    configure
  )

  fun getSimpleProjectInfo(relativePath: String) =
    projectInfo(relativePath) {
      withSettingsFile {
        setProjectName(relativePath.toNioPath().name)
      }
      withBuildFile {
        withJavaPlugin()
      }
    }

  fun getComplexProjectInfo(projectName: String) =
    projectInfo(projectName) {
      withSettingsFile {
        setProjectName(projectName)
        include("module")
        includeFlat("$projectName-flat-module")
        includeBuild("../$projectName-composite")
      }
      withBuildFile {
        withJavaPlugin()
      }
      moduleInfo("$projectName.module", "module") {
        withBuildFile {
          withJavaPlugin()
        }
      }
      moduleInfo("$projectName.$projectName-flat-module", "../$projectName-flat-module") {
        withBuildFile {
          withJavaPlugin()
        }
      }
      compositeInfo("$projectName-composite", "../$projectName-composite") {
        withSettingsFile {
          setProjectName("$projectName-composite")
        }
        withBuildFile {
          withJavaPlugin()
        }
      }
    }

  fun ModuleInfo.Builder.withSettingsFile(configure: GradleSettingScriptBuilder<*>.() -> Unit) {
    filesConfiguration.withSettingsFile(useKotlinDsl = useKotlinDsl, configure = configure)
  }

  fun ModuleInfo.Builder.withBuildFile(configure: GradleBuildScriptBuilder<*>.() -> Unit) {
    filesConfiguration.withBuildFile(gradleVersion, useKotlinDsl = useKotlinDsl, configure = configure)
  }
}