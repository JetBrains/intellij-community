// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.setup

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.writeText
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.util.asDisposable
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@RegistryKey("ide.activity.tracking.enable.debug", "true")
@SystemProperty("intellij.progress.task.ignoreHeadless", "true")
class GradleOpenProjectTest : GradleOpenProjectTestCase() {

  @Test
  fun `test project open`(): Unit = runBlocking {
    val projectInfo = getComplexProjectInfo("project")
    initProject(projectInfo)

    openProject("project")
      .useProjectAsync {
        assertProjectState(it, projectInfo)
      }
  }

  @Test
  fun `test project import`(): Unit = runBlocking {
    val projectInfo = getComplexProjectInfo("project")
    initProject(projectInfo)

    importProject(projectInfo)
      .useProjectAsync {
        assertProjectState(it, projectInfo)
      }
  }

  @Test
  fun `test project re-open`(): Unit = runBlocking {
    val projectInfo = getComplexProjectInfo("project")
    val linkedProjectInfo = getComplexProjectInfo("linked_project")
    initProject(projectInfo)
    initProject(linkedProjectInfo)

    WorkspaceModelCacheImpl.forceEnableCaching(asDisposable())
    openProject("project")
      .useProjectAsync(save = true) {
        assertProjectState(it, projectInfo)
        linkProject(it, "linked_project")
        assertProjectState(it, projectInfo, linkedProjectInfo)
      }

    openProject("project", numProjectSyncs = 0)
      .useProjectAsync {
        assertProjectState(it, projectInfo, linkedProjectInfo)
      }
  }

  @Test
  fun `test project re-import deprecation`(): Unit = runBlocking {
    val projectInfo = getComplexProjectInfo("project")
    val linkedProjectInfo = getComplexProjectInfo("linked_project")
    initProject(projectInfo)
    initProject(linkedProjectInfo)

    WorkspaceModelCacheImpl.forceEnableCaching(asDisposable())
    openProject("project")
      .useProjectAsync(save = true) {
        assertProjectState(it, projectInfo)
        linkProject(it, "linked_project")
        assertProjectState(it, projectInfo, linkedProjectInfo)
      }

    importProject(projectInfo, numProjectSyncs = 0)
      .useProjectAsync {
        assertProjectState(it, projectInfo, linkedProjectInfo)
      }
  }

  @Test
  fun `test attach project`(): Unit = runBlocking {
    val projectInfo = getComplexProjectInfo("project")
    val linkedProjectInfo1 = getComplexProjectInfo("linked_project1")
    val linkedProjectInfo2 = getComplexProjectInfo("linked_project2")
    initProject(projectInfo)
    initProject(linkedProjectInfo1)
    initProject(linkedProjectInfo2)

    openProject("project")
      .useProjectAsync {
        assertProjectState(it, projectInfo)

        attachProject(it, "linked_project1")
        assertProjectState(it, projectInfo, linkedProjectInfo1)

        attachProjectFromScript(it, "linked_project2")
        assertProjectState(it, projectInfo, linkedProjectInfo1, linkedProjectInfo2)
      }
  }

  @Test
  fun `test attach project to Gradle and Maven`(): Unit = runBlocking {
    val projectInfo = getComplexProjectInfo("project")
    val linkedProjectInfo = getComplexProjectInfo("linked_project")
    initProject(projectInfo)
    initProject(linkedProjectInfo)

    edtWriteAction {
      testRoot.createFile("linked_project/pom.xml")
        .writeText("""
            <?xml version="1.0"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>test</groupId>
              <artifactId>maven_project</artifactId>
              <version>1</version>
            </project>
          """.trimIndent())
    }

    openProject("project")
      .useProjectAsync { it ->
        assertProjectState(it, projectInfo)

        attachProject(it, "linked_project")
        assertProjectState(it, projectInfo, linkedProjectInfo)

        attachMavenProject(it, "linked_project")
        val existingModuleNames = it.modules.map { it.name }
        Assertions.assertTrue(existingModuleNames.contains("maven_project"), "Maven linked project not found")
        val linkedProjects = existingModuleNames.filter { it.contains("linked_project") }
        Assertions.assertTrue(linkedProjects.isEmpty(), "Unexpected Gradle linked projects found: $linkedProjects")

        attachProject(it, "linked_project")
        assertProjectState(it, projectInfo, linkedProjectInfo)
      }
  }

  @Test
  fun `test auto-link project without project model`(): Unit = runBlocking {
    val projectInfo = getSimpleProjectInfo("project")
    initProject(projectInfo)

    edtWriteAction {
      testRoot.createFile("project/.idea/compiler.xml")
        .writeText("""
            |<?xml version="1.0" encoding="UTF-8"?>
            |<project version="4">
            |  <component name="CompilerConfiguration">
            |    <bytecodeTargetLevel target="14" />
            |  </component>
            |</project>
          """.trimMargin())
    }

    openProject("project")
      .useProjectAsync { project ->
        val gradleSettings = GradleSettings.getInstance(project)
        Assertions.assertEquals(1, gradleSettings.linkedProjectsSettings.size)
      }
  }

