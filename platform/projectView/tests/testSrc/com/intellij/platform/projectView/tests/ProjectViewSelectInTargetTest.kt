// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.FileSelectInContext
import com.intellij.ide.SelectInContext
import com.intellij.ide.impl.ProjectViewSelectInGroupTarget
import com.intellij.ide.impl.getSelectInPrefetchers
import com.intellij.ide.impl.prefetchForSelectIn
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.platform.projectView.frontend.actions.SplitProjectViewPaneSelectInTarget
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneAggregator
import com.intellij.platform.projectView.impl.project.ProjectPaneModel
import com.intellij.platform.projectView.pane.serialize
import com.intellij.platform.projectView.rpc.ProjectViewRpc
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
 * Tests [com.intellij.ide.SelectInTarget.canSelect] of the split Project View in the monolith mode,
 * and with a backend that works as if it ran in another process.
 *
 * The tests get the targets from [ProjectViewSelectInGroupTarget], the same as the Select In popup does.
 */
@TestApplication
@RegistryKey(key = "project.view.toolwindow.split.monolith", value = "true")
internal class ProjectViewSelectInTargetTest : AbstractProjectViewPaneTest() {
  companion object {
    private const val BLUEPRINT = "platform/projectView/tests/testData/paneTreeExample"
    private val blueprint: Path by lazy { projectViewTestDataPath(ProjectViewSelectInTargetTest::class.java, BLUEPRINT) }
    private const val PREFETCH_KEY = "project.view.toolwindow.split.remdev.select.in.prefetch"
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
      val context = FileSelectInContext(project, hello())

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

  @Test
  fun `in the monolith mode, Select In has nothing to prefetch`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      awaitStartupPaneSelection(project)

      assertTrue(getSelectInPrefetchers(FileSelectInContext(project, hello())).isEmpty())
    }
  }

  @Test
  fun `the backend tells which panes cannot select a context`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      awaitStartupPaneSelection(project)
      val rpc = ProjectViewRpc.awaitConnectionAndGetInstance()

      val forHello = rpc.getPanesThatCannotSelect(project.projectId(), FileSelectInContext(project, hello()).serialize())
      val forOutside = rpc.getPanesThatCannotSelect(project.projectId(), FileSelectInContext(project, createOutsideFile()).serialize())

      assertFalse(ProjectPaneModel.ID in forHello, "The Project pane can select Hello.txt, but the backend says that it cannot: $forHello")
      assertTrue(ProjectPaneModel.ID in forOutside, "The Project pane cannot select the outside file, but the backend says that it can: $forOutside")
    }
  }

  @Test
  fun `with a remote backend, a target can select any context before the prefetch`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      awaitStartupPaneSelection(project)
      withRemoteBackend {
        val context = FileSelectInContext(project, createOutsideFile())

        assertTrue(readAction { projectPaneTarget(context).canSelect(context) })
      }
    }
  }

  @Test
  fun `with a remote backend, a target uses the answer of the prefetch`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      awaitStartupPaneSelection(project)
      withRemoteBackend {
        val context = FileSelectInContext(project, createOutsideFile())

        prefetchForSelectIn(context, getSelectInPrefetchers(context))

        assertFalse(readAction { projectPaneTarget(context).canSelect(context) })
        assertFalse(readAction { ProjectViewSelectInGroupTarget().canSelect(context) })
        assertTrue(getSelectInPrefetchers(context).isEmpty(), "The answer for the context is already fetched")
      }
    }
  }

  @Test
  fun `with a remote backend, the answer of the prefetch is only for its own context`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      awaitStartupPaneSelection(project)
      withRemoteBackend {
        val context = FileSelectInContext(project, createOutsideFile())
        val otherContext = FileSelectInContext(project, context.virtualFile)

        prefetchForSelectIn(context, getSelectInPrefetchers(context))

        assertTrue(readAction { projectPaneTarget(otherContext).canSelect(otherContext) })
        assertFalse(getSelectInPrefetchers(otherContext).isEmpty(), "The other context needs its own answer")
      }
    }
  }

  @Test
  fun `with a remote backend and the prefetch key off, Select In has nothing to prefetch`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      awaitStartupPaneSelection(project)
      withRemoteBackend { disposable ->
        Registry.get(PREFETCH_KEY).setValue(false, disposable)

        assertTrue(getSelectInPrefetchers(FileSelectInContext(project, hello())).isEmpty())
      }
    }
  }

  private fun hello(): VirtualFile = srcRoot.get().virtualFile.findChild("Hello.txt")!!

  private fun createOutsideFile(): VirtualFile {
    val path = Files.createFile(outsideDir.get().resolve("Outside.txt"))
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
  }

  private fun projectPaneTarget(context: SelectInContext): SplitProjectViewPaneSelectInTarget {
    return ProjectViewSelectInGroupTarget().getSubTargets(context)
      .filterIsInstance<SplitProjectViewPaneSelectInTarget>()
      .single { it.paneId == ProjectPaneModel.ID }
  }

  /** Runs [code] while the aggregator uses the backend as if it ran in another process. */
  private suspend fun withRemoteBackend(code: suspend (Disposable) -> Unit) {
    val disposable = Disposer.newDisposable()
    try {
      FrontendProjectViewPaneAggregator.getInstance(project).simulateRemoteBackend(disposable)
      code(disposable)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }
}
