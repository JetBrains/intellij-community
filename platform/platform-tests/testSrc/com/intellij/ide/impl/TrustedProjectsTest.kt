// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.impl.TrustedProjectStartupDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.withProjectAsync
import com.intellij.util.ThreeState
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

@TestApplication
@SystemProperty("idea.trust.headless.disabled", "false")
class TrustedProjectsTest {

  private val testRoot by tempPathFixture()

  @Test
  fun `prefer closest ancestor to determine the trusted state`() {
    val projects = Path.of("projects/")
    val outerDir = Path.of("projects/outer")
    val innerDir = Path.of("projects/outer/inner")

    TrustedProjects.setProjectTrusted(projects, true)
    Assertions.assertTrue(TrustedProjects.isProjectTrusted(innerDir))
    TrustedProjects.setProjectTrusted(outerDir, false)
    Assertions.assertFalse(TrustedProjects.isProjectTrusted(innerDir))
    TrustedProjects.setProjectTrusted(innerDir, true)
    Assertions.assertTrue(TrustedProjects.isProjectTrusted(innerDir))
  }

  @Test
  fun `return unsure if there are no information about ancestors`() {
    val projectRoot1 = Path.of("project/root1/")
    val projectRoot2 = Path.of("project/root2/")
    TrustedProjects.setProjectTrusted(projectRoot1, true)
    Assertions.assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(projectRoot1))
    Assertions.assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(projectRoot2))
  }

  @Test
  fun `test trusted project state after ProjectManager#newProject`(): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")
    val openProjectTask = OpenProjectTask {
      projectName = "project"
    }
    ProjectManagerEx.getInstanceEx()
      .newProjectAsync(projectRoot, openProjectTask)
      .awaitInitialisation()
      .useProjectAsync { project ->
        Assertions.assertTrue(TrustedProjects.isProjectTrusted(project))
      }
  }

  @ParameterizedTest
  @CsvSource(
    "true, TRUST_AND_OPEN, YES",
    "false, TRUST_AND_OPEN, YES",
    "true, OPEN_IN_SAFE_MODE, NO",
    "false, OPEN_IN_SAFE_MODE, NO",
    "true, CANCEL, UNSURE",
    "false, CANCEL, UNSURE"
  )
  fun `test trusted project state after ProjectManager#openProject`(
    isNewProject: Boolean,
    openChoice: OpenUntrustedProjectChoice,
    expectedTrustedState: ThreeState,
  ): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")

    TrustedProjectStartupDialog.setDialogChoiceInTests(openChoice, asDisposable())

    Assertions.assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(projectRoot))

    val openProjectTask = OpenProjectTask {
      this.projectName = "project"
      this.isNewProject = isNewProject
    }

    when (openChoice) {
      OpenUntrustedProjectChoice.TRUST_AND_OPEN, OpenUntrustedProjectChoice.OPEN_IN_SAFE_MODE -> {
        ProjectManagerEx.getInstanceEx()
          .openProjectAsync(projectRoot, openProjectTask)!!
          .awaitInitialisation()
          .useProjectAsync { project ->
            Assertions.assertEquals(expectedTrustedState, TrustedProjects.getProjectTrustedState(project))
          }
      }
      OpenUntrustedProjectChoice.CANCEL -> {
        runCatching {
          ProjectManagerEx.getInstanceEx()
            .openProjectAsync(projectRoot, openProjectTask)!!
        }.onSuccess { project ->
          project.awaitInitialisation()
          project.closeProjectAsync()
          Assertions.fail<Nothing> {
            "The trusted project dialog was closed with the cancel choice. " +
            "Therefore the project open operation should be cancelled."
          }
        }.onFailure { exception ->
          Assertions.assertInstanceOf(CancellationException::class.java, exception)
        }
      }
    }

    Assertions.assertEquals(expectedTrustedState, TrustedProjects.getProjectTrustedState(projectRoot))
  }

  private suspend fun Project.awaitInitialisation() = withProjectAsync { project ->
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
  }
}