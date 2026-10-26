// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.ide.scopeView.NamedScopeFilter
import com.intellij.ide.scratch.RootType
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.platform.projectView.backend.impl.scope.ScopePaneModel
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneAggregator
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.psi.search.scope.ProjectFilesScope
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.ui.tree.project.ProjectFileTreeModel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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

  @BeforeEach
  fun hideModules() {
    // Otherwise ScopeViewTreeModel groups the content roots under a module node, and assertTreeWithContentRoot needs them under the root.
    ProjectViewState.getInstance(project.get()).showModules = false
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

  @Test
  fun `a frozen scope pane resumes its tree when it is selected again`() = timeoutRunBlocking(60.seconds) {
    val src = srcRoot.get().virtualFile
    val aggregator = FrontendProjectViewPaneAggregator.getInstance(project.get())
    val holder = NamedScopeManager.getInstance(project.get())
    val helloScope = NamedScope("Hello Files", FilePatternPackageSet(null, "*Hello.txt"))
    val textScope = NamedScope("Text Files", FilePatternPackageSet(null, "*.txt"))
    holder.addScope(helloScope)
    holder.addScope(textScope)
    val helloPaneId = ScopePaneModel.paneId(NamedScopeFilter(holder, helloScope))
    val textPaneId = ScopePaneModel.paneId(NamedScopeFilter(holder, textScope))
    aggregator.awaitPane(helloPaneId)
    aggregator.awaitPane(textPaneId)

    try {
      withProjectViewPane(project.get(), helloPaneId) { hello ->
        hello.select()
        hello.awaitTree { "Hello.txt" in it.names() }
        val helloNodeId = hello.findLoadedNode("Hello.txt")!!.projectViewNode.id

        withProjectViewPane(project.get(), textPaneId) { text ->
          text.select()
          text.awaitTree { "World.txt" in it.names() }
          edtWriteAction { src.createChildData(this@ProjectViewScopePaneTest, "NewHello.txt") }
          // The text pane shows the new file, so the shared model has processed it. The frozen hello pane gets no update.
          text.awaitTree { "NewHello.txt" in it.names() }
          assertNull(hello.findLoadedNode("NewHello.txt"), "The frozen hello pane shows a file created after it was frozen")
        }

        hello.select()
        val tree = hello.awaitTree { "NewHello.txt" in it.names() }
        assertFalse("World.txt" in tree.names(), "The hello pane shows a file of the text scope:\n$tree")
        assertEquals(helloNodeId, hello.findLoadedNode("Hello.txt")!!.projectViewNode.id, "The resumed tree has a new node ID")
      }
    }
    finally {
      edtWriteAction { src.findChild("NewHello.txt")?.delete(this@ProjectViewScopePaneTest) }
    }
  }

  @Test
  fun `a disposed project file tree model does not leak`() {
    val model = ProjectFileTreeModel(project.get())
    Disposer.dispose(model)
    // The models of other scope panes can still be alive and registered. Only a disposed model is a leak.
    LeakHunter.checkLeak(RootType.ROOT_EP.point, ProjectFileTreeModel::class.java) { it.isDisposed }
  }

  private fun String.names(): List<String> = lines().map { it.trim() }

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
