// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileSystemItemFilter
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.projectView.impl.project.ProjectPaneModel
import com.intellij.psi.PsiDirectory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Test

/**
 * One directory under two sections of the project node (IJPL-258255).
 *
 * The test provider copies the Bazel plugin layout: the source root shows twice, once as itself and
 * once as an "Excluded Directories" section, each with its own filter. `parent` holds both kept and
 * excluded content, so it shows under both sections, as two nodes that are equal by `equals`.
 * Each of the two nodes must keep its own children, also when a change in `parent` updates both of them.
 */
@TestApplication
internal class ProjectViewPaneSameDirectoryTwiceTest : AbstractProjectViewPaneTest() {
  companion object {
    private const val BLUEPRINT = "platform/projectView/tests/testData/paneTreeSameDirTwice"
  }

  private val project = projectFixture(pathFixture = tempPathFixture(subdirName = "pvSameDir"))
  private val srcRoot = project.moduleFixture(name = "pvSameDir").sourceRootFixture(
    isTestSource = false,
    pathFixture = tempPathFixture(subdirName = "src"),
    blueprintResourcePath = projectViewTestDataPath(ProjectViewPaneSameDirectoryTwiceTest::class.java, BLUEPRINT),
  )
  private val disposable = disposableFixture()

  @Test
  fun `a directory under two sections keeps its children in each section`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    val src = srcRoot.get() // materialize the project + module + source root before opening the pane
    TreeStructureProvider.EP.getPoint(project.get()).registerExtension(TwoSectionsTreeStructureProvider(src), disposable.get())
    withProjectViewPane(project.get(), ProjectPaneModel.ID) { pane ->
      pane.assertTree(
        """
        pvSameDir
         Excluded Directories
          parent
           excluded
            Excluded.txt
         src
          parent
           kept
            Kept.txt
           Parent.txt
         External Libraries
        """
      )

      // A new file directly in 'parent' updates both 'parent' nodes, but only the one under 'src' shows it.
      edtWriteAction {
        src.virtualFile.findChild("parent")!!.createChildData(this@ProjectViewPaneSameDirectoryTwiceTest, "New.txt")
      }

      waitUntilAssertSucceeds("'New.txt' should have appeared under 'src/parent' only", PANE_TEST_WAIT_TIMEOUT) {
        pane.assertTree(
          """
          pvSameDir
           Excluded Directories
            parent
             excluded
              Excluded.txt
           src
            parent
             kept
              Kept.txt
             New.txt
             Parent.txt
           External Libraries
          """
        )
      }
    }
  }
}

/** Puts [root] under the project node twice: once without `parent/excluded`, and once with only that directory. */
private class TwoSectionsTreeStructureProvider(private val root: PsiDirectory) : TreeStructureProvider {
  override fun modify(
    parent: AbstractTreeNode<*>,
    children: Collection<AbstractTreeNode<*>>,
    settings: ViewSettings,
  ): Collection<AbstractTreeNode<*>> {
    if (parent !is ProjectViewProjectNode) return children
    val project = parent.project
    val parentDir = root.virtualFile.findChild("parent")!!
    val excludedDir = parentDir.findChild("excluded")!!
    val notExcluded = PsiFileSystemItemFilter { item -> !VfsUtilCore.isAncestor(excludedDir, item.virtualFile, false) }
    val onlyExcluded = PsiFileSystemItemFilter { item ->
      item.virtualFile == parentDir || VfsUtilCore.isAncestor(excludedDir, item.virtualFile, false)
    }
    return buildList {
      add(PsiDirectoryNode(project, root, settings, notExcluded))
      add(ExcludedDirectoriesNode(project, root, settings, onlyExcluded))
      addAll(children.filterIsInstance<ExternalLibrariesNode>())
    }
  }
}

private class ExcludedDirectoriesNode(
  project: Project,
  directory: PsiDirectory,
  settings: ViewSettings,
  filter: PsiFileSystemItemFilter,
) : PsiDirectoryNode(project, directory, settings, filter) {
  override fun updateImpl(data: PresentationData) {
    super.updateImpl(data)
    data.clearText()
    data.presentableText = "Excluded Directories"
    data.locationString = null
  }
}
