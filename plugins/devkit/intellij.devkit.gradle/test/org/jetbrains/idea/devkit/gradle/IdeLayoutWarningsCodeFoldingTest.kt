// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.execution.ConsoleFolding
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class IdeLayoutWarningsCodeFoldingTest : LightJavaCodeInsightFixtureTestCase() {

  private lateinit var console: ConsoleViewImpl

  private val consoleEditor: EditorEx
    get() = console.editor as EditorEx

  override fun setUp() {
    super.setUp()
    ExtensionTestUtil.maskExtensions(
      ConsoleFolding.EP_NAME,
      listOf(IdeLayoutWarningsCodeFolding()),
      testRootDisposable
    )
    console = ConsoleViewImpl(project, GlobalSearchScope.allScope(project), false, true)
    console.component
    val processHandler = NopProcessHandler()
    processHandler.startNotify()
    console.attachToProcess(processHandler)
  }

  override fun tearDown() {
    var disposeException: Throwable? = null
    try {
      Disposer.dispose(console)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      disposeException = e
    }
    finally {
      try {
        super.tearDown()
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        disposeException?.let(e::addSuppressed)
        throw e
      }
      disposeException?.let { throw it }
    }
  }

  fun testSingleWarning() {
    print(
      """
      > Task :buildPlugin
      Layout component 'vcs-log' has some nonexistent 'action' elements: 'Vcs.Log.Action'
      BUILD SUCCESSFUL in 2s
      """
    )

    val region = assertOneElement(foldings())
    assertEquals("<1 layout component warning>", region.placeholderText)
  }

  fun testConsecutiveWarningsAreFoldedTogether() {
    print(
      """
      Layout component 'vcs-log' has some nonexistent 'action' elements: 'Vcs.Log.Action'
      [org.jetbrains.intellij.platform] Layout component 'actions' has some nonexistent 'group' elements: 'CustomGroup'
      > Task :buildPlugin: Layout component 'foo' has some nonexistent 'bar' elements: 'baz'
      BUILD SUCCESSFUL in 2s
      """
    )

    val region = assertOneElement(foldings())
    assertEquals("<3 layout component warnings>", region.placeholderText)
  }

  fun testWarningsAreFoldedSeparately() {
    print(
      """
      Layout component 'vcs-log' has some nonexistent 'action' elements: 'Vcs.Log.Action'
      BUILD SUCCESSFUL in 2s
      [org.jetbrains.intellij.platform] Layout component 'actions' has some nonexistent 'group' elements: 'CustomGroup'
      Layout component is loaded successfully
      """
    )

    assertEquals(2, foldings().size)
  }

  fun testOrdinaryOutputIsNotFolded() {
    print(
      """
      > Task :buildPlugin
      BUILD SUCCESSFUL in 2s
      Layout component is loaded successfully
      """
    )

    assertEmpty(foldings())
  }

  private fun print(text: String) {
    console.print(text.trimIndent(), ConsoleViewContentType.NORMAL_OUTPUT)
    console.foldImmediately()
  }

  private fun foldings(): List<FoldRegion> = consoleEditor.foldingModel.allFoldRegions.toList()
}
