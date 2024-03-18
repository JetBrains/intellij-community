// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import org.jetbrains.plugins.gradle.service.project.*
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.junit.Test
import java.util.function.Predicate

class GradlePartialImportingTest : GradlePartialImportingTestCase() {

  @Test
  fun `test re-import with partial project data resolve`() {
    createProjectSubFile("gradle.properties", """
      |prop_loaded_1=val1
      |prop_finished_2=val2
    """.trimMargin())
    importProject {
      withJavaPlugin()
    }

    assertReceivedModels(
      projectPath, "project",
      mapOf("name" to "project", "prop_loaded_1" to "val1"),
      mapOf("name" to "project", "prop_finished_2" to "val2")
    )

    val initialProjectStructure = ProjectDataManager.getInstance()
      .getExternalProjectData(myProject, SYSTEM_ID, projectPath)!!
      .externalProjectStructure!!
      .graphCopy()

    createProjectSubFile("gradle.properties", """
      |prop_loaded_1=val1_inc
      |prop_finished_2=val2_inc
    """.trimMargin())

    cleanupBeforeReImport()
    ExternalSystemUtil.refreshProject(
      projectPath,
      ImportSpecBuilder(myProject, SYSTEM_ID)
        .use(ProgressExecutionMode.MODAL_SYNC)
        .projectResolverPolicy(
          GradlePartialResolverPolicy(Predicate { it is TestPartialProjectResolverExtension })
        )
    )

    assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
      }
    }

    assertReceivedModels(
      projectPath, "project",
      mapOf("name" to "project", "prop_loaded_1" to "val1_inc"),
      mapOf("name" to "project", "prop_finished_2" to "val2_inc")
    )

    val projectStructureAfterIncrementalImport = ProjectDataManager.getInstance()
      .getExternalProjectData(myProject, SYSTEM_ID, projectPath)!!
      .externalProjectStructure!!
      .graphCopy()

    assertEquals(initialProjectStructure, projectStructureAfterIncrementalImport)
  }

  @Test
  fun `test composite project partial re-import`() {
    createBuildFile("buildSrc") {
      withPlugin("groovy")
      addImplementationDependency(code("gradleApi()"))
      addImplementationDependency(code("localGroovy()"))
    }
    createProjectSubFile("gradle.properties", """
      |prop_loaded_1=val1
      |prop_finished_2=val2
    """.trimMargin())
    createSettingsFile {
      setProjectName("project")
      includeBuild("includedBuild")
    }
    createSettingsFile("includedBuild") {
      setProjectName("includedBuild")
      include("subProject")
    }
    createProjectSubDir("includedBuild/subProject")
    createBuildFile("includedBuild/buildSrc") {
      withPlugin("groovy")
      addImplementationDependency(code("gradleApi()"))
      addImplementationDependency(code("localGroovy()"))
    }
    createProjectSubFile("includedBuild/gradle.properties", """
      |prop_loaded_included=val1
      |prop_finished_included=val2
    """.trimMargin())
    importProject("")

    // since Gradle 6.8, included (of the "main" build) builds became visible for `buildSrc` project.
    // Before Gradle 8.0 there are separate TAPI requests per `buildSrc` project in a composite
    // and hence included build models can be handled more than once
    val includedBuildModelsReceivedQuantity = if (isGradleOlderThan("6.8") || isGradleAtLeast("8.0")) 1 else 2

    assertReceivedModels(
      projectPath, "project",
      mapOf("name" to "project", "prop_loaded_1" to "val1"),
      mapOf("name" to "project", "prop_finished_2" to "val2")
    )
    assertReceivedModels(
      path("buildSrc"), "buildSrc",
      mapOf("name" to "buildSrc"),
      mapOf("name" to "buildSrc")
    )
    assertReceivedModels(
      path("includedBuild"), "includedBuild",
      mapOf("name" to "includedBuild", "prop_loaded_included" to "val1"),
      mapOf("name" to "includedBuild", "prop_finished_included" to "val2"),
      includedBuildModelsReceivedQuantity
    )
    assertReceivedModels(
      path("includedBuild"), "subProject",
      mapOf("name" to "subProject", "prop_loaded_included" to "val1"),
      mapOf("name" to "subProject", "prop_finished_included" to "val2"),
      includedBuildModelsReceivedQuantity
    )
    assertReceivedModels(
      path("includedBuild/buildSrc"), "buildSrc",
      mapOf("name" to "buildSrc"),
      mapOf("name" to "buildSrc")
    )

    val initialProjectStructure = ProjectDataManager.getInstance()
      .getExternalProjectData(myProject, SYSTEM_ID, projectPath)!!
      .externalProjectStructure!!
      .graphCopy()

    createProjectSubFile("gradle.properties", """
      |prop_loaded_1=val1_inc
      |prop_finished_2=val2_inc
    """.trimMargin())
    createProjectSubFile("includedBuild/gradle.properties", """
      |prop_loaded_included=val1_1
      |prop_finished_included=val2_2
    """.trimMargin())

    cleanupBeforeReImport()
    ExternalSystemUtil.refreshProject(
      projectPath,
      ImportSpecBuilder(myProject, SYSTEM_ID)
        .use(ProgressExecutionMode.MODAL_SYNC)
        .projectResolverPolicy(
          GradlePartialResolverPolicy(Predicate { it is TestPartialProjectResolverExtension })
        )
    )

    assertReceivedModels(
      projectPath, "project",
      mapOf("name" to "project", "prop_loaded_1" to "val1_inc"),
      mapOf("name" to "project", "prop_finished_2" to "val2_inc")
    )
    assertReceivedModels(
      path("buildSrc"), "buildSrc",
      mapOf("name" to "buildSrc"),
      mapOf("name" to "buildSrc")
    )
    assertReceivedModels(
      path("includedBuild"), "includedBuild",
      mapOf("name" to "includedBuild", "prop_loaded_included" to "val1_1"),
      mapOf("name" to "includedBuild", "prop_finished_included" to "val2_2"),
      includedBuildModelsReceivedQuantity
    )
    assertReceivedModels(
      path("includedBuild"), "subProject",
      mapOf("name" to "subProject", "prop_loaded_included" to "val1_1"),
      mapOf("name" to "subProject", "prop_finished_included" to "val2_2"),
      includedBuildModelsReceivedQuantity
    )
    assertReceivedModels(
      path("includedBuild/buildSrc"), "buildSrc",
      mapOf("name" to "buildSrc"),
      mapOf("name" to "buildSrc")
    )

    val projectStructureAfterIncrementalImport = ProjectDataManager.getInstance()
      .getExternalProjectData(myProject, SYSTEM_ID, projectPath)!!
      .externalProjectStructure!!
      .graphCopy()

    assertEquals(initialProjectStructure, projectStructureAfterIncrementalImport)
  }

  @Test
  fun `test import cancellation on project loaded phase`() {
    createProjectSubFile("gradle.properties", """
      |prop_loaded_1=val1
      |prop_finished_2=val2
    """.trimMargin())
    importProject {
      withJavaPlugin()
    }

    assertReceivedModels(
      projectPath, "project",
      mapOf("name" to "project", "prop_loaded_1" to "val1"),
      mapOf("name" to "project", "prop_finished_2" to "val2")
    )

    createProjectSubFile("gradle.properties", """
      |prop_loaded_1=error
      |prop_finished_2=val22
    """.trimMargin())

    cleanupBeforeReImport()
    ExternalSystemUtil.refreshProject(projectPath, ImportSpecBuilder(myProject, SYSTEM_ID).use(ProgressExecutionMode.MODAL_SYNC))

    if (isGradleAtLeast("4.8")) {
      assertReceivedModels(
        projectPath, "project",
        mapOf("name" to "project", "prop_loaded_1" to "error")
      )
    }
    else {
      assertReceivedModels(
        projectPath, "project",
        mapOf("name" to "project", "prop_loaded_1" to "error"),
        mapOf("name" to "project", "prop_finished_2" to "val22")
      )
    }
  }
}
