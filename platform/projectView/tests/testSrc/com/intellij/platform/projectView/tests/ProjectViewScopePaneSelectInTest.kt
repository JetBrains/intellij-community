// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.FileSelectInContext
import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.ide.scopeView.NamedScopeFilter
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.platform.projectView.backend.impl.scope.ScopePaneModel
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneAggregator
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.SelectInRequestDTO
import com.intellij.platform.projectView.pane.serialize
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
internal class ProjectViewScopePaneSelectInTest : AbstractProjectViewPaneTest() {
  companion object {
    private const val BLUEPRINT = "platform/projectView/tests/testData/paneTreeExample"
    private val blueprint: Path by lazy { projectViewTestDataPath(ProjectViewScopePaneSelectInTest::class.java, BLUEPRINT) }

    // Opened, because Select In works only in an initialized project.
    private val project = projectFixture(pathFixture = tempPathFixture(subdirName = "pvScopeSelectIn"), openAfterCreation = true)
    private val module = project.moduleFixture(name = "pvScopeSelectIn")
    private val srcRoot = module.sourceRootFixture(
      isTestSource = false,
      pathFixture = tempPathFixture(subdirName = "src"),
      blueprintResourcePath = blueprint,
    )
  }

  @BeforeEach
  fun hideModules() {
    ProjectViewState.getInstance(project.get()).showModules = false
  }

  @AfterEach
  fun removeCustomScopes(): Unit = timeoutRunBlocking {
    val holder = NamedScopeManager.getInstance(project.get())
    val paneIds = holder.editableScopes.map { ScopePaneModel.paneId(NamedScopeFilter(holder, it)) }
    holder.removeAllSets()
    // The next test can add a scope with the same name, and its awaitPane must not find the pane of this test.
    val aggregator = FrontendProjectViewPaneAggregator.getInstance(project.get())
    for (paneId in paneIds) {
      aggregator.awaitPaneGone(paneId)
    }
  }

  @Test
  fun `Select In searches the scope of its own pane first`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    val hello = srcRoot.get().virtualFile.findChild("Hello.txt")!!
    val aggregator = FrontendProjectViewPaneAggregator.getInstance(project.get())
    val holder = NamedScopeManager.getInstance(project.get())
    val helloScope = NamedScope("Hello Files", FilePatternPackageSet(null, "*Hello.txt"))
    holder.addScope(helloScope)
    val helloPaneId = ScopePaneModel.paneId(NamedScopeFilter(holder, helloScope))
    aggregator.awaitPane(helloPaneId)

    val path = aggregator.findNodeForSelectIn(selectInRequest(helloPaneId, hello))
    assertEquals(helloPaneId, path?.paneId, "Hello.txt is in the hello scope, but the found path is $path")
  }

  @Test
  fun `Select In searches the first other scope that contains the file`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    val src = srcRoot.get().virtualFile
    // A sibling of Hello.txt, so the hello pane shows its directory, but not the file.
    val other = edtWriteAction { src.createChildData(this@ProjectViewScopePaneSelectInTest, "Other.txt") }
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
    // The predefined scopes go before the custom ones, so one of them can be the first that contains the file.
    val expectedPaneId = readAction {
      NamedScopeFilter.list(DependencyValidationManager.getInstance(project.get()), holder)
        .first { it.scope != helloScope && it.accept(other) }
        .let { ScopePaneModel.paneId(it) }
    }

    try {
      withProjectViewPane(project.get(), textPaneId) { text ->
        text.select()
        text.awaitTree { "Other.txt" in it.names() }
        val path = aggregator.findNodeForSelectIn(selectInRequest(helloPaneId, other))
        assertEquals(expectedPaneId, path?.paneId, "Other.txt isn't in the hello scope, but the found path is $path")
        withProjectViewPane(project.get(), helloPaneId) { hello ->
          hello.select()
          val tree = hello.awaitTree { "Hello.txt" in it.names() }
          assertFalse("Other.txt" in tree.names(), "The hello pane shows a file of another scope:\n$tree")
          assertFalse("World.txt" in tree.names(), "The hello pane shows a file of another scope:\n$tree")
        }
      }
    }
    finally {
      edtWriteAction { other.delete(this@ProjectViewScopePaneSelectInTest) }
    }
  }

  @Test
  fun `Select In searches another scope when no scope pane is live`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    val src = srcRoot.get().virtualFile
    val other = edtWriteAction { src.createChildData(this@ProjectViewScopePaneSelectInTest, "Other.txt") }
    val aggregator = FrontendProjectViewPaneAggregator.getInstance(project.get())
    val holder = NamedScopeManager.getInstance(project.get())
    val helloScope = NamedScope("Hello Files", FilePatternPackageSet(null, "*Hello.txt"))
    holder.addScope(helloScope)
    val helloPaneId = ScopePaneModel.paneId(NamedScopeFilter(holder, helloScope))
    aggregator.awaitPane(helloPaneId)
    val expectedPaneId = readAction {
      NamedScopeFilter.list(DependencyValidationManager.getInstance(project.get()), holder)
        .first { it.scope != helloScope && it.accept(other) }
        .let { ScopePaneModel.paneId(it) }
    }

    try {
      // Nobody requests the state of a scope pane, so the search must make both panes live by itself.
      val path = aggregator.findNodeForSelectIn(selectInRequest(helloPaneId, other))
      assertEquals(expectedPaneId, path?.paneId, "Other.txt isn't in the hello scope, but the found path is $path")
    }
    finally {
      edtWriteAction { other.delete(this@ProjectViewScopePaneSelectInTest) }
    }
  }

  @Test
  fun `the selected pane is live again after Select In has searched another pane`(): Unit = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    val src = srcRoot.get().virtualFile
    val hello = src.findChild("Hello.txt")!!
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

    var newFile: VirtualFile? = null
    try {
      withProjectViewPane(project.get(), textPaneId) { text ->
        text.select()
        text.awaitTree { "Hello.txt" in it.names() }
        val path = aggregator.findNodeForSelectIn(selectInRequest(helloPaneId, hello))
        assertEquals(helloPaneId, path?.paneId, "Hello.txt is in the hello scope, but the found path is $path")
        // Only a live pane gets updates, so the text pane shows a new file only if it's live again.
        newFile = edtWriteAction { src.createChildData(this@ProjectViewScopePaneSelectInTest, "New.txt") }
        text.awaitTree { "New.txt" in it.names() }
      }
    }
    finally {
      newFile?.let { file -> edtWriteAction { file.delete(this@ProjectViewScopePaneSelectInTest) } }
    }
  }

  private fun String.names(): List<String> = lines().map { it.trim() }

  private fun selectInRequest(paneId: ProjectViewPaneId, file: VirtualFile): SelectInRequestDTO {
    val context = FileSelectInContext(project.get(), file)
    return SelectInRequestDTO(targetId = paneId.idString, contextDTO = context.serialize(), context = context)
  }
}
