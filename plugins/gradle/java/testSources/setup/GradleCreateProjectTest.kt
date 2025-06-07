// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.setup

import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.JAVA
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.withProjectAsync
import com.intellij.util.asDisposable
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.Companion.javaGradleData
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@RegistryKey("ide.activity.tracking.enable.debug", "true")
@SystemProperty("intellij.progress.task.ignoreHeadless", "true")
class GradleCreateProjectTest : GradleCreateProjectTestCase() {

  @Test
  fun `test project re-create`(): Unit = runBlocking {
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

    WorkspaceModelCacheImpl.forceEnableCaching(asDisposable())
    createProjectByWizard(projectInfo)
      .useProjectAsync(save = true) { project ->
        assertProjectState(project, projectInfo)
        assertBuildFiles(projectInfo)
      }
    createProjectByWizard(projectInfo)
      .useProjectAsync(save = true) { project ->
        assertProjectState(project, projectInfo)
        assertBuildFiles(projectInfo)
      }
    deleteProject(projectInfo)
    createProjectByWizard(projectInfo)
      .useProjectAsync { project ->
        assertProjectState(project, projectInfo)
        assertBuildFiles(projectInfo)
      }
  }

  @Test
  fun `test project groovy setting generation`(): Unit = runBlocking {
    val projectInfo = projectInfo("project", useKotlinDsl = false) {
      withJavaBuildFile()
      withSettingsFile {
        setProjectName("project")
      }
    }
    createProjectByWizard(projectInfo)
      .useProjectAsync { project ->
        assertProjectState(project, projectInfo)
        assertBuildFiles(projectInfo)
      }
  }

  @Test
  fun `test project kotlin dsl setting generation`(): Unit = runBlocking {
    val projectInfo = projectInfo("project", useKotlinDsl = true) {
      withJavaBuildFile()
      withSettingsFile {
        setProjectName("project")
      }
    }
    createProjectByWizard(projectInfo)
      .useProjectAsync { project ->
        assertProjectState(project, projectInfo)
        assertBuildFiles(projectInfo)
      }
  }

  @Test
  fun `test project groovy setting generation with groovy-kotlin scripts`(): Unit = runBlocking {
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
        assertBuildFiles(projectInfo)
      }
  }

  @Test
  fun `test project kotlin dsl setting generation with groovy-kotlin scripts`(): Unit = runBlocking {
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
        assertBuildFiles(projectInfo)
      }
  }

  @Test
  @RegistryKey("gradle.daemon.jvm.criteria.new.project", "true")
  fun `test project generation with Gradle daemon JVM criteria`(): Unit = runBlocking {
    val projectInfo = projectInfo("project") {
      withJavaBuildFile()
      withSettingsFile {
        withFoojayPlugin()
        setProjectName("project")
        include("module")
      }
      moduleInfo("project.module", "module") {
        withJavaBuildFile()
      }
    }
    createProjectByWizard(projectInfo)
      .useProjectAsync { project ->
        assertProjectState(project, projectInfo)
        assertBuildFiles(projectInfo)
        assertDaemonJvmProperties(project)
      }
  }

  @Test
  fun `test NPW properties suggestion`(): Unit = runBlocking {
    createProjectByWizard(NEW_EMPTY_PROJECT, numProjectSyncs = 0) {
      baseData!!.name = "project"
      baseData!!.path = testPath.toCanonicalPath()
    }.withProjectAsync { project ->
      assertModules(project, "project")
      createModuleByWizard(project, JAVA) {
        baseData!!.path = testPath.resolve("project").toCanonicalPath()
        javaBuildSystemData!!.buildSystem = "Gradle"
        javaGradleData!!.addSampleCode = false

        Assertions.assertEquals("untitled", baseData!!.name)
        Assertions.assertEquals(GradleDsl.KOTLIN, javaGradleData!!.gradleDsl)
        Assertions.assertNull(javaGradleData!!.parentData)
      }
      createModuleByWizard(project, JAVA) {
        baseData!!.path = testPath.resolve("project").toCanonicalPath()
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
      val projectPath1 = testPath.resolve("project/untitled").toCanonicalPath()
      val projectPath2 = testPath.resolve("project/untitled1").toCanonicalPath()
      val projectNode1 = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, projectPath1)!!
      val projectNode2 = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, projectPath2)!!
      createModuleByWizard(project, JAVA) {
        javaBuildSystemData!!.buildSystem = "Gradle"
        javaGradleData!!.parentData = projectNode1.data
        javaGradleData!!.addSampleCode = false

        Assertions.assertEquals("untitled2", baseData!!.name)
        Assertions.assertEquals(projectPath1, baseData!!.path)
        Assertions.assertEquals(GradleDsl.KOTLIN, javaGradleData!!.gradleDsl)
      }
      createModuleByWizard(project, JAVA) {
        javaBuildSystemData!!.buildSystem = "Gradle"
        javaGradleData!!.parentData = projectNode2.data
        javaGradleData!!.addSampleCode = false

        Assertions.assertEquals("untitled2", baseData!!.name)
        Assertions.assertEquals(projectPath2, baseData!!.path)
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