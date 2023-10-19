// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageEditorAnnotatorImpl
import com.intellij.coverage.CoverageIntegrationBaseTest
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.FillingLineMarkerRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.rt.coverage.data.LineCoverage.*
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

    waitAnnotations()
    assertCoveredFiles()
    closeSuite()
    assertNoCoverage()
  }

  fun `test gutter annotations for opening files`(): Unit = runBlocking {
    ThreadingAssertions.assertBackgroundThread()

    val suite = loadIJSuite()
    openSuiteAndWait(suite)

    openFiles()

    waitAnnotations()
    assertCoveredFiles()
    closeSuite()
    assertNoCoverage()
  }

  fun `test gutter annotations for xml suite`(): Unit = runBlocking {
    ThreadingAssertions.assertBackgroundThread()
    openFiles()

    val suite = loadXMLSuite()
    openSuiteAndWait(suite)

    waitAnnotations()
    assertGutterHighlightLines("foo.bar.BarClass", mapOf(3 to FULL, 5  to FULL, 9  to NONE, 13  to NONE))
    assertGutterHighlightLines("foo.bar.UncoveredClass", mapOf(3 to NONE, 5 to NONE, 8 to NONE, 11 to NONE, 14 to NONE))
    assertGutterHighlightLines("foo.FooClass", mapOf(3 to FULL, 5 to FULL, 9 to PARTIAL))
    closeSuite()
    assertNoCoverage()
  }

  fun `test gutter sub coverage`() : Unit = runBlocking {
    ThreadingAssertions.assertBackgroundThread()
    openFiles()

    val suite = loadIJSuite()
    openSuiteAndWait(suite)

    waitSuiteProcessing {
      CoverageDataManager.getInstance(myProject).selectSubCoverage(suite, listOf("foo.bar.BarTest,testMethod3"))
    }
    waitAnnotations()
    assertGutterHighlightLines("foo.bar.BarClass", mapOf(5  to FULL, 9  to NONE, 13  to NONE))
    assertGutterHighlightLines("foo.bar.UncoveredClass", mapOf())
    assertGutterHighlightLines("foo.FooClass", mapOf())

    waitSuiteProcessing {
      CoverageDataManager.getInstance(myProject).selectSubCoverage(suite, listOf("foo.FooTest,testMethod1"))
    }
    waitAnnotations()
    assertGutterHighlightLines("foo.bar.BarClass", mapOf())
    assertGutterHighlightLines("foo.bar.UncoveredClass", mapOf())
    assertGutterHighlightLines("foo.FooClass", mapOf(5 to FULL, 9 to NONE))

    waitSuiteProcessing {
      CoverageDataManager.getInstance(myProject).selectSubCoverage(suite, listOf("foo.FooTest,testMethod2"))
    }
    waitAnnotations()
    assertGutterHighlightLines("foo.bar.BarClass", mapOf())
    assertGutterHighlightLines("foo.bar.UncoveredClass", mapOf())
    assertGutterHighlightLines("foo.FooClass", mapOf(5 to NONE, 9 to FULL))

    closeSuite()
    assertNoCoverage()
  }

  private suspend fun assertNoCoverage() {
    assertGutterHighlightLines("foo.bar.BarClass", null)
    assertGutterHighlightLines("foo.bar.UncoveredClass", null)
    assertGutterHighlightLines("foo.FooClass", null)
  }

  private suspend fun assertCoveredFiles() {
    assertGutterHighlightLines("foo.bar.BarClass", mapOf(5 to FULL, 9 to NONE, 13 to NONE))
    assertGutterHighlightLines("foo.bar.UncoveredClass", mapOf(5 to NONE, 8 to NONE, 11 to NONE, 14 to NONE))
    assertGutterHighlightLines("foo.FooClass", mapOf(5 to FULL, 9 to PARTIAL))
  }

  private suspend fun openFiles() {
    openClass("foo.bar.BarClass")
    openClass("foo.bar.UncoveredClass")
    openClass("foo.FooClass")
  }

  private suspend fun waitAnnotations() = delay(500)

  private suspend fun assertGutterHighlightLines(className: String, expected: Map<Int, Byte>?) {
    val editor = findEditor(className)
    val highlighters = editor.getUserData(CoverageEditorAnnotatorImpl.COVERAGE_HIGHLIGHTERS)
    val lines = highlighters?.associate { it.document.getLineNumber(it.startOffset) + 1 to getCoverage(it) }
    Assert.assertEquals(expected, lines)
  }

  private fun getCoverage(it: RangeHighlighter) = when ((it.lineMarkerRenderer as FillingLineMarkerRenderer).getTextAttributesKey()) {
    CodeInsightColors.LINE_FULL_COVERAGE -> FULL
    CodeInsightColors.LINE_PARTIAL_COVERAGE -> PARTIAL
    CodeInsightColors.LINE_NONE_COVERAGE -> NONE
    else -> error("Unexpected gutter highlighting")
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
