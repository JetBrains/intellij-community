// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.openapi.Disposable
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfoAssertions.assertProjectStructure
import org.jetbrains.plugins.gradle.testFramework.projectInfo.initProject
import org.jetbrains.plugins.gradle.testFramework.projectInfo.multiModuleProjectInfo

abstract class GradleProjectResolverTestCase : GradleImportingTestCase() {

  /**
   * Gradle project resolver recreates all extensions for sync.
   * Therefore, this function doesn't allow providing an instance of extension.
   * @see org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.createProjectResolvers
   */
  fun addProjectResolverExtension(
    projectResolverExtensionClass: Class<out AbstractTestProjectResolverExtension>,
    parentDisposable: Disposable,
    configure: AbstractTestProjectResolverService.() -> Unit,
  ) = myProject.registerProjectResolverExtension(projectResolverExtensionClass, parentDisposable, configure)

  fun multiModuleProjectInfo(
    useBuildSrc: Boolean = isGradleAtLeast("8.0"),
    includeProjectsWithDuplicatedNames: Boolean = false,
  ): GradleProjectInfo =
    multiModuleProjectInfo(
      currentGradleVersion,
      relativePath = ".",
      useBuildSrc = useBuildSrc,
      includeProjectsWithDuplicatedNames = includeProjectsWithDuplicatedNames,
    )

  fun initProject(projectInfo: GradleProjectInfo) = runBlocking {
      projectInfo.initProject(projectNioPath)
  }

  fun assertProjectStructure(projectInfo: GradleProjectInfo) =
    assertProjectStructure(myProject, projectInfo)

  class TestProjectResolverExtension : AbstractTestProjectResolverExtension() {
    override val serviceClass = TestProjectResolverService::class.java
  }

  class TestProjectResolverService : AbstractTestProjectResolverService()
}