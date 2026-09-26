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
 * Flatten Packages for the frontend Project View, end to end.
 *
 * In this mode the source root owns one flat node per package, so `org/example/Hello.txt` gives the
 * two sibling nodes `org` and `org.example` under `src`. A new package must join that list, and a
 * removed one must leave it.
 *
 * The two directions do not share a code path. A removed package invalidates its own node, which
 * `TreeBasedProjectViewPaneModel.applyNodeUpdate` propagates up to `src` (IJPL-255213). A new package
 * has no node to invalidate, and nothing updates `src`, so the node never appears.
 *
 * The tests need the Java plugin: only `JavaProjectViewDirectoryHelper` supports Flatten Packages,
 * and the pane treats an unsupported option as off.
 *
 * Kept apart from [ProjectViewPaneTest] because these tests mutate the project they run in.
 */
@TestApplication
internal class ProjectViewPaneFlattenPackagesTest : AbstractProjectViewPaneTest() {
  companion object {
    private fun blueprint(name: String): Path =
      projectViewTestDataPath(ProjectViewPaneFlattenPackagesTest::class.java, "platform/projectView/tests/testData/$name")

    // 'org' holds no file of its own, so in this mode it is a leaf: its only child is a package, and
    // a package is always listed flat under the source root instead.
    private const val FLAT_TREE = """
      pvPackages
       src
        org
        org.example
         Hello.txt
       External Libraries
    """

    private const val FLAT_TREE_WITH_PACK = """
      pvPackages
       src
        org
        org.example
         Hello.txt
        org.pack
         World.txt
       External Libraries
    """
  }

  // Instance-level fixtures, so that every test gets its own project. With one project per class the
  // tests would share the backend pane session, and a test that leaves a broken tree behind would
  // fail the next one for the wrong reason. The directory names are pinned, so the rendered tree is
  // deterministic.
  private val oneProject = projectFixture(pathFixture = tempPathFixture(subdirName = "pvPackages"))
  private val oneSrcRoot = oneProject.moduleFixture(name = "pvPackages").sourceRootFixture(
    isTestSource = false,
    pathFixture = tempPathFixture(subdirName = "src"),
    blueprintResourcePath = blueprint("paneTreeCompactPackages"),
  )

  private val twoProject = projectFixture(pathFixture = tempPathFixture(subdirName = "pvPackages"))
  private val twoSrcRoot = twoProject.moduleFixture(name = "pvPackages").sourceRootFixture(
    isTestSource = false,
    pathFixture = tempPathFixture(subdirName = "src"),
    blueprintResourcePath = blueprint("paneTreeSplitPackages"),
  )

  @Test
  fun `a new package appears in the flat list`() = timeoutRunBlocking(60.seconds) {
    val src = prepare(oneProject, oneSrcRoot)
    withProjectViewPane(oneProject.get(), ProjectPaneModel.ID) { pane ->
      pane.assertTree(FLAT_TREE)

      // Only now, when the tree is fully loaded. The update walk never loads a subtree, so an
      // unloaded 'src' could not show the defect. The load also fills the VFS, which
      // createDirectoryIfMissing needs in order to see the existing 'org'.
      edtWriteAction {
        VfsUtil.createDirectoryIfMissing(src, "org/pack").createChildData(this@ProjectViewPaneFlattenPackagesTest, "World.txt")
      }

      waitUntilAssertSucceeds("'org.pack' should have joined the flat list under 'src'", 30.seconds) {
        pane.assertTree(FLAT_TREE_WITH_PACK)
      }
    }
  }

  @Test
  fun `a removed package disappears from the flat list`() = timeoutRunBlocking(60.seconds) {
    val src = prepare(twoProject, twoSrcRoot)
    withProjectViewPane(twoProject.get(), ProjectPaneModel.ID) { pane ->
      pane.assertTree(FLAT_TREE_WITH_PACK)

      val pack = src.findFileByRelativePath("org/pack")!!
      edtWriteAction { pack.delete(this@ProjectViewPaneFlattenPackagesTest) }

      waitUntilAssertSucceeds("'org.pack' should have left the flat list under 'src'", 30.seconds) {
        pane.assertTree(FLAT_TREE)
      }
    }
  }

  /** Materializes the project and turns Flatten Packages on, before any pane is opened. */
  private fun prepare(projectFixture: TestFixture<Project>, srcRootFixture: TestFixture<PsiDirectory>): VirtualFile {
    val src = srcRootFixture.get().virtualFile // materialize the project + module + source root
    val project = projectFixture.get()
    assertTrue(
      ProjectViewDirectoryHelper.getInstance(project).supportsFlattenPackages(),
      "Flatten Packages needs the Java plugin, otherwise the pane treats the option as off",
    )
    // Set the per-project state only. ProjectViewPaneSettingsService.setOptionSelected would also
    // write the default project state and the application-level ProjectViewSharedSettings.
    // Compact Middle Packages stays off: with both options on, the empty 'org' is dropped and the
    // tree becomes the Compact one.
    ProjectViewState.getInstance(project).flattenPackages = true
    return src
  }
}
