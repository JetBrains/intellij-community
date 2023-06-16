// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.setup

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.vfs.writeText
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.utils.module.assertModules
import com.intellij.testFramework.utils.vfs.createFile
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


class GradleOpenProjectTest : GradleOpenProjectTestCase() {

  @Test
  fun `test project open`() {
    runBlocking {
      val projectInfo = getComplexProjectInfo("project")
      initProject(projectInfo)

      openProject("project")
        .useProjectAsync {
          assertProjectState(it, projectInfo)
        }
    }
  }

  @Test
  fun `test project import`() {
    runBlocking {
      val projectInfo = getComplexProjectInfo("project")
      initProject(projectInfo)

      importProject(projectInfo)
        .useProjectAsync {
          assertProjectState(it, projectInfo)
        }
    }
  }

  @Test
  fun `test project re-open`() {
    runBlocking {
      val projectInfo = getComplexProjectInfo("project")
      val linkedProjectInfo = getComplexProjectInfo("linked_project")
      initProject(projectInfo)
      initProject(linkedProjectInfo)

      openProject("project")
        .useProjectAsync(save = true) {
          assertProjectState(it, projectInfo)
          linkProject(it, "linked_project")
          assertProjectState(it, projectInfo, linkedProjectInfo)
        }

      openProject("project", wait = false)
        .useProjectAsync {
          assertProjectState(it, projectInfo, linkedProjectInfo)
        }
    }
  }

  @Test
  fun `test project re-import deprecation`() {
    runBlocking {
      val projectInfo = getComplexProjectInfo("project")
      val linkedProjectInfo = getComplexProjectInfo("linked_project")
      initProject(projectInfo)
      initProject(linkedProjectInfo)

      openProject("project")
        .useProjectAsync(save = true) {
          assertProjectState(it, projectInfo)
          linkProject(it, "linked_project")
          assertProjectState(it, projectInfo, linkedProjectInfo)
        }

      importProject(projectInfo, wait = false)
        .useProjectAsync {
          assertProjectState(it, projectInfo, linkedProjectInfo)
        }
    }
  }

  @Test
  fun `test attach project`() {
    runBlocking {
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
  }

  @Test
  fun `test auto-link project without project model`() {
    runBlocking {
      val projectInfo = getSimpleProjectInfo("project")
      initProject(projectInfo)

      writeAction {
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
  }

  @Test
  fun `test don't auto-link project with project model`() {
    runBlocking {
      val projectInfo = getSimpleProjectInfo("project")
      initProject(projectInfo)

      writeAction {
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

      openProject("project", wait = false)
        .useProjectAsync { project ->
          val gradleSettings = GradleSettings.getInstance(project)
          Assertions.assertEquals(0, gradleSettings.linkedProjectsSettings.size)
        }
    }
  }

  @Test
  fun `test auto-link project from new gradle_xml`() {
    runBlocking {
      val projectInfo = getSimpleProjectInfo("project")
      initProject(projectInfo)

      writeAction {
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

      openProject("project", wait = false)
        .useProjectAsync { project ->
          assertModules(project, "project")
          awaitAnyGradleProjectReload {
            writeAction {
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
  }

  @Test
  fun `test open project with inspection profiles`() {
    runBlocking {
      val projectInfo = getSimpleProjectInfo("project")
      initProject(projectInfo)

      writeAction {
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
}