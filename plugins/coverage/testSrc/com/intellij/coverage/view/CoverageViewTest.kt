// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view

import com.intellij.coverage.CoverageEditorAnnotatorImpl
import com.intellij.coverage.CoverageIntegrationBaseTest
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.utils.vfs.getPsiFile
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert

class CoverageViewTest : CoverageIntegrationBaseTest() {

  fun testViewActivation() = runBlocking {
    val bundle = loadIJSuite()

    openSuiteAndWait(bundle)
    assertToolWindowExists()
    Assert.assertNotNull(findCoverageView(bundle))

    closeSuite()
    assertToolWindowExists()
    Assert.assertNull(findCoverageView(bundle))
  }


  fun `test gutter annotations for opened files`(): Unit = runBlocking {
    ThreadingAssertions.assertBackgroundThread()
    openFiles()

    val suite = loadIJSuite()
    openSuiteAndWait(suite)

    delay(500)
    assertCoveredFiles()
    closeSuite()
    assertNoCoverage()
  }

  fun `test gutter annotations for opening files`(): Unit = runBlocking {
    ThreadingAssertions.assertBackgroundThread()

    val suite = loadIJSuite()
    openSuiteAndWait(suite)

    openFiles()

    delay(500)
    assertCoveredFiles()
    closeSuite()
    assertNoCoverage()
  }

  fun `test gutter annotations for xml suite`(): Unit = runBlocking {
    ThreadingAssertions.assertBackgroundThread()
    openFiles()

    val suite = loadXMLSuite()
    openSuiteAndWait(suite)

    delay(500)
    assertGutterHighlightLines("foo.bar.BarClass", listOf(3, 5, 9, 13))
    assertGutterHighlightLines("foo.bar.UncoveredClass", listOf(3, 5, 8, 11, 14))
    assertGutterHighlightLines("foo.FooClass", listOf(3, 5, 9))
    closeSuite()
    assertNoCoverage()
  }

  private suspend fun assertNoCoverage() {
    assertGutterHighlightLines("foo.bar.BarClass", null)
    assertGutterHighlightLines("foo.bar.UncoveredClass", null)
    assertGutterHighlightLines("foo.FooClass", null)
  }

  private suspend fun assertCoveredFiles() {
    assertGutterHighlightLines("foo.bar.BarClass", listOf(5, 9, 13))
    assertGutterHighlightLines("foo.bar.UncoveredClass", listOf(5, 8, 11, 14))
    assertGutterHighlightLines("foo.FooClass", listOf(5, 9))
  }

  private suspend fun openFiles() {
    openClass("foo.bar.BarClass")
    openClass("foo.bar.UncoveredClass")
    openClass("foo.FooClass")
  }

  private suspend fun assertGutterHighlightLines(className: String, expectedLines: List<Int>?) {
    val editor = findEditor(className)
    val highlighters = editor.getUserData(CoverageEditorAnnotatorImpl.COVERAGE_HIGHLIGHTERS)
    val lines = highlighters?.map { it.document.getLineNumber(it.startOffset) + 1 }
    Assert.assertEquals(expectedLines, lines)
  }

  private suspend fun findEditor(className: String): EditorImpl {
    val psiClass = getPsiClass(className)
    return readAction { findEditor(psiClass) }
  }

  private fun findEditor(psiClass: PsiClass): EditorImpl {
    val psiFile = psiClass.containingFile
    return FileEditorManager.getInstance(myProject).allEditors.asSequence()
      .filterIsInstance<TextEditor>()
      .map { it.editor }.filterIsInstance<EditorImpl>()
      .filter { it.virtualFile.getPsiFile(myProject) == psiFile }.first()
  }

  private suspend fun openClass(className: String) {
    val psiClass = getPsiClass(className)
    writeAction { psiClass.navigate(true) }
  }

  private suspend fun getPsiClass(className: String) = readAction {
    JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.projectScope(myProject))!!
  }

  private fun findCoverageView(bundle: CoverageSuitesBundle): CoverageView? =
    CoverageViewManager.getInstance(myProject).getToolwindow(bundle)

  private fun assertToolWindowExists() {
    Assert.assertNotNull(getInstance(myProject).getToolWindow(CoverageViewManager.TOOLWINDOW_ID))
  }
}
