// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.FileSelectInContext
import com.intellij.ide.SelectInContext
import com.intellij.ide.impl.ProjectViewSelectInGroupTarget
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.projectView.frontend.actions.SplitProjectViewPaneSelectInTarget
import com.intellij.platform.projectView.impl.project.ProjectPaneModel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests [com.intellij.ide.SelectInTarget.canSelect] of the split Project View in the monolith mode.
 *
 * The tests get the targets from [ProjectViewSelectInGroupTarget], the same as the Select In popup does.
 */
@TestApplication
@RegistryKey(key = "project.view.toolwindow.split.monolith", value = "true")
internal class ProjectViewSelectInTargetTest : AbstractProjectViewPaneTest() {
  companion object {
    private const val BLUEPRINT = "platform/projectView/tests/testData/paneTreeExample"
    private val blueprint: Path by lazy { projectViewTestDataPath(ProjectViewSelectInTargetTest::class.java, BLUEPRINT) }
  }

  // Opened, because Select In works only in an initialized project.
  private val projectFixture = projectFixture(pathFixture = tempPathFixture(subdirName = "pvSelectInTarget"), openAfterCreation = true)
  private val module = projectFixture.moduleFixture(name = "pvSelectInTarget")
  private val srcRoot = module.sourceRootFixture(
    isTestSource = false,
    pathFixture = tempPathFixture(subdirName = "src"),
    blueprintResourcePath = blueprint,
  )
  /** Not in the content roots and not in the base directory of the project. */
  private val outsideDir = tempPathFixture(subdirName = "outside")
  private val project: Project get() = projectFixture.get()

  @Test
  fun `the Project pane target can select a file of the project`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      awaitStartupPaneSelection(project)
      val context = FileSelectInContext(project, srcRoot.get().virtualFile.findChild("Hello.txt")!!)

      assertTrue(readAction { projectPaneTarget(context).canSelect(context) })
    }
  }

  @Test
  fun `the Project pane target cannot select a file outside the project`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      awaitStartupPaneSelection(project)
      val context = FileSelectInContext(project, createOutsideFile())

      assertFalse(readAction { projectPaneTarget(context).canSelect(context) })
    }
  }

  @Test
  fun `the Project View target cannot select a file that no pane can select`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      awaitStartupPaneSelection(project)
      val context = FileSelectInContext(project, createOutsideFile())

      assertFalse(readAction { ProjectViewSelectInGroupTarget().canSelect(context) })
    }
  }

  private fun createOutsideFile(): VirtualFile {
    val path = Files.createFile(outsideDir.get().resolve("Outside.txt"))
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
  }

  private fun projectPaneTarget(context: SelectInContext): SplitProjectViewPaneSelectInTarget {
    return ProjectViewSelectInGroupTarget().getSubTargets(context)
      .filterIsInstance<SplitProjectViewPaneSelectInTarget>()
      .single { it.paneId == ProjectPaneModel.ID }
  }
}
