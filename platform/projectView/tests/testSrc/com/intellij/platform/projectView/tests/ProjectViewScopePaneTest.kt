// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.scopeView.NamedScopeFilter
import com.intellij.openapi.project.Project
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneAggregator
import com.intellij.platform.projectView.impl.scope.ScopePaneModel
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.psi.search.scope.ProjectFilesScope
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class ProjectViewScopePaneTest : AbstractProjectViewPaneTest() {
  companion object {
    private const val BLUEPRINT = "platform/projectView/tests/testData/paneTreeExample"
    private val blueprint: Path by lazy { projectViewTestDataPath(ProjectViewScopePaneTest::class.java, BLUEPRINT) }

    // Pin the project and source-root directory names so the rendered tree is deterministic.
    private val project = projectFixture(pathFixture = tempPathFixture(subdirName = "pvScopeExample"))
    private val module = project.moduleFixture(name = "pvScopeExample")
    private val srcRoot = module.sourceRootFixture(
      isTestSource = false,
      pathFixture = tempPathFixture(subdirName = "src"),
      blueprintResourcePath = blueprint,
    )
  }

  @AfterEach
  fun removeCustomScopes() {
    NamedScopeManager.getInstance(project.get()).removeAllSets()
  }

  @Test
  fun `the project files scope pane shows the project files`() = timeoutRunBlocking(60.seconds) {
    srcRoot.get() // materialize the project + module + source root before opening the pane
    withProjectViewPane(project.get(), projectFilesScopePaneId(project.get())) { pane ->
      pane.assertTreeWithContentRoot(
        """
        pvScopeExample
         <content root>
          sub
           World.txt
          Hello.txt
        """
      )
    }
  }

  @Test
  fun `adding and removing a scope adds and removes a pane`() = timeoutRunBlocking(60.seconds) {
    srcRoot.get()
    val aggregator = FrontendProjectViewPaneAggregator.getInstance(project.get())
    // Wait for the initial set of panes first, so that adding a scope is a real change.
    aggregator.awaitPane(projectFilesScopePaneId(project.get()))

    val holder = NamedScopeManager.getInstance(project.get())
    val scope = NamedScope("Only Hello", FilePatternPackageSet(null, "*Hello.txt"))
    val paneId = ScopePaneModel.paneId(NamedScopeFilter(holder, scope))
    holder.addScope(scope) // fires the scope listeners the provider subscribes to

    aggregator.awaitPane(paneId)
    withProjectViewPane(project.get(), paneId) { pane ->
      // Only the structure of the scope's own contents is asserted here: the nodes above them depend on how
      // ScopeViewTreeModel groups content roots, which is the legacy behaviour this pane inherits as is.
      val files = pane.dumpTree().lines().map { it.trim() }
      assertTrue(files.contains("Hello.txt"), "The scope pane should show Hello.txt, but the tree is:\n${pane.dumpTree()}")
      assertFalse(files.contains("World.txt"), "The scope excludes World.txt, but the tree is:\n${pane.dumpTree()}")
    }

    holder.removeAllSets()
    aggregator.awaitPaneGone(paneId)
  }

  /**
   * Asserts the whole tree, with the content root's own line replaced by `<content root>`: a Scope pane names
   * its content roots after their location (see `ScopeViewTreeModel.RootNode.getNodeName`), which in a test is
   * a temp directory.
   */
  private suspend fun ProjectViewPaneTester.assertTreeWithContentRoot(expected: String) {
    val dump = dumpTree().lines().joinToString("\n") { line ->
      val indent = line.takeWhile { it == ' ' }
      if (line.startsWith("$indent/")) "$indent<content root>" else line
    }
    assertEquals(expected.trimIndent().trimEnd(), dump)
  }

  private suspend fun FrontendProjectViewPaneAggregator.awaitPane(paneId: ProjectViewPaneId) {
    getPaneDescriptorsFlow().first { descriptors -> descriptors.any { it.id == paneId } }
  }

  private suspend fun FrontendProjectViewPaneAggregator.awaitPaneGone(paneId: ProjectViewPaneId) {
    getPaneDescriptorsFlow().first { descriptors -> descriptors.none { it.id == paneId } }
  }

  /**
   * The pane ID is derived from the scope and its package set (that's what the legacy Scope pane uses as its
   * sub-ID), so it's computed here rather than spelled out.
   */
  private fun projectFilesScopePaneId(project: Project): ProjectViewPaneId {
    val holder: NamedScopesHolder = DependencyValidationManager.getInstance(project)
    val scope = holder.scopes.first { it is ProjectFilesScope }
    return ScopePaneModel.paneId(NamedScopeFilter(holder, scope))
  }
}
