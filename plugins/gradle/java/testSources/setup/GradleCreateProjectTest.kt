// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.setup

import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.JAVA
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.idea.IJIgnore
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.utils.module.assertModules
import com.intellij.testFramework.withProjectAsync
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.Companion.javaGradleData
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardStep.GradleDsl
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


class GradleCreateProjectTest : GradleCreateProjectTestCase() {

  @Test
  fun `test project create`() {
    runBlocking {
      val projectInfo = projectInfo("project") {
        withJavaBuildFile()
        withSettingsFile {
          setProjectName("project")
          include("module")
          includeFlat("flat-module")
        }
        moduleInfo("project.module", "module") {
          withJavaBuildFile()
        }
        moduleInfo("project.flat-module", "../flat-module") {
          withJavaBuildFile()
        }
      }
      createProjectByWizard(projectInfo)
        .useProjectAsync(save = true) { project ->
          assertProjectState(project, projectInfo)
        }
      createProjectByWizard(projectInfo)
        .useProjectAsync(save = true) { project ->
          assertProjectState(project, projectInfo)
        }
      deleteProject(projectInfo)
      createProjectByWizard(projectInfo)
        .useProjectAsync { project ->
          assertProjectState(project, projectInfo)
        }
    }
  }

  @Test
  fun `test project groovy setting generation`() {
    runBlocking {
      val projectInfo = projectInfo("project", useKotlinDsl = false) {
        withJavaBuildFile()
        withSettingsFile {
          setProjectName("project")
        }
      }
      createProjectByWizard(projectInfo)
        .useProjectAsync { project ->
          assertProjectState(project, projectInfo)
        }
    }
  }

  @Test
  fun `test project kotlin dsl setting generation`() {
    runBlocking {
      val projectInfo = projectInfo("project", useKotlinDsl = true) {
        withJavaBuildFile()
        withSettingsFile {
          setProjectName("project")
        }
      }
      createProjectByWizard(projectInfo)
        .useProjectAsync { project ->
          assertProjectState(project, projectInfo)
        }
    }
  }

  @Test
  fun `test project groovy setting generation with groovy-kotlin scripts`() {
    runBlocking {
      val projectInfo = projectInfo("project", useKotlinDsl = false) {
        withJavaBuildFile()
        withSettingsFile {
          setProjectName("project")
          include("module1")
          include("module2")
        }
        moduleInfo("project.module1", "module1", useKotlinDsl = true) {
          withJavaBuildFile()
        }
        moduleInfo("project.module2", "module2", useKotlinDsl = false) {
          withJavaBuildFile()
        }
      }
      createProjectByWizard(projectInfo)
        .useProjectAsync { project ->
          assertProjectState(project, projectInfo)
        }
    }
  }

  @IJIgnore(issue = "IDEA-341326")
  @Test
  fun `test project kotlin dsl setting generation with groovy-kotlin scripts`() {
    runBlocking {
      val projectInfo = projectInfo("project", useKotlinDsl = true) {
        withJavaBuildFile()
        withSettingsFile {
          setProjectName("project")
          include("module1")
          include("module2")
        }
        moduleInfo("project.module1", "module1", useKotlinDsl = true) {
          withJavaBuildFile()
        }
        moduleInfo("project.module2", "module2", useKotlinDsl = false) {
          withJavaBuildFile()
        }
      }
      createProjectByWizard(projectInfo)
        .useProjectAsync { project ->
          assertProjectState(project, projectInfo)
        }
    }
  }

  @Test
  fun `test NPW properties suggestion`() {
    runBlocking {
      createProjectByWizard(NEW_EMPTY_PROJECT, wait = false) {
        baseData!!.name = "project"
        baseData!!.path = testRoot.path
      }.withProjectAsync { project ->
        assertModules(project, "project")
        createModuleByWizard(project, JAVA) {
          baseData!!.path = testRoot.path + "/project"
          javaBuildSystemData!!.buildSystem = "Gradle"
          javaGradleData!!.addSampleCode = false

          Assertions.assertEquals("untitled", baseData!!.name)
          Assertions.assertEquals(GradleDsl.KOTLIN, javaGradleData!!.gradleDsl)
          Assertions.assertNull(javaGradleData!!.parentData)
        }
        createModuleByWizard(project, JAVA) {
          baseData!!.path = testRoot.path + "/project"
          javaBuildSystemData!!.buildSystem = "Gradle"
          javaGradleData!!.addSampleCode = false

          Assertions.assertEquals("untitled1", baseData!!.name)
          Assertions.assertEquals(GradleDsl.KOTLIN, javaGradleData!!.gradleDsl)
          Assertions.assertNull(javaGradleData!!.parentData)
        }
        assertModules(
          project, "project",
          "untitled", "untitled.main", "untitled.test",
          "untitled1", "untitled1.main", "untitled1.test"
        )
      }.useProjectAsync { project ->
        val projectNode1 = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, testRoot.path + "/project/untitled")!!
        val projectNode2 = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, testRoot.path + "/project/untitled1")!!
        createModuleByWizard(project, JAVA) {
          javaBuildSystemData!!.buildSystem = "Gradle"
          javaGradleData!!.parentData = projectNode1.data
          javaGradleData!!.addSampleCode = false

          Assertions.assertEquals("untitled2", baseData!!.name)
          Assertions.assertEquals(testRoot.path + "/project/untitled", baseData!!.path)
          Assertions.assertEquals(GradleDsl.KOTLIN, javaGradleData!!.gradleDsl)
        }
        createModuleByWizard(project, JAVA) {
          javaBuildSystemData!!.buildSystem = "Gradle"
          javaGradleData!!.parentData = projectNode2.data
          javaGradleData!!.addSampleCode = false

          Assertions.assertEquals("untitled2", baseData!!.name)
          Assertions.assertEquals(testRoot.path + "/project/untitled1", baseData!!.path)
          Assertions.assertEquals(GradleDsl.KOTLIN, javaGradleData!!.gradleDsl)
        }
        assertModules(
          project, "project",
          "untitled", "untitled.main", "untitled.test",
          "untitled1", "untitled1.main", "untitled1.test",
          "untitled.untitled2", "untitled.untitled2.main", "untitled.untitled2.test",
          "untitled1.untitled2", "untitled1.untitled2.main", "untitled1.untitled2.test"
        )
      }
    }
  }
}