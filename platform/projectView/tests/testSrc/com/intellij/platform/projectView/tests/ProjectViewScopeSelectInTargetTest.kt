// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.CompositeSelectInTarget
import com.intellij.ide.FileSelectInContext
import com.intellij.ide.IdeBundle
import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInManager
import com.intellij.ide.SelectInTarget
import com.intellij.ide.impl.ProjectViewSelectInGroupTarget
import com.intellij.ide.scopeView.NamedScopeFilter
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.platform.projectView.ProjectView
import com.intellij.platform.projectView.backend.impl.scope.ScopePaneModel
import com.intellij.platform.projectView.frontend.actions.SplitProjectViewSelectInGroupTarget
import com.intellij.platform.projectView.frontend.actions.findNodeForSelectIn
import com.intellij.platform.projectView.frontend.pane.id
import com.intellij.platform.projectView.frontend.window.ProjectViewToolWindowServiceImpl
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Tests the Select In targets of the scope panes.
 *
 * The tests get the targets from [ProjectViewSelectInGroupTarget], the same as the Select In popup does.
 */
@TestApplication
@RegistryKey(key = "project.view.toolwindow.split.monolith", value = "true")
internal class ProjectViewScopeSelectInTargetTest : AbstractProjectViewPaneTest() {
  companion object {
    private const val BLUEPRINT = "platform/projectView/tests/testData/paneTreeExample"
    private val blueprint: Path by lazy { projectViewTestDataPath(ProjectViewScopeSelectInTargetTest::class.java, BLUEPRINT) }
  }

  // Opened, because Select In works only in an initialized project.
  private val projectFixture = projectFixture(pathFixture = tempPathFixture(subdirName = "pvScopeSelectInTarget"), openAfterCreation = true)
  private val module = projectFixture.moduleFixture(name = "pvScopeSelectInTarget")
  private val srcRoot = module.sourceRootFixture(
    isTestSource = false,
    pathFixture = tempPathFixture(subdirName = "src"),
    blueprintResourcePath = blueprint,
  )
  private val project: Project get() = projectFixture.get()

  private val helloScope = NamedScope("Hello Files", FilePatternPackageSet(null, "*Hello.txt"))
  private val textScope = NamedScope("Text Files", FilePatternPackageSet(null, "*.txt"))

  @Test
  fun `the targets of the scope panes are the children of one Scope target`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      awaitStartupPaneSelection(project)
      val filters = addScopes(helloScope, textScope)
      val context = FileSelectInContext(project, hello())

      val scopeTarget = scopeTarget(context)

      assertEquals(IdeBundle.message("select.in.scope"), scopeTarget.toString())
      val childNames = scopeTarget.getSubTargets(context).sortedWith(SelectInManager.SelectInTargetComparator.INSTANCE).map { it.toString() }
      assertEquals(filters.map { it.scope.presentableName }, childNames)
    }
  }

  @Test
  fun `the Scope target searches the current scope pane first if its scope contains the file`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      awaitStartupPaneSelection(project)
      val filters = addScopes(helloScope, textScope)
      val hello = hello()
      val textPaneId = ScopePaneModel.paneId(filters.first { it.scope == textScope })
      val firstPaneIdWithHello = ScopePaneModel.paneId(readAction { filters.first { it.accept(hello) } })
      assertNotEquals(textPaneId, firstPaneIdWithHello, "The test needs a scope before the text scope that contains Hello.txt")
      assertTrue(ProjectView.getInstance(project).selectPane(textPaneId))
      val context = FileSelectInContext(project, hello)

      val path = scopeTarget(context).findNodeForSelectIn(context)

      assertEquals(textPaneId, path?.paneId)
    }
  }

  @Test
  fun `the Scope target searches the first scope pane that contains the file`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      // The startup pane isn't a scope pane, so the current pane isn't a child of the Scope target.
      awaitStartupPaneSelection(project)
      val filters = addScopes(helloScope)
      val hello = hello()
      val expectedPaneId = ScopePaneModel.paneId(readAction { filters.first { it.accept(hello) } })
      val context = FileSelectInContext(project, hello)

      val path = scopeTarget(context).findNodeForSelectIn(context)

      assertEquals(expectedPaneId, path?.paneId)
    }
  }

  @Test
  fun `the order of the scopes in the Select In popup does not depend on the current pane`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      awaitStartupPaneSelection(project)
      val filters = addScopes(helloScope, textScope)
      val context = FileSelectInContext(project, hello())
      val itemsBefore = popupItems(context)

      // The last scope, so that a current pane first rule would move it.
      assertTrue(ProjectView.getInstance(project).selectPane(ScopePaneModel.paneId(filters.last())))

      assertEquals(itemsBefore, popupItems(context))
    }
  }

  private fun hello(): VirtualFile = srcRoot.get().virtualFile.findChild("Hello.txt")!!

  /**
   * Adds the [scopes], and waits until the tool window has a pane for each scope.
   * Returns the filters of all scopes in the order of the scopes.
   */
  private suspend fun addScopes(vararg scopes: NamedScope): List<NamedScopeFilter> {
    val holder = NamedScopeManager.getInstance(project)
    for (scope in scopes) {
      holder.addScope(scope)
    }
    val filters = readAction { NamedScopeFilter.list(DependencyValidationManager.getInstance(project), holder) }
    val paneIds = filters.map { ScopePaneModel.paneId(it) }
    ProjectViewToolWindowServiceImpl.getInstance(project).panesSnapshot.first { snapshot ->
      snapshot.panes.map { it.id }.containsAll(paneIds)
    }
    return filters
  }

  /** Returns the only target that the Select In popup shows with children. */
  private fun scopeTarget(context: SelectInContext): SplitProjectViewSelectInGroupTarget {
    val target = ProjectViewSelectInGroupTarget().getSubTargets(context).filterIsInstance<CompositeSelectInTarget>().single()
    return target as SplitProjectViewSelectInGroupTarget
  }

  /** The items of the Project View submenu of the Select In popup, in the order of the popup. A child of an item has an indent. */
  private fun popupItems(context: SelectInContext): List<String> {
    return sorted(ProjectViewSelectInGroupTarget().getSubTargets(context)).flatMap { target ->
      val children = (target as? CompositeSelectInTarget)?.getSubTargets(context).orEmpty()
      listOf(target.toString()) + sorted(children).map { "  $it" }
    }
  }

  private fun sorted(targets: Collection<SelectInTarget>): List<SelectInTarget> =
    targets.sortedWith(SelectInManager.SelectInTargetComparator.INSTANCE)
}
