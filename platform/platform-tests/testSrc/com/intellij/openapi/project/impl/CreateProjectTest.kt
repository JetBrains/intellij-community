// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.impl.TrustedPaths
import com.intellij.ide.impl.isTrusted
import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.testFramework.useProject
import io.kotest.common.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotNull

class CreateProjectTest : BareTestFixtureTestCase() {
  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  @Rule
  @JvmField
  val tempDir = TempDirectory()

  @After
  fun cleanup() {
    val projects = ProjectUtilCore.getOpenProjects()
    if (projects.isNotEmpty()) {
      val message = "Leaked projects: ${projects.toList()}"
      projects.forEach(PlatformTestUtil::forceCloseProjectWithoutSaving)
      Assert.fail(message)
    }
  }

  private fun getNotifications(project: Project, groupId: String): List<Notification> {
    val notifications = NotificationsManager.getNotificationsManager().getNotificationsOfType(Notification::class.java, project)
    return notifications.filter { it.groupId == groupId }.toList()
  }

  private fun getProjectLoadingErrorNotifications(project: Project): List<Notification> {
    return getNotifications(project, "Project Loading Error")
  }

  @Test
  fun newlyCreatedProjectTrusted_createProject() {
    val projectDir = tempDir.root.toPath()
    TrustedPaths.getInstance().setProjectPathTrusted(projectDir, false)
    ProjectManagerEx.getInstanceEx().createProject("newlyCreatedProjectTrusted_createProject", projectDir.toString()).useProject { project ->
      assertThat(getProjectLoadingErrorNotifications(project)).isEmpty()
      assertThat(project.isTrusted()).isTrue
    }
  }

  @Test
  fun newlyCreatedProjectTrusted_openProject() {
    val projectDir = tempDir.root.toPath()
    val options = OpenProjectTask {
      isNewProject = true
      runConfigurators = false
      projectName = "newlyCreatedProjectTrusted_openProject"
    }
    TrustedPaths.getInstance().setProjectPathTrusted(projectDir, false)
    val project = ProjectManagerEx.getInstanceEx().newProject(projectDir, options)
    assertNotNull(project)
    project.useProject { project ->
      assertThat(getProjectLoadingErrorNotifications(project)).isEmpty()
      assertThat(project.isTrusted()).isTrue
    }
  }

  @Test
  fun newlyCreatedProjectTrusted_openProjectAsync() {
    val projectDir = tempDir.root.toPath()
    val options = OpenProjectTask {
      isNewProject = true
      runConfigurators = false
      projectName = "newlyCreatedProjectTrusted_openProjectAsync"
    }
    TrustedPaths.getInstance().setProjectPathTrusted(projectDir, false)
    runBlocking {
      val project = ProjectManagerEx.getInstanceEx().newProjectAsync(projectDir, options)
      assertNotNull(project)
      project.useProject { project ->
        assertThat(getProjectLoadingErrorNotifications(project)).isEmpty()
        assertThat(project.isTrusted()).isTrue
      }
    }
  }
}