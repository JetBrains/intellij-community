// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.projectView.impl.project.ProjectPaneModel
import com.intellij.psi.PsiDirectory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Compact Middle Packages for the frontend Project View, end to end.
 *
 * With `org/example/Hello.txt` under a source root, the pane shows the one flattened node
 * `org.example`. A new sibling package splits it into `org` with the children `example` and `pack`,
 * and the removal of that sibling joins them back. Both directions reach
 * `TreeBasedProjectViewPaneModel.applyNodeUpdate` with a node that is gone, so the model must update
 * the parent of that node deeply instead of dropping the node (IJPL-255213).
 *
 * The tests need the Java plugin: only `JavaProjectViewDirectoryHelper` supports Compact Middle
 * Packages, and the pane treats an unsupported option as off.
 *
 * Kept apart from [ProjectViewPaneTest] because these tests mutate the project they run in.
 */
@TestApplication
internal class ProjectViewPaneCompactPackagesTest : AbstractProjectViewPaneTest() {
  companion object {
    private fun blueprint(name: String): Path =
      projectViewTestDataPath(ProjectViewPaneCompactPackagesTest::class.java, "platform/projectView/tests/testData/$name")

    private const val COMPACTED_TREE = """
      pvPackages
       src
        org.example
         Hello.txt
       External Libraries
    """

    private const val SPLIT_TREE = """
      pvPackages
       src
        org
         example
          Hello.txt
         pack
          World.txt
       External Libraries
    """
  }

  // Instance-level fixtures, so that every test gets its own project. With one project per class the
  // tests would share the backend pane session, and a test that leaves a broken tree behind would
  // fail the next one for the wrong reason. The directory names are pinned, so the rendered tree is
  // deterministic.
  private val compactedProject = projectFixture(pathFixture = tempPathFixture(subdirName = "pvPackages"))
  private val compactedSrcRoot = compactedProject.moduleFixture(name = "pvPackages").sourceRootFixture(
    isTestSource = false,
    pathFixture = tempPathFixture(subdirName = "src"),
    blueprintResourcePath = blueprint("paneTreeCompactPackages"),
  )

  private val splitProject = projectFixture(pathFixture = tempPathFixture(subdirName = "pvPackages"))
  private val splitSrcRoot = splitProject.moduleFixture(name = "pvPackages").sourceRootFixture(
    isTestSource = false,
    pathFixture = tempPathFixture(subdirName = "src"),
    blueprintResourcePath = blueprint("paneTreeSplitPackages"),
  )

  @Test
  fun `a new sibling package splits the compacted node`() = timeoutRunBlocking(60.seconds) {
    val src = prepare(compactedProject, compactedSrcRoot)
    withProjectViewPane(compactedProject.get(), ProjectPaneModel.ID) { pane ->
      pane.assertTree(COMPACTED_TREE)

      // Only now, when the tree is fully loaded. The update walk never loads a subtree, so an
      // unloaded 'org.example' could not show the defect. The load also fills the VFS, which
      // createDirectoryIfMissing needs in order to see the existing 'org'.
      edtWriteAction {
        VfsUtil.createDirectoryIfMissing(src, "org/pack").createChildData(this@ProjectViewPaneCompactPackagesTest, "World.txt")
      }

      waitUntilAssertSucceeds("'org.example' should have split into 'org' with 'example' and 'pack'", 30.seconds) {
        pane.assertTree(SPLIT_TREE)
      }
    }
  }

  @Test
  fun `removing a sibling package compacts the node again`() = timeoutRunBlocking(60.seconds) {
    val src = prepare(splitProject, splitSrcRoot)
    withProjectViewPane(splitProject.get(), ProjectPaneModel.ID) { pane ->
      pane.assertTree(SPLIT_TREE)

      // The whole directory, not only its file: an empty 'pack' is not an empty middle package, so
      // 'org' would keep both children.
      val sibling = src.findFileByRelativePath("org/pack")!!
      edtWriteAction { sibling.delete(this@ProjectViewPaneCompactPackagesTest) }

      waitUntilAssertSucceeds("'org' should have compacted back into 'org.example'", 30.seconds) {
        pane.assertTree(COMPACTED_TREE)
      }
    }
  }

  /** Materializes the project and turns Compact Middle Packages on, before any pane is opened. */
  private fun prepare(projectFixture: TestFixture<Project>, srcRootFixture: TestFixture<PsiDirectory>): VirtualFile {
    val src = srcRootFixture.get().virtualFile // materialize the project + module + source root
    val project = projectFixture.get()
    assertTrue(
      ProjectViewDirectoryHelper.getInstance(project).supportsHideEmptyMiddlePackages(),
      "Compact Middle Packages needs the Java plugin, otherwise the pane treats the option as off",
    )
    // Set the per-project state only. ProjectViewPaneSettingsService.setOptionSelected would also
    // write the default project state and the application-level ProjectViewSharedSettings.
    ProjectViewState.getInstance(project).hideEmptyMiddlePackages = true
    return src
  }
}
