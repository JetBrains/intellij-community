// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.configurationStore.LoadInvalidProjectTest.Companion.checkProjectHasNotProjectLoadingErrorNotifications
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.useProject
import com.intellij.testFramework.useProjectAsync
import io.kotest.common.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestApplication
@SystemProperty("idea.trust.headless.disabled", "false")
class CreateProjectTest {

  private val projectDir by tempPathFixture()

  @Test
  fun newlyCreatedProjectTrusted_createProject() {
    TrustedProjects.setProjectTrusted(projectDir, false)
    val project = ProjectManagerEx.getInstanceEx().createProject("newlyCreatedProjectTrusted_createProject", projectDir.toString())
    assertNotNull(project)
    project.useProject { project ->
      checkProjectHasNotProjectLoadingErrorNotifications(project)
      assertTrue(TrustedProjects.isProjectTrusted(project))
    }
  }

  @Test
  fun newlyCreatedProjectTrusted_openProject() {
    TrustedProjects.setProjectTrusted(projectDir, false)
    val project = ProjectManagerEx.getInstanceEx().newProject(projectDir, OpenProjectTask {
      isNewProject = true
      runConfigurators = false
      projectName = "newlyCreatedProjectTrusted_openProject"
    })
    assertNotNull(project)
    project.useProject { project ->
      checkProjectHasNotProjectLoadingErrorNotifications(project)
      assertTrue(TrustedProjects.isProjectTrusted(project))
    }
  }

  @Test
  fun newlyCreatedProjectTrusted_openProjectAsync() {
    runBlocking {
      TrustedProjects.setProjectTrusted(projectDir, false)
      val project = ProjectManagerEx.getInstanceEx().newProjectAsync(projectDir, OpenProjectTask {
        isNewProject = true
        runConfigurators = false
        projectName = "newlyCreatedProjectTrusted_openProjectAsync"
      })
      assertNotNull(project)
      project.useProjectAsync { project ->
        checkProjectHasNotProjectLoadingErrorNotifications(project)
        assertTrue(TrustedProjects.isProjectTrusted(project))
      }
    }
  }
}