  @Test
  fun `test don't auto-link project with project model`(): Unit = runBlocking {
    val projectInfo = getSimpleProjectInfo("project")
    initProject(projectInfo)

    edtWriteAction {
      testRoot.createFile("project/.idea/compiler.xml")
        .writeText("""
            |<?xml version="1.0" encoding="UTF-8"?>
            |<project version="4">
            |  <component name="CompilerConfiguration">
            |    <bytecodeTargetLevel target="14" />
            |  </component>
            |</project>
          """.trimMargin())
      testRoot.createFile("project/.idea/modules.xml")
        .writeText("""
            |<?xml version="1.0" encoding="UTF-8"?>
            |<project version="4">
            |  <component name="ProjectModuleManager">
            |    <modules>
            |      <module fileurl="file://${'$'}PROJECT_DIR${'$'}/project.iml" filepath="${'$'}PROJECT_DIR${'$'}/project.iml" />
            |    </modules>
            |  </component>
            |</project>
          """.trimMargin())
    }

    openProject("project", numProjectSyncs = 0)
      .useProjectAsync { project ->
        val gradleSettings = GradleSettings.getInstance(project)
        Assertions.assertEquals(0, gradleSettings.linkedProjectsSettings.size)
      }
  }

  @Test
  fun `test auto-link project from new gradle_xml`(): Unit = runBlocking {
    val projectInfo = getSimpleProjectInfo("project")
    initProject(projectInfo)

    edtWriteAction {
      testRoot.createFile("project/project.iml")
        .writeText("""
            |<?xml version="1.0" encoding="UTF-8"?>
            |<module type="GENERAL_MODULE" version="4">
            |  <component name="NewModuleRootManager" inherit-compiler-output="true">
            |    <exclude-output />
            |    <content url="file://${'$'}MODULE_DIR${'$'}" />
            |    <orderEntry type="sourceFolder" forTests="false" />
            |  </component>
            |</module>
          """.trimMargin())
      testRoot.createFile("project/.idea/modules.xml")
        .writeText("""
            |<?xml version="1.0" encoding="UTF-8"?>
            |<project version="4">
            |  <component name="ProjectModuleManager">
            |    <modules>
            |      <module fileurl="file://${'$'}PROJECT_DIR${'$'}/project.iml" filepath="${'$'}PROJECT_DIR${'$'}/project.iml" />
            |    </modules>
            |  </component>
            |</project>
          """.trimMargin())
    }

    openProject("project", numProjectSyncs = 0)
      .useProjectAsync { project ->
        assertModules(project, "project")
        awaitProjectConfiguration(project) {
          edtWriteAction {
            testRoot.createFile("project/.idea/gradle.xml")
              .writeText("""
                  |<?xml version="1.0" encoding="UTF-8"?>
                  |<project version="4">
                  |    <component name="GradleSettings">
                  |        <option name="linkedExternalProjectsSettings">
                  |            <GradleProjectSettings>
                  |                <option name="externalProjectPath" value="${'$'}PROJECT_DIR${'$'}" />
                  |                <option name="gradleHome" value="" />
                  |                <option name="gradleJvm" value="$gradleJvm" />
                  |                <option name="modules">
                  |                    <set>
                  |                        <option value="${'$'}PROJECT_DIR${'$'}" />
                  |                    </set>
                  |                </option>
                  |            </GradleProjectSettings>
                  |        </option>
                  |    </component>
                  |</project>
                """.trimMargin())
          }
          PlatformTestUtil.saveProject(project)
        }
        assertNotificationIsVisible(project, false)
        assertModules(project, "project", "project.main", "project.test")
      }
  }

  @Test
  fun `test open project with inspection profiles`(): Unit = runBlocking {
    val projectInfo = getSimpleProjectInfo("project")
    initProject(projectInfo)

    edtWriteAction {
      testRoot.createFile("project/.idea/inspectionProfiles/myInspections.xml")
        .writeText("""
            |<component name="InspectionProjectProfileManager">
            |  <profile version="1.0">
            |    <option name="myName" value="myInspections" />
            |    <option name="myLocal" value="true" />
            |    <inspection_tool class="MultipleRepositoryUrls" enabled="true" level="ERROR" enabled_by_default="true" />
            |  </profile>
            |</component>
          """.trimMargin())
      testRoot.createFile("project/.idea/inspectionProfiles/profiles_settings.xml")
        .writeText("""
            |<component name="InspectionProjectProfileManager">
            |  <settings>
            |    <option name="PROJECT_PROFILE" value="myInspections" />
            |    <version value="1.0" />
            |  </settings>
            |</component>
          """.trimMargin())
    }

    openProject("project")
      .useProjectAsync { project ->
        ProjectInspectionProfileManager.getInstance(project).forceLoadSchemes()
        val currentProfile = InspectionProfileManager.getInstance(project).currentProfile
        currentProfile.ensureInitialized(project)
        Assertions.assertEquals("myInspections", currentProfile.name)
        val toolState = currentProfile.getToolDefaultState("MultipleRepositoryUrls", project)
        Assertions.assertEquals(HighlightDisplayLevel.ERROR, toolState.level)

        assertProjectState(project, projectInfo)
      }
  }
}