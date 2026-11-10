// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.execution.ConsoleFolding
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class IdeLayoutWarningsCodeFoldingTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  private lateinit var console: ConsoleViewImpl

  private val consoleEditor: EditorEx
    get() = console.editor as EditorEx

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    ExtensionTestUtil.maskExtensions(
      ConsoleFolding.EP_NAME,
      listOf(IdeLayoutWarningsCodeFolding()),
      myFixture.testRootDisposable
    )
    withContext(Dispatchers.EDT) {
      console = ConsoleViewImpl(myFixture.project, GlobalSearchScope.allScope(myFixture.project), false, true)
      console.component
      val processHandler = NopProcessHandler()
      processHandler.startNotify()
      console.attachToProcess(processHandler)
    }
  }

  @AfterEach
  fun tearDown() {
    Disposer.dispose(console)
  }


  @Test
  fun testSingleWarning(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    print(
      """
      > Task :buildPlugin
      Layout component 'vcs-log' has some nonexistent 'action' elements: 'Vcs.Log.Action'
      BUILD SUCCESSFUL in 2s
      """
    )

    val region = assertThat(foldings()).singleElement().actual()
    assertEquals("<1 layout component warning>", region.placeholderText)
  }

  @Test
  fun testConsecutiveWarningsAreFoldedTogether(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    print(
      """
      Layout component 'vcs-log' has some nonexistent 'action' elements: 'Vcs.Log.Action'
      [org.jetbrains.intellij.platform] Layout component 'actions' has some nonexistent 'group' elements: 'CustomGroup'
      > Task :buildPlugin: Layout component 'foo' has some nonexistent 'bar' elements: 'baz'
      BUILD SUCCESSFUL in 2s
      """
    )

    val region = assertThat(foldings()).singleElement().actual()
    assertEquals("<3 layout component warnings>", region.placeholderText)
  }

  @Test
  fun testWarningsAreFoldedSeparately(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
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

  @Test
  fun testOrdinaryOutputIsNotFolded(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    print(
      """
      > Task :buildPlugin
      BUILD SUCCESSFUL in 2s
      Layout component is loaded successfully
      """
    )

    assertThat(foldings()).isNullOrEmpty()
  }

  private suspend fun print(text: String) {
    withContext(Dispatchers.EDT) {
      console.print(text.trimIndent(), ConsoleViewContentType.NORMAL_OUTPUT)
      console.foldImmediately()
    }
  }

  private suspend fun foldings(): List<FoldRegion> = readAction { consoleEditor.foldingModel.allFoldRegions.toList() }
}
