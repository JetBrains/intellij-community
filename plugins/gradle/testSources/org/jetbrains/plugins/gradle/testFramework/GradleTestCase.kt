// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate")

package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.settings.ProjectBuildClasspathManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.testFramework.utils.vfs.getDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.settings.TestRunner
import org.jetbrains.plugins.gradle.testFramework.util.ModuleInfo
import org.jetbrains.plugins.gradle.testFramework.util.ProjectInfo
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.junit.jupiter.api.Assertions
import java.nio.file.Path
import kotlin.io.path.name


abstract class GradleTestCase : GradleBaseTestCase() {

  suspend fun initProject(projectInfo: ProjectInfo) =
    initProject(testRoot, projectInfo)

  private suspend fun initProject(root: VirtualFile, projectInfo: ProjectInfo) {
    for (compositeInfo in projectInfo.composites) {
      initProject(root, compositeInfo)
    }
    for (moduleInfo in projectInfo.modules) {
      val moduleRoot = edtWriteAction {
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
      edtWriteAction {
        for (moduleInfo in projectInfo.modules) {
          root.deleteRecursively(moduleInfo.relativePath)
        }
      }
    }
  }

  open fun assertProjectState(project: Project, vararg projectsInfo: ProjectInfo) {
    assertNotificationIsVisible(project, false)
    assertProjectStructure(project, *projectsInfo)
    assertProjectClasspathSaved(project)
    for (projectInfo in projectsInfo) {
      assertDefaultProjectSettings(project, projectInfo)
    }
  }

  fun assertProjectStructure(project: Project, vararg projectsInfo: ProjectInfo) {
    val settings = ExternalSystemApiUtil.getSettings(project, SYSTEM_ID)
    Assertions.assertEquals(projectsInfo.size, settings.linkedProjectsSettings.size)
    val modulesInfo = projectsInfo.flatMap { getModuleInfos(it) }
    assertModules(
      project,
      *modulesInfo.map { it.ideName }.toTypedArray(),
      *modulesInfo.flatMap { it.modulesPerSourceSet }.toTypedArray()
    )
  }

  fun assertProjectClasspathSaved(project: Project) {
    val cp = project.service<ProjectBuildClasspathManager>()
    Assertions.assertFalse(cp.getProjectBuildClasspath().isEmpty()) {
      "Assert classpath entity is saved to the workspace model"
    }
  }

  private fun getModuleInfos(projectInfo: ProjectInfo): List<ModuleInfo> {
    return projectInfo.modules + projectInfo.composites.flatMap { getModuleInfos(it) }
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
    Assertions.assertFalse(projectSettings.isResolveExternalAnnotations)
    Assertions.assertTrue(projectSettings.delegatedBuild)
    Assertions.assertEquals(TestRunner.GRADLE, projectSettings.testRunner)
    Assertions.assertTrue(projectSettings.isUseQualifiedModuleNames)
  }

  fun projectInfo(
    relativePath: String,
    useKotlinDsl: Boolean = true,
    configure: ProjectInfo.Builder.() -> Unit = {}
  ) = ProjectInfo.create(
    Path.of(relativePath).name,
    relativePath,
    useKotlinDsl,
    configure
  )

  fun getSimpleProjectInfo(relativePath: String) =
    projectInfo(relativePath) {
      withSettingsFile {
        setProjectName(Path.of(relativePath).name)
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

  fun ModuleInfo.Builder.withFile(relativePath: String, content: String) {
    filesConfiguration.withFile(relativePath, content)
  }

  fun ModuleInfo.Builder.withSettingsFile(configure: GradleSettingScriptBuilder<*>.() -> Unit) {
    filesConfiguration.withSettingsFile(gradleVersion, useKotlinDsl = useKotlinDsl, configure = configure)
  }

  open fun ModuleInfo.Builder.withBuildFile(configure: GradleBuildScriptBuilder<*>.() -> Unit) {
    filesConfiguration.withBuildFile(gradleVersion, useKotlinDsl = useKotlinDsl, configure = configure)
  }
}