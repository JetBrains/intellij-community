// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.projectInfo

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.settings.ProjectBuildClasspathManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.externalSystem.testFramework.AutoSyncAssertions.AutoSyncStatus
import com.intellij.platform.externalSystem.testFramework.AutoSyncAssertions.assertAutoSyncStatus
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.settings.TestRunner
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.jetbrains.plugins.gradle.util.gradleSettings
import org.junit.jupiter.api.Assertions
import java.nio.file.Path

object GradleProjectInfoAssertions {

  fun assertProjectState(project: Project, testRoot: Path, vararg projectsInfo: GradleProjectInfo) {
    assertAutoSyncStatus(project, AutoSyncStatus.SYNCHRONIZED)
    assertProjectStructure(project, *projectsInfo)
    assertProjectClasspathSaved(project)
    for (projectInfo in projectsInfo) {
      assertBuildFiles(testRoot, projectInfo)
      assertDefaultProjectSettings(project, testRoot, projectInfo)
    }
  }

  fun assertProjectStructure(project: Project, vararg projectsInfo: GradleProjectInfo) {
    Assertions.assertEquals(projectsInfo.size, project.gradleSettings.linkedProjectsSettings.size)
    assertProjectStructure(project, collectProjectInfo(projectsInfo.asList()))
  }

  fun assertPartialProjectStructure(project: Project, projectInfo: GradleProjectInfo, vararg expectedProjectNames: String) {
    assertPartialProjectStructure(project, listOf(projectInfo), *expectedProjectNames)
  }

  fun assertPartialProjectStructure(project: Project, projectInfo: List<GradleProjectInfo>, vararg expectedProjectNames: String) {
    Assertions.assertEquals(projectInfo.size, project.gradleSettings.linkedProjectsSettings.size)
    assertProjectStructure(project, collectProjectInfo(projectInfo, *expectedProjectNames))
  }

  fun collectProjectInfo(projectInfo: List<GradleProjectInfo>): List<GradleProjectInfo> =
    projectInfo + projectInfo.flatMap { collectProjectInfo(it.composites) }

  fun collectProjectInfo(projectInfo: List<GradleProjectInfo>, vararg projectNames: String): List<GradleProjectInfo> {
    val expectedProjectInfo = collectProjectInfo(projectInfo)
      .associateBy { it.projectName }
    return projectNames.map { expectedProjectName ->
      requireNotNull(expectedProjectInfo[expectedProjectName]) {
        "Cannot find project info with name: $expectedProjectName\n" +
        "  projectInfos=" + expectedProjectInfo.keys
      }
    }
  }

  private fun assertProjectStructure(project: Project, projectInfos: List<GradleProjectInfo>) {
    val moduleInfos = projectInfos.flatMap { it.modules }
    val holderModuleNames = moduleInfos.map { it.ideName }
    val sourceSetModuleNames = moduleInfos.flatMap { it.sourceSetModules }
    ModuleAssertions.assertModules(project, holderModuleNames + sourceSetModuleNames)
  }

  fun assertProjectClasspathSaved(project: Project) {
    val cp = project.service<ProjectBuildClasspathManager>()
    Assertions.assertFalse(cp.getProjectBuildClasspath().isEmpty()) {
      "Assert classpath entity is saved to the workspace model"
    }
  }

  fun assertDefaultProjectSettings(project: Project, testRoot: Path, projectInfo: GradleProjectInfo) {
    val externalProjectPath = testRoot.resolve(projectInfo.projectRelativePath).toCanonicalPath()
    val settings = GradleSettings.getInstance(project)
    val projectSettings = settings.getLinkedProjectSettings(externalProjectPath)
    val rootModule = project.modules.first { it.name == projectInfo.rootModule.name }

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

  fun assertBuildFiles(testRoot: Path, projectInfo: GradleProjectInfo) {
    for (compositeInfo in projectInfo.composites) {
      assertBuildFiles(testRoot, compositeInfo)
    }
    for (moduleInfo in projectInfo.modules) {
      val moduleRoot = testRoot.resolve(moduleInfo.relativePath)
        .refreshAndGetVirtualDirectory()
      moduleInfo.files.assertContentsAreEqual(moduleRoot)
    }
  }
}
