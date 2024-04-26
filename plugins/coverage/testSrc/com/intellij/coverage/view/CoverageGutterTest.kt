// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view

import com.intellij.coverage.CoverageEditorAnnotatorImpl
import com.intellij.coverage.CoverageIntegrationBaseTest
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.FillingLineMarkerRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val TIMEOUT_MS = 20_000L

@RunWith(JUnit4::class)
class CoverageGutterTest : CoverageIntegrationBaseTest() {

  @Test(timeout = TIMEOUT_MS)
  fun `test gutter annotations for opened files`(): Unit = runBlocking {
    ThreadingAssertions.assertBackgroundThread()
    openFiles()

    val suite = loadIJSuite()
    openSuiteAndWait(suite)

    assertCoveredFiles()
    closeSuite(suite)
    assertNoCoverage()
  }

  @Test(timeout = TIMEOUT_MS)
  fun `test gutter annotations for opening files`(): Unit = runBlocking {
    ThreadingAssertions.assertBackgroundThread()

    val suite = loadIJSuite()
    openSuiteAndWait(suite)

    openFiles()

    assertCoveredFiles()
    closeSuite(suite)
    assertNoCoverage()
  }

  @Test(timeout = TIMEOUT_MS)
  fun `test gutter annotations for xml suite`(): Unit = runBlocking {
    ThreadingAssertions.assertBackgroundThread()
    openFiles()

    val suite = loadXMLSuite()
    openSuiteAndWait(suite)

    assertGutterHighlightLines("foo.bar.BarClass",
                               mapOf(3 to LineCoverage.FULL, 5 to LineCoverage.FULL, 9 to LineCoverage.NONE, 13 to LineCoverage.NONE))
    assertGutterHighlightLines("foo.bar.UncoveredClass",
                               mapOf(3 to LineCoverage.NONE, 5 to LineCoverage.NONE, 8 to LineCoverage.NONE, 11 to LineCoverage.NONE,
                                     14 to LineCoverage.NONE))
    assertGutterHighlightLines("foo.FooClass", mapOf(3 to LineCoverage.FULL, 5 to LineCoverage.FULL, 9 to LineCoverage.PARTIAL))
    closeSuite(suite)
    assertNoCoverage()
  }

  @Test(timeout = TIMEOUT_MS)
  fun `test gutter sub coverage`(): Unit = runBlocking {
    ThreadingAssertions.assertBackgroundThread()
    openFiles()

    val suite = loadIJSuite()
    openSuiteAndWait(suite)

    waitSuiteProcessing {
      manager.selectSubCoverage(suite, listOf("foo.bar.BarTest,testMethod3"))
    }
    assertGutterHighlightLines("foo.bar.BarClass", mapOf(5 to LineCoverage.FULL, 9 to LineCoverage.NONE, 13 to LineCoverage.NONE))
    assertGutterHighlightLines("foo.bar.UncoveredClass", mapOf())
    assertGutterHighlightLines("foo.FooClass", mapOf())

    waitSuiteProcessing {
      manager.selectSubCoverage(suite, listOf("foo.FooTest,testMethod1"))
    }
    assertGutterHighlightLines("foo.bar.BarClass", mapOf())
    assertGutterHighlightLines("foo.bar.UncoveredClass", mapOf())
    assertGutterHighlightLines("foo.FooClass", mapOf(5 to LineCoverage.FULL, 9 to LineCoverage.NONE))

    waitSuiteProcessing {
      manager.selectSubCoverage(suite, listOf("foo.FooTest,testMethod2"))
    }
    assertGutterHighlightLines("foo.bar.BarClass", mapOf())
    assertGutterHighlightLines("foo.bar.UncoveredClass", mapOf())
    assertGutterHighlightLines("foo.FooClass", mapOf(5 to LineCoverage.NONE, 9 to LineCoverage.FULL))

    closeSuite(suite)
    assertNoCoverage()
  }

  private suspend fun assertNoCoverage() {
    assertGutterHighlightLines("foo.bar.BarClass", null)
    assertGutterHighlightLines("foo.bar.UncoveredClass", null)
    assertGutterHighlightLines("foo.FooClass", null)
  }

  private suspend fun assertCoveredFiles() {
    assertGutterHighlightLines("foo.bar.BarClass", mapOf(5 to LineCoverage.FULL, 9 to LineCoverage.NONE, 13 to LineCoverage.NONE))
    assertGutterHighlightLines("foo.bar.UncoveredClass",
                               mapOf(5 to LineCoverage.NONE, 8 to LineCoverage.NONE, 11 to LineCoverage.NONE, 14 to LineCoverage.NONE))
    assertGutterHighlightLines("foo.FooClass", mapOf(5 to LineCoverage.FULL, 9 to LineCoverage.PARTIAL))
  }

  private suspend fun openFiles() {
    openClass(myProject, "foo.bar.BarClass")
    openClass(myProject, "foo.bar.UncoveredClass")
    openClass(myProject, "foo.FooClass")
    awaitGutterAnnotations()
  }

  private suspend fun assertGutterHighlightLines(className: String, expected: Map<Int, Byte>?) {
    val highlighters = getHighlighters(className)
    val lines = highlighters?.associate { it.document.getLineNumber(it.startOffset) + 1 to getCoverage(it) }
    Assert.assertEquals(expected, lines)
  }

  private suspend fun getHighlighters(className: String): List<RangeHighlighter>? = withContext(Dispatchers.EDT) {
    findEditor(myProject, className).getUserData(CoverageEditorAnnotatorImpl.COVERAGE_HIGHLIGHTERS)
  }

  private fun getCoverage(it: RangeHighlighter) = when ((it.lineMarkerRenderer as FillingLineMarkerRenderer).getTextAttributesKey()) {
    CodeInsightColors.LINE_FULL_COVERAGE -> LineCoverage.FULL
    CodeInsightColors.LINE_PARTIAL_COVERAGE -> LineCoverage.PARTIAL
    CodeInsightColors.LINE_NONE_COVERAGE -> LineCoverage.NONE
    else -> error("Unexpected gutter highlighting")
  }


}

internal suspend fun findEditor(project: Project, className: String): EditorImpl {
  val psiClass = getPsiClass(project, className)
  return readAction { findEditor(psiClass) }
}

private fun findEditor(psiClass: PsiClass): EditorImpl {
  val virtualFile = psiClass.containingFile.virtualFile
  return FileEditorManager.getInstance(psiClass.project).allEditors.asSequence()
    .filterIsInstance<TextEditor>()
    .map { it.editor }.filterIsInstance<EditorImpl>()
    .filter { it.virtualFile == virtualFile }.first()
}

internal suspend fun openClass(project: Project, className: String) {
  val psiClass = getPsiClass(project, className)
  writeAction { psiClass.navigate(true) }
}

private suspend fun getPsiClass(project: Project, className: String) = readAction {
  JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.projectScope(project))!!
}
